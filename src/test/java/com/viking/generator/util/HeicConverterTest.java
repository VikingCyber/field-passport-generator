package com.viking.generator.util;

import com.viking.generator.util.ImageUtils.ImageKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HeicConverterTest {

    /** Каталог кэша вложений заметок (gitignored, создаётся рантаймом). */
    private static final Path CACHE_NOTES_DIR = Path.of("cache", "images", "notes");

    /**
     * Возвращает тестовый HEIF-файл:
     * приоритет — настоящий HEIC из кэша приложения (на машине оператора),
     * иначе — встроенный мини-фикстур (AVIF, 325 байт, тот же HEIF-контейнер).
     */
    private byte[] loadHeifFixture() throws IOException {
        Optional<Path> cached = findCachedHeic();
        if (cached.isPresent()) {
            return Files.readAllBytes(cached.get());
        }
        try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream("/heif/heif-sample.avif"),
                "heif-sample.avif не найден в test resources")) {
            return in.readAllBytes();
        }
    }

    private Optional<Path> findCachedHeic() {
        if (!Files.isDirectory(CACHE_NOTES_DIR)) return Optional.empty();
        try (Stream<Path> files = Files.list(CACHE_NOTES_DIR)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(this::isHeicByMagic)
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private boolean isHeicByMagic(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(12);
            return ImageUtils.detectKind(head) == ImageKind.HEIC;
        } catch (IOException e) {
            return false;
        }
    }

    @Test
    @DisplayName("isAvailable = false, если ffmpeg не найден")
    void unavailableWhenBinaryMissing() {
        HeicConverter converter = new HeicConverter("no-such-ffmpeg-binary-xyz");
        assertFalse(converter.isAvailable());
    }

    @Test
    @DisplayName("toJpeg(null) и toJpeg(пусто) возвращают null")
    void rejectsNullOrEmpty() {
        HeicConverter converter = new HeicConverter("no-such-ffmpeg-binary-xyz");
        assertNull(converter.toJpeg(null));
        assertNull(converter.toJpeg(new byte[0]));
    }

    @Test
    @DisplayName("toJpeg без доступного ffmpeg возвращает null, а не исключение")
    void returnsNullWhenFfmpegMissing() {
        HeicConverter converter = new HeicConverter("no-such-ffmpeg-binary-xyz");
        byte[] heic = new byte[20];
        heic[4] = 0x66; heic[5] = 0x74; heic[6] = 0x79; heic[7] = 0x70; // "ftyp"
        heic[8] = 0x68; heic[9] = 0x65; heic[10] = 0x69; heic[11] = 0x63; // "heic"
        assertNull(converter.toJpeg(heic));
    }

    @Test
    @DisplayName("На машине разработчика ffmpeg должен быть доступен (smoke)")
    void ffmpegAvailableOnDevMachine() {
        // Smoke-тест: проект запускается только на машине оператора, где ffmpeg установлен.
        // Не скипается намеренно: конвертация HEIC — жёсткая зависимость этой машины.
        assertTrue(new HeicConverter("ffmpeg").isAvailable());
    }

    @Test
    @DisplayName("HEIC/HEIF конвертируется в валидный JPEG через ffmpeg (реальный файл или фикстур)")
    void convertsHeifToValidJpeg() throws IOException {
        // На машине оператора берётся настоящий HEIC из cache/images/notes;
        // на свежем checkout — встроенный AVIF-фикстур.
        assumeTrue(new HeicConverter("ffmpeg").isAvailable(), "ffmpeg недоступен — скип");

        byte[] fixture = loadHeifFixture();

        // Детект формата по байтам сошёлся с форматом семейства HEIC
        assertEquals(ImageKind.HEIC, ImageUtils.detectKind(fixture));

        byte[] jpeg = new HeicConverter("ffmpeg").toJpeg(fixture);

        assertNotNull(jpeg, "Конвертер должен вернуть JPEG");
        assertTrue(jpeg.length > 0, "JPEG не должен быть пустым");
        assertEquals(0xFF, jpeg[0] & 0xFF, "Магия JPEG, байт 0");
        assertEquals(0xD8, jpeg[1] & 0xFF, "Магия JPEG, байт 1");
    }
}