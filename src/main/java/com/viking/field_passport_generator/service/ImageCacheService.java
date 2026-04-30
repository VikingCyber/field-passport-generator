package com.viking.field_passport_generator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viking.field_passport_generator.data.dto.satellite.FieldSpectralResponse;
import com.viking.field_passport_generator.data.dto.satellite.IndexData;
import com.viking.field_passport_generator.data.dto.satellite.SatelliteScan;
import com.viking.field_passport_generator.http.DownloadTask;
import com.viking.field_passport_generator.http.LoadResult;
import com.viking.field_passport_generator.http.NoteImageLoader;
import com.viking.field_passport_generator.http.SatelliteImageLoader;
import com.viking.field_passport_generator.model.SatelliteImage;
import com.viking.field_passport_generator.model.SpectralIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ImageCacheService {
    private final NoteImageLoader noteImageLoader;
    private final SatelliteImageLoader satelliteImageLoader;
    private final Path cachePath;
    private final String fromDate;
    private final String toDate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Logger log = LoggerFactory.getLogger(ImageCacheService.class);

    public ImageCacheService(NoteImageLoader noteImageLoader, SatelliteImageLoader satelliteImageLoader,
                             Path cachePath, String fromDate, String toDate) {
        this.noteImageLoader = noteImageLoader;
        this.satelliteImageLoader = satelliteImageLoader;
        this.cachePath = cachePath;
        this.fromDate = fromDate;
        this.toDate = toDate;
        initCacheDirectory();
    }

    private void initCacheDirectory() {
        try {
            Files.createDirectories(cachePath);
            log.info("Cache directory ready: {}", cachePath);
        } catch (IOException e) {
            log.error("Cannot create cache directory: {}", cachePath, e);
            throw new RuntimeException("Failed to initialize cache", e);
        }
    }

    public void preloadNotes(Set<String> allIds) {
        Set<String> existingIdsInCache = getExistingCacheIds();

        List<String> imgToDownload = allIds.stream()
                .filter(id -> !existingIdsInCache.contains(id))
                .toList();

        if (imgToDownload.isEmpty()) {
            log.info("Кэш актуален (все {} фото на месте).", allIds.size());
            return;
        }

        int totalLinksFound = 0;
        int totalDownloaded = 0;
        int totalFailed = 0;

        log.info("=== Synchronization started : {} photos ====", imgToDownload.size());

        for (int i = 0; i < imgToDownload.size(); i += 50) {
            List<String> batch = imgToDownload.subList(i, Math.min(i + 50, imgToDownload.size()));
            LoadResult result = noteImageLoader.load(batch);
            result.images().forEach((id, resource) -> {
                try {
                    Files.write(cachePath.resolve(id + "." + resource.extension()), resource.data());
                } catch (IOException e) {
                    log.error("Error recording file on disk {}: {}", id, e.getMessage());
                }
            });
            totalLinksFound += result.linksFound();
            totalDownloaded += result.images().size();
            totalFailed += result.downloadErrors();
        }

        printCacheReport(imgToDownload.size(), totalLinksFound, totalDownloaded, totalFailed);
    }

    public void preloadSatellites(Set<Long> allIds) {
        log.info("Starting satellite preload for {} fields", allIds.size());
        // Шаг 1: Сбор json
        Map<Long, FieldSpectralResponse> allMeta = collectMetadata(allIds);
        // Шаг 2: план загрузки отсутствующих файлов
        List<DownloadTask> tasks = buildDownloadTasks(allMeta);

        if (tasks.isEmpty()) {
            log.info("All satellite images are already cached.");
            return;
        }

        log.info("Starting to download {} new satellite images.", tasks.size());
        executeDownloads(tasks);
    }


    public Map<Long, FieldSpectralResponse> collectMetadata(Set<Long> allIds) {
        Map<Long, FieldSpectralResponse> allMeta = new ConcurrentHashMap<>();
        List<Long> idsToFetch = new ArrayList<>();

        for (Long id : allIds) {
            String idStr = String.valueOf(id);
            Path historyFile = cachePath.resolve(idStr).resolve("history.json");

            if (Files.exists(historyFile)) {
                FieldSpectralResponse local = readMetadataFromDisk(historyFile);
                if (local != null) {
                    allMeta.put(id, local);
                    continue;
                }
            }
            idsToFetch.add(id);
        }

        Semaphore semaphore = new Semaphore(3);
        if (!idsToFetch.isEmpty()) {
            log.info("Fetching metadata from API for {} fields", idsToFetch.size());
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < idsToFetch.size(); i += 5) {
                    List<Long> batch = idsToFetch.subList(i, Math.min(i + 5, idsToFetch.size()));

                    executor.submit(() -> {
                        try {
                            semaphore.acquire();
                            Map<Long, FieldSpectralResponse> remote = satelliteImageLoader.fetchSpectralData(
                                    batch,
                                    fromDate,
                                    toDate
                            );
                            for (Long id : batch) {
                                if (remote != null && remote.containsKey(id)) {
                                    // Данные есть — сохраняем нормально
                                    FieldSpectralResponse data = remote.get(id);
                                    allMeta.put(id, data);
                                    saveMetadata(Map.of(id, data));
                                } else {
                                    // Данных в ответе нет — создаем "пустышку", чтобы закрыть дырку в кэше
                                    log.warn("Field {} returned no data. Creating empty marker.", id);
                                    FieldSpectralResponse emptyResponse = new FieldSpectralResponse(id, List.of());
                                    allMeta.put(id, emptyResponse); // Чтобы main-поток тоже знал, что данных нет
                                    saveMetadata(Map.of(id, emptyResponse));
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.error("Synchronization interrupted");
                        } finally {
                            semaphore.release();
                        }
                    });
                }
            }
        } else {
            log.info("All fields are already up-to-date in cache. Nothing to fetch.");
        }
        return allMeta;
    }

    private FieldSpectralResponse readMetadataFromDisk(Path historyFile) {
        try {
            return objectMapper.readValue(historyFile.toFile(), FieldSpectralResponse.class);
        } catch (IOException e) {
            log.error("Error reading history.json from {}: {}", historyFile, e.getMessage());
            return null;
        }
    }

    private List<DownloadTask> buildDownloadTasks(Map<Long, FieldSpectralResponse> allMeta) {
        List<DownloadTask> tasks = new ArrayList<>();
        allMeta.forEach((fieldId, response) -> {
            for (SatelliteScan scan : response.data()) {
                String date = scan.date();
                scan.getAllIndices().forEach((indexName, indexData) -> {
                    if (indexData.url() != null && !indexData.url().isBlank()) {
                        if (!isImageCached(fieldId, indexName, date)) {
                            tasks.add(new DownloadTask(
                                    fieldId,
                                    indexName,
                                    date,
                                    indexData.url()
                            ));
                        }
                    }
                });
            }
        });
        return tasks;
    }

    private void executeDownloads(List<DownloadTask> tasks) {
        log.info("Начинаем закачку {} спутниковых снимков...", tasks.size());

        AtomicInteger downloaded = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        // Используем виртуальные потоки
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (DownloadTask task : tasks) {

                // Если в лоадере сработал предохранитель — выходим
                if (satelliteImageLoader.isCircuitBroken()) {
                    log.error("Загрузка остановлена: выбит предохранитель (Circuit Breaker)!");
                    break;
                }

                executor.submit(() -> {
                    // Внутри лоадера уже есть семафор, так что мы просто вызываем
                    byte[] data = satelliteImageLoader.downloadBytes(task.url());

                    if (data != null) {
                        saveImageToDisk(task, data);
                        downloaded.incrementAndGet();
                    } else {
                        failed.incrementAndGet();
                    }
                });
            }
        }

        log.info("Загрузка завершена. Успешно: {}, Ошибок: {}", downloaded.get(), failed.get());
    }

    private void saveMetadata(Map<Long, FieldSpectralResponse> metadata) {
        metadata.forEach((id, data) -> {
            try {
                Path fieldDir = cachePath.resolve(id.toString());
                Files.createDirectories(fieldDir);
                Path historyFile = fieldDir.resolve("history.json");
                objectMapper.writeValue(historyFile.toFile(), data);
                log.debug("Metadata for field {} successfully saved", id);
            } catch (IOException e) {
                log.error("Failed to save history for field {}: {}", id, e.getMessage());
            }
        });
    }

    /**
     * Saves image with full metadata in the filename
     * Format: fieldId_date_index.png
     */
    private void saveImageToDisk(DownloadTask task, byte[] data) {
        try {
            String index = task.indexName().toLowerCase();
            Path typeDir = cachePath.resolve(task.fieldId().toString()).resolve(index);
            Files.createDirectories(typeDir);

            String fileName = String.format("%s_%s_%s.png",
                    task.fieldId(), task.date(), index);

            Files.write(typeDir.resolve(fileName), data);
            log.debug("Image saved: {}", fileName);

        } catch (IOException e) {
            log.error("Failed to write image for field {} (index {}): {}",
                    task.fieldId(), task.indexName(), e.getMessage());
        }
    }

    /**
     * Checks existence with the same full filename pattern
     */
    private boolean isImageCached(Long fieldId, String indexName, String date) {
        String index = indexName.toLowerCase();
        String fileName = String.format("%s_%s_%s.png", fieldId, date, index);

        Path path = cachePath.resolve(fieldId.toString())
                .resolve(index)
                .resolve(fileName);

        return Files.exists(path);
    }

    private void printCacheReport(int totalMissing, int totalLinksFound, int totalDownloaded, int totalFailed) {
        int missingInApi = totalMissing - totalLinksFound;

        log.info("=== Отчет по прогреву кэша ===");
        log.info("Всего не хватало:   {}", totalMissing);
        log.info("Найдено ссылок:    {}", totalLinksFound);
        log.info("Успешно скачано:   {}", totalDownloaded);
        log.info("--- Проблемы ---");
        log.info("Отсутствуют в API: {} (записи без фото)", missingInApi);
        log.info("Ошибка загрузки:   {} (битые ссылки/404)", totalFailed);
        log.info("==============================");
    }

    private Set<String> getExistingCacheIds() {
        try (Stream<Path> stream = Files.list(cachePath)) {
            return stream
                    .map(path -> path.getFileName().toString())
                    .map(name -> name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            log.error("Failed to read cache directory", e);
            return Set.of();
        }
    }

    public void enrich(List<SatelliteImage> images) {
        for (SatelliteImage img : images) {
            Long fieldId = img.getFieldId();
            Path historyFile = cachePath.resolve(String.valueOf(fieldId)).resolve("history.json");

            if (!Files.exists(historyFile)) {
                log.warn("History file not found for field {}", fieldId);
                continue;
            }

            FieldSpectralResponse response = readMetadataFromDisk(historyFile);
            if (response == null || response.data() == null) {
                continue;
            }

            SatelliteScan bestScan = findBestScan(response.data(), img.getPlanDate());

            if (bestScan != null) {
                LocalDate actualScanDate = LocalDate.parse(bestScan.date());
                img.setActualDate(actualScanDate);

                SpectralIndex requiredIndex = img.getIndex();
                if (requiredIndex == null) continue;

                IndexData indexData = bestScan.getAllIndices().get(requiredIndex.getIndexName());
                if (indexData != null && indexData.url() != null) {
                    byte[] data = fetchSingleImage(
                            fieldId,
                            requiredIndex.getIndexName(),
                            bestScan.date(),
                            indexData.url()
                    );
                    img.setImageBytes(data);
                }
            } else {
                log.debug("No suitable scan found for field {} near date {}", fieldId, img.getPlanDate());
            }
        }
    }

    private SatelliteScan findBestScan(List<SatelliteScan> history, LocalDate targetDate) {
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

    private byte[] fetchSingleImage(Long fieldId, String indexName, String date, String url) {
        String index = indexName.toLowerCase();
        String fileName = String.format("%s_%s_%s.png", fieldId, date, index);
        Path imagePath = cachePath.resolve(fieldId.toString()).resolve(index).resolve(fileName);

        if (Files.exists(imagePath)) {
            try {
                return Files.readAllBytes(imagePath);
            } catch (IOException e) {
                log.error("Error reading cached image {}: {}", fileName, e.getMessage());
            }
        }

        log.info("Downloading missing image for passport: {}", fileName);
        byte[] data = satelliteImageLoader.downloadBytes(url);
        if (data != null) {
            saveImageToDisk(new DownloadTask(fieldId, indexName, date, url), data);
            return data;
        }
        return null;
    }

    public byte[] getImageBytes(String id) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(cachePath, id + ".*")) {
            Iterator<Path> iterator = stream.iterator();
            if (iterator.hasNext()) {
                return Files.readAllBytes(iterator.next());
            }
        } catch (IOException e) {
            log.error("Failed to read cached image for ID {}: {}", id, e.getMessage());
        }
        return null;
    }
}
