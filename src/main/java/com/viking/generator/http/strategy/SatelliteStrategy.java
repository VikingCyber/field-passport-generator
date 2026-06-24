package com.viking.generator.http.strategy;

import com.viking.generator.config.SatelliteConfigInternal;
import com.viking.generator.data.dto.satellite.FieldSpectralResponse;
import com.viking.generator.data.dto.satellite.SatelliteScan;
import com.viking.generator.http.SatelliteImageLoader;
import com.viking.generator.model.media.ImageSource;
import com.viking.generator.model.media.SatelliteImage;
import com.viking.generator.model.common.SourceType;
import com.viking.generator.util.ImageUtils;
import com.viking.generator.util.JsonDataParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final JsonDataParser jsonParser;
    private final Path cachePath;
    private final String fromDate;
    private final String toDate;
    private final int scanWindowDays;
    private final double maxCloudThreshold;
    private final double cloudWeightFactor;
    private final String defaultExt;

    public SatelliteStrategy(SatelliteImageLoader loader, JsonDataParser jsonParser,
                             SatelliteConfigInternal config) {
        this.loader = loader;
        this.jsonParser = jsonParser;
        this.cachePath = config.cachePath();
        this.fromDate = config.fromDate();
        this.toDate = config.toDate();
        this.scanWindowDays = config.scanWindowDays();
        this.maxCloudThreshold = config.maxCloudThreshold();
        this.cloudWeightFactor = config.cloudWeightFactor();
        this.defaultExt = config.extension();
    }

    @Override
    public Logger getLogger() {
        return log;
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

    private Optional<SatelliteScan> getBestScanForSat(SatelliteImage sat) {
        Path historyFile = cachePath.resolve(sat.getId()).resolve("history.json");
        return Optional.ofNullable(readMetadataFromDisk(historyFile))
                .map(FieldSpectralResponse::data)
                .flatMap(scans -> Optional.ofNullable(findBestScan(scans, sat.getPlanDate())));
    }

    private Optional<byte[]> downloadAndCache(SatelliteScan best, String id, String indexName, String filePrefix) {
        String url = best.getUrl(indexName);
        return Optional.ofNullable(loader.downloadBytes(url))
                .map(data -> {
                   String realExt = ImageUtils.determineExtension(url);
                   Path targetPath = resolvePath(cachePath, filePrefix + ".",
                           filePrefix + "." + realExt, id, indexName);
                   writeToDisk(targetPath, data);
                   return data;
                });
    }

    @Override
    public void process(ImageSource source) {
        // 1. Проверка типа (Safe Cast)
        if (!(source instanceof SatelliteImage sat) || sat.hasImage()) return;

        getBestScanForSat(sat).ifPresent(best -> {
            String indexName = sat.getIndex().getIndexName();
            String filePrefix = sat.getId() + "_" + best.date() + "_" + indexName;
            Path localPath = resolvePath(cachePath, filePrefix + ".*", filePrefix + "." + defaultExt,
                    sat.getId(), indexName);
            readFromDisk(localPath)
                    .or(() -> downloadAndCache(best, sat.getId(), indexName, filePrefix))
                    .ifPresent(data -> {
                        sat.setImageBytes(data);
                        sat.setActualDate(LocalDate.parse(best.date()));
                        log.debug("Processed: {} for date {}", indexName, best.date());
                    });
        });
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

            if (daysDiff >= 0 && daysDiff <= scanWindowDays) {
                if (scan.cloud() > maxCloudThreshold) continue;

                // Вес: дни + облачность с коэффициентом "важности"
                double currentWeight = daysDiff + (scan.cloud() * cloudWeightFactor);

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
