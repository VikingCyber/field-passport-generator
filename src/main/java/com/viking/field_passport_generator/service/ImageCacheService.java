package com.viking.field_passport_generator.service;

import com.viking.field_passport_generator.http.ImageLoader;
import com.viking.field_passport_generator.http.LoadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ImageCacheService {
    private final ImageLoader loader;
    private final Path cachePath;
    private final Logger log = LoggerFactory.getLogger(ImageCacheService.class);

    public ImageCacheService(ImageLoader loader, Path cachePath) {
        this.loader = loader;
        this.cachePath = cachePath;
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

    public void preloadImages(Set<String> allIds) {
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
            LoadResult result = loader.load(batch);
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
