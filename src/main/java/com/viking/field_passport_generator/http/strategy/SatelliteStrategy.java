package com.viking.field_passport_generator.http.strategy;

import com.viking.field_passport_generator.data.dto.satellite.FieldSpectralResponse;
import com.viking.field_passport_generator.data.dto.satellite.SatelliteScan;
import com.viking.field_passport_generator.http.SatelliteImageLoader;
import com.viking.field_passport_generator.model.ImageSource;
import com.viking.field_passport_generator.model.SatelliteImage;
import com.viking.field_passport_generator.model.SourceType;
import com.viking.field_passport_generator.util.JsonDataParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * Strategy for handling satellite imagery.
 * Implements "Lazy Loading" synchronizes metadata (history.json) first,
 * and downloads actual images only when requested.
 */
public class SatelliteStrategy implements ImageProviderStrategy {
    private static final Logger log = LoggerFactory.getLogger(SatelliteStrategy.class);

    private final SatelliteImageLoader loader;
    private final String fromDate;
    private final String toDate;
    private final JsonDataParser jsonParser;
    private final Path cachePath;

    public SatelliteStrategy(SatelliteImageLoader loader, String fromDate, String toDate,
                             JsonDataParser jsonParser, Path cachePath) {
        this.loader = loader;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.jsonParser = jsonParser;
        this.cachePath = cachePath;
    }

    /**
     * Synchronizes metadata for the given fields.
     * Fetches spectral data from the API and saves it as 'history.json' in each field's directory.
     * @param ids a set of unique identifiers (Field IDs or Note IDs) to synchronize
     */
    @Override
    public void synchronize(Set<String> ids) {
        log.info("Synchronizing satellite metadata for {} fields", ids.size());
        Set<Long> longIds = ids.stream()
                .map(Long::valueOf)
                .collect(Collectors.toSet());
        collectMetadata(longIds);
    }

    @Override
    public void process(ImageSource source) {
        // 1. Проверка типа (Safe Cast)
        if (!(source instanceof SatelliteImage sat) || sat.hasImage()) return;

        // 2. Поиск лучшего скана в истории (history.json)
        Path historyFile = cachePath.resolve(sat.getId()).resolve("history.json");
        FieldSpectralResponse response = readMetadataFromDisk(historyFile);

        if (response == null || response.data() == null) {
            log.warn("Нет данных мониторинга для поля {}", sat.getId());
            return;
        }

        SatelliteScan best = findBestScan(response.data(), sat.getPlanDate());

        if (best != null) {
            // Параметры только для формирования пути файла
            Map<String, String> pathParams = Map.of(
                    "index", sat.getIndex().getIndexName(),
                    "date", best.date()
            );
            Path localPath = resolvePath(cachePath, sat.getId(), pathParams);

            // 3. Логика кэша: Сначала диск, потом API
            byte[] data;
            if (Files.exists(localPath)) {
                data = readFromDisk(localPath);
            } else {
                data = loader.downloadBytes(best.getUrl(sat.getIndex().getIndexName()));
                if (data != null) saveToDisk(localPath, data);
            }

            // 4. Обогащение объекта
            if (data != null) {
                sat.setImageBytes(data);
                sat.setActualDate(LocalDate.parse(best.date())); // Специфика спутника успешно сохранена
            }
        }
    }

    /**
     * Resolves path following the pattern root/fieldId/indexName/fieldId_date_index.png
     *
     * @param root the root directory of the cache.
     * @param id the identifier of the entity
     * @param params implementation-specific parameters used to construct the file path.
     * @return resolved path to the File
     */
    @Override
    public Path resolvePath(Path root, String id, Map<String, String> params) {
        String index = params.get("index").toLowerCase();
        String date = params.get("date");
        String fileName = String.format("%s_%s_%s.png", id, date, index);

        return root.resolve(id).resolve(index).resolve(fileName);
    }

    @Override
    public SourceType getType() {
        return SourceType.SATELLITE;
    }

    public Map<Long, FieldSpectralResponse> collectMetadata(Set<Long> allIds) {
        Map<Long, FieldSpectralResponse> allMeta = new ConcurrentHashMap<>();
        List<Long> idsToFetch = new ArrayList<>();

        for (Long id : allIds) {
            Path historyFile = cachePath.resolve(id.toString()).resolve("history.json");
            if (Files.exists(historyFile)) {
                FieldSpectralResponse local = readMetadataFromDisk(historyFile);
                if (local != null) {
                    allMeta.put(id, local);
                    continue;
                }
            }
            idsToFetch.add(id);
        }

        if (!idsToFetch.isEmpty()) {
            fetchRemoteBatch(idsToFetch, allMeta);
        }
        return allMeta;
    }

