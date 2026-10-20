package com.viking.generator.http.strategy;

import com.viking.generator.config.NoteConfig;
import com.viking.generator.http.LoadResult;
import com.viking.generator.http.NoteImageLoader;
import com.viking.generator.model.media.ImageSource;
import com.viking.generator.model.media.NoteImage;
import com.viking.generator.model.common.SourceType;
import com.viking.generator.util.HeicConverter;
import com.viking.generator.util.ImageUtils;
import com.viking.generator.util.ImageUtils.ImageKind;
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
    private final HeicConverter heicConverter;

    public NoteStrategy(NoteImageLoader loader, NoteConfig config, HeicConverter heicConverter) {
        this.loader = loader;
        this.cachePath = config.cachePath();
        this.subDir = config.dir();
        this.defaultExt = config.extension();
        this.heicConverter = heicConverter;
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

            // 3. Сохраняем то, что скачалось (PDF-вложения пропускаем, HEIC конвертируем в JPEG)
            result.images().forEach((id, resource) -> {
                byte[] data = toSupportedPhoto(id, resource.data());
                if (data == null) return;
                Path target = resolvePath(cachePath, id +".*", id + "." + defaultExt, subDir);
                writeToDisk(target, data);
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
        Optional<byte[]> cached = readFromDisk(localPath);
        Optional<byte[]> raw = cached.isPresent()
                ? cached
                : Optional.ofNullable(loader.load(List.of(id)).images().get(id))
                        .map(resource -> {
                            byte[] data = resource.data();
                            writeToDisk(localPath, data);
                            return data;
                        });

        raw.ifPresent(bytes -> note.setImageBytes(toSupportedPhoto(id, bytes)));
    }

    /**
     * Приводит скачанные байты вложения к формату, который можно встроить в PDF:
     * классические растры (JPEG/PNG/GIF/BMP) оставляем как есть, HEIC конвертируем
     * в JPEG через ffmpeg, а всё остальное (PDF-документы и т.п.) пропускаем.
     */
    private byte[] toSupportedPhoto(String id, byte[] raw) {
        if (raw == null) return null;
        ImageKind kind = ImageUtils.detectKind(raw);
        switch (kind) {
            case JPEG, PNG, GIF, BMP -> { return raw; }
            case HEIC -> {
                byte[] jpeg = heicConverter.toJpeg(raw);
                if (jpeg != null) {
                    log.info("HEIC {} конвертирован в JPEG ({} байт)", id, jpeg.length);
                    return jpeg;
                }
                log.warn("Не удалось конвертировать HEIC {} — фото пропущено", id);
                return null;
            }
            default -> {
                log.info("Пропускаем вложение {} (формат {}, {} байт, не изображение)",
                        id, kind, raw.length);
                return null;
            }
        }
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
