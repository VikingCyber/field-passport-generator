package com.viking.field_passport_generator.service;

import com.viking.field_passport_generator.config.AppConfig;
import com.viking.field_passport_generator.http.ImageLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class ImageCacheService {
    private final ImageLoader loader;
    private final Path cachePath;
    private final Logger log = LoggerFactory.getLogger(ImageCacheService.class);
    private final Semaphore semaphore = new Semaphore(10);

    public ImageCacheService(ImageLoader loader, Path cachePath) {
        this.loader = loader;
        this.cachePath = cachePath;
    }

    public void preloadImages(Set<String> allIds) {
        List<String> imgToDownload = allIds.stream()
                .filter(id -> Files.notExists(cachePath.resolve(id + ".jpg")))
                .toList();

        if (imgToDownload.isEmpty()) {
            log.info("Кэш актуален (все {} фото на месте).", allIds.size());
            return;
        }

        AtomicInteger totalLinksFound = new AtomicInteger(0);
        AtomicInteger totalDownloaded = new AtomicInteger(0);

        log.info("=== Synchronization started : {} photos ====", imgToDownload.size());

        for (int i = 0; i < imgToDownload.size(); i += 50) {
            List<String> batch = imgToDownload.subList(i, Math.min(i + 50, imgToDownload.size()));
            Map<String, String> links = loader.fetchDownloadUrls(batch);

            totalLinksFound.addAndGet(links.size());

            if (links.isEmpty()) {
                log.debug("Batch {}-{}: Links not found", i, i + batch.size());
                continue;
            }

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                links.forEach((id, url) -> executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        byte[] data = loader.downloadBytes(url);
                        if (data != null) {
                            Files.write(cachePath.resolve(id + ".jpg"), data);
                            totalDownloaded.incrementAndGet();
                        }
                    } catch (IOException e) {
                        log.error("Error recording file on disk {}: {}", id, e.getMessage());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("Interrupted while downloading image: {}: {}", id, e.getMessage());
                    } finally {
                        semaphore.release();
                    }
                }));
            }
        }

        int missingInApi = imgToDownload.size() - totalLinksFound.get();
        int failedDownloads = totalLinksFound.get() - totalDownloaded.get();

        log.info("=== Отчет по прогреву кэша ===");
        log.info("Всего не хватало:   {}", imgToDownload.size());
        log.info("Найдено ссылок:    {}", totalLinksFound.get());
        log.info("Успешно скачано:   {}", totalDownloaded.get());
        log.info("--- Проблемы ---");
        log.info("Отсутствуют в API: {} (записи без фото)", missingInApi);
        log.info("Ошибка загрузки:   {} (битые ссылки/404)", failedDownloads);
        log.info("==============================");
    }

    public byte[] getImageBytes(String id) {
        Path p = cachePath.resolve(id + ".jpg");
        try {
            return Files.exists(p) ? Files.readAllBytes(p) : null;
        } catch (IOException e) {
            return null;
        }
    }
}
