package com.viking.generator.http.strategy;

import com.viking.generator.config.NoteConfig;
import com.viking.generator.http.LoadResult;
import com.viking.generator.http.LoadedResource;
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
import java.io.InputStream;
import java.nio.file.DirectoryStream;
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

        repairMisnamedFiles();

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

            // 3. Сохраняем то, что скачалось: поддерживаемые форматы — в нормализованном виде,
            // остальные (PDF и т.п.) — как есть, но с честным расширением
            result.images().forEach((id, resource) -> {
                byte[] photo = toSupportedPhoto(id, resource.data());
                persist(id, photo != null ? photo : resource.data());
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
        Optional<byte[]> raw = readFromDisk(localPath);
        if (raw.isEmpty()) {
            LoadedResource resource = loader.load(List.of(id)).images().get(id);
            if (resource != null) {
                byte[] bytes = resource.data();
                byte[] photo = toSupportedPhoto(id, bytes);
                persist(id, photo != null ? photo : bytes);
                raw = Optional.of(bytes);
            }
        }

        raw.ifPresent(bytes -> {
            byte[] photo = toSupportedPhoto(id, bytes);
            if (photo != null) note.setImageBytes(photo);
        });
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

    /**
     * Сохраняет вложение в кэш с расширением, соответствующим реальному формату
     * (а не фиксированному defaultExt = png). Перед записью удаляет устаревшие
     * файлы того же id с другим расширением, чтобы не копились дубли.
     */
    private void persist(String id, byte[] data) {
        String ext = ImageUtils.extensionFor(ImageUtils.detectKind(data));
        if (ext == null) {
            log.warn("Пропускаем запись вложения {} — формат не распознан", id);
            return;
        }
        Path dir = cachePath.resolve(subDir);
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> stale = Files.newDirectoryStream(dir, id + ".*")) {
                for (Path old : stale) {
                    Files.deleteIfExists(old);
                }
            } catch (IOException e) {
                log.warn("Не удалось удалить устаревшие кэш-файлы для {}: {}", id, e.getMessage());
            }
        }
        Path target = resolvePath(cachePath, id + ".*", id + "." + ext, subDir);
        writeToDisk(target, data);
    }

    /**
     * Переименовывает кэш-файлы со «лживым» расширением (исторические вложения
     * сохранялись все как .png) в честное по содержимому: HEIC → .heic,
     * JPEG → .jpg, PDF → .pdf и т.д. Проверяет только сигнатуру, читая 12 байт.
     */
    private void repairMisnamedFiles() {
        Path dir = cachePath.resolve(subDir);
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile).forEach(this::renameToRealExtension);
        } catch (IOException e) {
            log.warn("Не удалось просканировать кэш заметок для переименования: {}", e.getMessage());
        }
    }

    private void renameToRealExtension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return;
        String currentExt = name.substring(dot + 1).toLowerCase();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] magic = in.readNBytes(12);
            String expectedExt = ImageUtils.extensionFor(ImageUtils.detectKind(magic));
            if (expectedExt == null || expectedExt.equals(currentExt)) return;
            Path renamed = file.resolveSibling(name.substring(0, dot + 1) + expectedExt);
            if (Files.exists(renamed)) Files.delete(renamed);
            Files.move(file, renamed);
            log.info("Переименован кэш-файл {} → {} (реальное расширение)", name, renamed.getFileName());
        } catch (IOException e) {
            log.warn("Не удалось переименовать {}: {}", name, e.getMessage());
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
