package com.viking.field_passport_generator.service;

import com.viking.field_passport_generator.http.strategy.ImageProviderStrategy;
import com.viking.field_passport_generator.model.ImageSource;
import com.viking.field_passport_generator.model.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class ImageCacheService {
    private final Map<SourceType, ImageProviderStrategy> registry;
    private final Path cachePath;
    private final Logger log = LoggerFactory.getLogger(ImageCacheService.class);

    public ImageCacheService(Path cachePath, List<ImageProviderStrategy> strategies) {
        this.registry = strategies.stream().collect(Collectors.toMap(ImageProviderStrategy::getType, s -> s));
        this.cachePath = cachePath;
        initCacheDirectory();
    }

    public void sync(Collection<?> ids, SourceType type) {
        if (ids == null || ids.isEmpty()) return;

        Set<String> stringIds = ids.stream()
                .map(String::valueOf)
                .collect(Collectors.toSet());

        ImageProviderStrategy strategy = registry.get(type);
        if (strategy != null) {
            strategy.synchronize(stringIds);
        }
    }

    public void fillImages(List<? extends ImageSource> images) {
        for (ImageSource img : images) {
            ImageProviderStrategy strategy = registry.get(img.getType());
            if (strategy != null) {
                strategy.process(img);
            }
        }
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
}
