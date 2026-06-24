package com.viking.generator.http.strategy;

import com.viking.generator.config.NoteConfig;
import com.viking.generator.http.LoadResult;
import com.viking.generator.http.NoteImageLoader;
import com.viking.generator.model.media.ImageSource;
import com.viking.generator.model.media.NoteImage;
import com.viking.generator.model.common.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NoteStrategy implements ImageProviderStrategy {
    private static final Logger log = LoggerFactory.getLogger(NoteStrategy.class);
    private final NoteImageLoader loader;
    private final Path cachePath;
    private final String subDir;
    private final String defaultExt;

    public NoteStrategy(NoteImageLoader loader, NoteConfig config) {
        this.loader = loader;
        this.cachePath = config.cachePath();
        this.subDir = config.dir();
        this.defaultExt = config.extension();
    }

    @Override
    public Logger getLogger() {
        return log;
    }

    @Override
    public void synchronize(Set<String> ids) {
        if (ids.isEmpty()) return;


        Set<String> existingIds = getExistingCacheIds();
        List<String> toFetch = ids.stream()
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
                Path target = resolvePath(cachePath, id +".*", id + "." + defaultExt, subDir);
                writeToDisk(target, resource.data());
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

        Path localPath = resolvePath(cachePath, id + ".*", id + "." + defaultExt, subDir);
        readFromDisk(localPath).or(() -> Optional.ofNullable(loader.load(List.of(id)).images().get(id))
                    .map(resource -> {
                        byte[] data = resource.data();
                        writeToDisk(localPath, data);
                        return data;
                    })).ifPresent(note::setImageBytes);
    }

    @Override
    public SourceType getType() {
        return SourceType.NOTE;
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
        Path notesPath = cachePath.resolve(subDir);
        if (!Files.exists(notesPath)) return Set.of();
        try (Stream<Path> stream = Files.list(notesPath)) {
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