    private void fetchRemoteBatch(List<Long> idsToFetch, Map<Long, FieldSpectralResponse> allMeta) {
        Semaphore semaphore = new Semaphore(3);
        log.info("Fetching metadata from API for {} fields", idsToFetch.size());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < idsToFetch.size(); i += 5) {
                List<Long> batch = idsToFetch.subList(i, Math.min(i + 5, idsToFetch.size()));

                executor.submit(() -> {
                    boolean acquired = false;
                    try {
                        semaphore.acquire();
                        acquired = true;
                        Map<Long, FieldSpectralResponse> remote = loader.fetchSpectralData(
                                batch,
                                fromDate,
                                toDate
                        );
                        Map<Long, FieldSpectralResponse> toSave = new HashMap<>();
                        for (Long id : batch) {
                            FieldSpectralResponse data;
                            if (remote != null && remote.containsKey(id)) {
                                // Данные есть — сохраняем нормально
                                data = remote.get(id);
                            } else {
                                // Данных в ответе нет — создаем "пустышку", чтобы закрыть дырку в кэше
                                log.warn("Field {} returned no data. Creating empty marker.", id);
                                data = new FieldSpectralResponse(id, List.of());
                            }
                            allMeta.put(id, data);
                            toSave.put(id, data);
                        }
                        if (!toSave.isEmpty()) {
                            saveMetadata(toSave);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("Synchronization interrupted");
                    } catch (Exception e) {
                        log.error("Unexpected error processing batch: {}", batch, e);
                    } finally {
                        if (acquired) {
                            semaphore.release();
                        }
                    }
                });
            }
        }
    }

    public SatelliteScan findBestScan(List<SatelliteScan> history, LocalDate targetDate) {
        if (history == null || history.isEmpty()) return null;

        SatelliteScan bestMatch = null;
        double minWeight = Double.MAX_VALUE;

        for (SatelliteScan scan : history) {
            LocalDate scanDate = LocalDate.parse(scan.date());
            long daysDiff = ChronoUnit.DAYS.between(targetDate, scanDate);

            if (daysDiff >= 0 && daysDiff <= 7) {
                if (scan.cloud() > 0.8) continue;

                // Вес: дни + облачность с коэффициентом "важности"
                double currentWeight = daysDiff + (scan.cloud() * 5.0);

                if (currentWeight < minWeight) {
                    minWeight = currentWeight;
                    bestMatch = scan;
                }
            }
        }
        return bestMatch;
    }

    private void saveMetadata(Map<Long, FieldSpectralResponse> metadata) {
        metadata.forEach((id, data) -> {
            try {
                Path historyFile = cachePath.resolve(id.toString()).resolve("history.json");
                jsonParser.write(historyFile, data);
                log.debug("Metadata for field {} successfully saved", id);
            } catch (Exception e) {
                log.error("Failed to save history for field {}: {}", id, e.getMessage());
            }
        });
    }

    private FieldSpectralResponse readMetadataFromDisk(Path historyFile) {
        if (!Files.exists(historyFile)) return null;
        return jsonParser.parse(historyFile, FieldSpectralResponse.class);
    }

    private byte[] readFromDisk(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            log.error("Ошибка чтения изображения из кэша {}: {}", path, e.getMessage());
            return null;
        }
    }

    private void saveToDisk(Path path, byte[] data) {
        if (data == null) return;
        try {
            // Создаем все родительские директории (например, root/fieldId/ndvi/)
            Files.createDirectories(path.getParent());
            Files.write(path, data);
            log.debug("Спутниковый снимок сохранен в кэш: {}", path.getFileName());
        } catch (IOException e) {
            log.error("Ошибка записи изображения на диск {}: {}", path, e.getMessage());
        }
    }

    /*
     * Mass Generation & Pre-caching
     * The following methods (preloadSatellites, buildDownloadTasks, etc.)
     * are reserved for implementing bulk image preloading to optimize
     * large-scale report generation.
     */
//        public void preloadSatellites(Set<Long> allIds) {
//        log.info("Starting satellite preload for {} fields", allIds.size());
//        // Шаг 1: Сбор json
//        Map<Long, FieldSpectralResponse> allMeta = collectMetadata(allIds);
//        // Шаг 2: план загрузки отсутствующих файлов
//        List<DownloadTask> tasks = buildDownloadTasks(allMeta);
//
//        if (tasks.isEmpty()) {
//            log.info("All satellite images are already cached.");
//            return;
//        }
//
//        log.info("Starting to download {} new satellite images.", tasks.size());
//        executeDownloads(tasks);
//    }

//    private List<DownloadTask> buildDownloadTasks(Map<Long, FieldSpectralResponse> allMeta) {
//        List<DownloadTask> tasks = new ArrayList<>();
//        allMeta.forEach((fieldId, response) -> {
//            for (SatelliteScan scan : response.data()) {
//                String date = scan.date();
//                scan.getAllIndices().forEach((indexName, indexData) -> {
//                    if (indexData.url() != null && !indexData.url().isBlank()) {
//                        if (!isImageCached(fieldId, indexName, date)) {
//                            tasks.add(new DownloadTask(
//                                    fieldId,
//                                    indexName,
//                                    date,
//                                    indexData.url()
//                            ));
//                        }
//                    }
//                });
//            }
//        });
//        return tasks;
//    }


//    private void executeDownloads(List<DownloadTask> tasks) {
//        ImageProviderStrategy satStrategy = registry.get(SourceType.SATELLITE);
//
//        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
//            for (DownloadTask task : tasks) {
//                executor.submit(() -> {
//                    byte[] data = loader.downloadBytes(task.url());
//                    if (data != null) {
//                        // Формируем параметры для стратегии, чтобы она дала нам путь
//                        Map<String, String> params = Map.of(
//                                "index", task.indexName(),
//                                "date", task.date()
//                        );
//                        Path path = satStrategy.resolvePath(cachePath, task.fieldId(), params);
//                        saveToDisk(path, data);
//                    }
//                });
//            }
//        }
//    }
}
