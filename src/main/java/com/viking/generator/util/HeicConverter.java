package com.viking.generator.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Конвертер HEIC/HEIF-фотографий в JPEG через внешний бинарь ffmpeg.
 * <p>
 * OpenPDF не умеет декодировать HEIC (внутри — HEVC/AV1-кадр), поэтому перед
 * встраиванием в PDF такие снимки нужно привести к JPEG. ffmpeg выбран как
 * источник декодера, так как приложение работает на одной машине оператора.
 * <p>
 * Если ffmpeg недоступен или конвертация не удалась — метод {@link #toJpeg}
 * возвращает {@code null}, а вызовы аккуратно пропускают такой снимок.
 */
public class HeicConverter {
    private static final Logger log = LoggerFactory.getLogger(HeicConverter.class);

    private static final long VERSION_CHECK_TIMEOUT_SECONDS = 5;
    private static final long CONVERSION_TIMEOUT_SECONDS = 60;

    private final String binary;

    public HeicConverter() {
        this("ffmpeg");
    }

    public HeicConverter(String binary) {
        this.binary = binary;
    }

    /**
     * Проверяет доступность ffmpeg в PATH.
     */
    public boolean isAvailable() {
        try {
            Process process = new ProcessBuilder(binary, "-version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(VERSION_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            log.debug("ffmpeg not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Конвертирует HEIC-байты в JPEG.
     *
     * @param heic исходные байты HEIC-файла
     * @return байты JPEG или {@code null}, если ffmpeg недоступен/конвертация не удалась
     */
    public byte[] toJpeg(byte[] heic) {
        if (heic == null || heic.length == 0) {
            return null;
        }
        if (!isAvailable()) {
            log.warn("HEIC-конвертация невозможна: ffmpeg не найден в PATH (binary={})", binary);
            return null;
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("heic-conv-");
            Path input = tempDir.resolve("input.heic");
            Path output = tempDir.resolve("output.jpg");
            Files.write(input, heic);

            List<String> command = List.of(binary,
                    "-y", "-v", "error",
                    "-i", input.toString(),
                    "-frames:v", "1",
                    "-q:v", "3",
                    output.toString());

            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(CONVERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("HEIC-конвертация превысила таймаут ({} с)", CONVERSION_TIMEOUT_SECONDS);
                return null;
            }
            if (process.exitValue() != 0) {
                String stderr = new String(process.getInputStream().readAllBytes()).trim();
                log.warn("HEIC-конвертация не удалась (exit={}): {}", process.exitValue(),
                        stderr.isEmpty() ? "без деталей" : stderr);
                return null;
            }
            if (!Files.exists(output)) {
                log.warn("ffmpeg завершился успешно, но не создал выходной файл");
                return null;
            }
            return Files.readAllBytes(output);
        } catch (IOException e) {
            log.warn("HEIC-конвертация: ошибка ввода-вывода: {}", e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("HEIC-конвертация прервана");
            return null;
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private void deleteRecursively(Path dir) {
        if (dir == null) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.debug("Не удалось удалить временный файл {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.debug("Не удалось удалить временную директорию {}: {}", dir, e.getMessage());
        }
    }
}