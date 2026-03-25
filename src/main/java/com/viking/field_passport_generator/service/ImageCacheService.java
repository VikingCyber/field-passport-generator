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

public class ImageCacheService {
    private final ImageLoader loader;
    private final Path cachePath;
    private final Logger log = LoggerFactory.getLogger(ImageCacheService.class);
    private final Semaphore semaphore = new Semaphore(10);

    public ImageCacheService(ImageLoader loader, AppConfig appConfig) {
        this.loader = loader;
        this.cachePath = Path.of(appConfig.getString("app.cache-dir"));
    }

    public void preloadImages(Set<String> allIds) {
        List<String> ImgToDownload = allIds.stream()
                .filter(id -> Files.notExists(cachePath.resolve(id + ".jpg")))
                .toList();

        if (ImgToDownload.isEmpty()) {
            log.info("Кэш актуален (все {} фото на месте).", allIds.size());
            return;
        }

        log.info("В кэше не хватает {} фото. Начинаю загрузку...", ImgToDownload.size());

        Map<String, String> links = loader.fetchDownloadUrls(ImgToDownload);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            links.forEach((id, url) -> executor.submit(() -> {
                try {
                    semaphore.acquire();
                    byte[] data = loader.downloadBytes(url);
                    if (data != null) {
                        Files.write(cachePath.resolve(id + ".jpg"), data);
                    }
                } catch (InterruptedException | IOException e) {
                    log.error("Ошибка загрузки {}: {}", id, e.getMessage());
                } finally {
                    semaphore.release();
                }
            }));
        }
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
