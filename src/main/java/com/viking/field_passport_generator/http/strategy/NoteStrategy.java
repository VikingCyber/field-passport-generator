package com.viking.field_passport_generator.http.strategy;

import com.viking.field_passport_generator.http.LoadResult;
import com.viking.field_passport_generator.http.NoteImageLoader;
import com.viking.field_passport_generator.model.ImageSource;
import com.viking.field_passport_generator.model.NoteImage;
import com.viking.field_passport_generator.model.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NoteStrategy implements ImageProviderStrategy {
    private static final Logger log = LoggerFactory.getLogger(NoteStrategy.class);
    private final NoteImageLoader loader;
    private final Path cachePath;

    public NoteStrategy(NoteImageLoader loader, Path cachePath) {
        this.loader = loader;
        this.cachePath = cachePath;
    }

    @Override
    public void synchronize(Set<String> ids) {
        if (ids.isEmpty()) return;


        Set<String> existingIds = getExistingCacheIds();
        List<String> toFetch = ids.stream()
                .map(String::valueOf)
                .filter(id -> !existingIds.contains(id))
                .toList();

        if (toFetch.isEmpty()) {
            log.info("Note cache is up to date");
            return;
        }

        log.info("=== Synchronization started: {} new photos ====", toFetch.size());

        int totalLinksFound = 0;
        int totalDownloaded = 0;
        int totalFailed = 0;

        // 2. Твой батчинг по 50 штук
        for (int i = 0; i < toFetch.size(); i += 50) {
            List<String> batch = toFetch.subList(i, Math.min(i + 50, toFetch.size()));

            // Вызываем твой loader, который внутри использует виртуальные потоки
            LoadResult result = loader.load(batch);

            // 3. Сохраняем то, что скачалось
            result.images().forEach((id, resource) -> {
                try {
                    Path target = cachePath.resolve(id + "." + resource.extension());
                    Files.write(target, resource.data());
                } catch (IOException e) {
                    log.error("Error recording file on disk {}: {}", id, e.getMessage());
                }
            });

            totalLinksFound += result.linksFound();
            totalDownloaded += result.images().size();
            totalFailed += result.downloadErrors();
        }

        printCacheReport(toFetch.size(), totalLinksFound, totalDownloaded, totalFailed);
    }

    @Override
    public void process(ImageSource source) {
        if (!(source instanceof NoteImage note) || note.hasImage()) return;

        String id = note.getId();

        Path localPath = findOnDisk(id);

        byte[] data = null;
        if (localPath != null && Files.exists(localPath)) {
            data = readFromDisk(localPath);
        } else {
            var result = loader.load(List.of(id));

            if (result.images().containsKey(id)) {
                var resource = result.images().get(id);
                data = resource.data();

                Path targetPath = cachePath.resolve(id + "." + resource.extension());
                saveToDisk(targetPath, data);
            }
        }

        if (data != null) {
            note.setImageBytes(data);
        }
    }

    @Override
    public Path resolvePath(Path root, String id, Map<String, String> params) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, id + ".*")) {
            Iterator<Path> iterator = stream.iterator();
            if (iterator.hasNext()) {
                return iterator.next();
            }
        } catch (IOException e) {
            log.error("Error searching for note image {}: {}", id, e.getMessage());
        }

        // Если файла нет, возвращаем путь по умолчанию для сохранения
        String ext = params.getOrDefault("extension", "png");
        return root.resolve(id + "." + ext);
    }

    @Override
    public SourceType getType() {
        return SourceType.NOTE;
    }

    private Path findOnDisk(String id) {
        if (!Files.isDirectory(cachePath)) {
            return null;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(cachePath, id + ".*")) {
            Iterator<Path> iterator = stream.iterator();
            return iterator.hasNext() ? iterator.next() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private byte[] readFromDisk(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            log.error("Error reading note from disk {}: {}", path, e.getMessage());
            return null;
        }
    }

    private void saveToDisk(Path path, byte[] data) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, data);
        } catch (IOException e) {
            log.error("Error saving note to disk {}: {}", path, e.getMessage());
        }
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
        if (!Files.exists(cachePath)) return Set.of();
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
}
