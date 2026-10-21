package com.viking.generator.util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.viking.generator.util.ImageUtils.ImageKind;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Оптимизирует растровые изображения перед встраиванием в PDF: уменьшает
 * разрешение до нужного максимума и пережимает в JPEG. Многомегабайтные
 * оригиналы из кэша (фото до 15 МБ, спутниковые PNG до 4 МБ) раздувают
 * PDF и долго рисуются в браузере — здесь они приводятся к размеру,
 * достаточному для печати/просмотра.
 * <p>
 * Чистая функция {@code byte[] -> byte[]}: вызывается в момент рендера,
 * кэш на диске не трогает. Безопасный фолбэк: если исходник не удалось
 * декодировать (неожиданный формат) или пережатие дало больший размер — 
 * возвращаются исходные байты.
 * <p>
 * HEIC обрабатывается отдельно (см. {@link HeicConverter}) и сюда не доходит.
 */
public final class ImageOptimizer {

    private static final Logger log = LoggerFactory.getLogger(ImageOptimizer.class);

    /** Фото заметок: в PDF выводятся сеткой 3 колонки, ~230 pt на ячейку. */
    public static final int PHOTO_MAX_DIM_PX = 1400;
    /** Спутниковые снимки NDVI: до ширины текстового поля (~200 мм). */
    public static final int SATELLITE_MAX_DIM_PX = 1600;
    /** Качество JPEG после пережатия (0..1). */
    public static final float JPEG_QUALITY = 0.8f;

    private ImageOptimizer() {
        throw new UnsupportedOperationException("Utility class, constructors are forbidden");
    }

    /** Оптимизация фото из заметок. */
    public static byte[] forPhoto(byte[] original) {
        return compress(original, PHOTO_MAX_DIM_PX, JPEG_QUALITY);
    }

    /** Оптимизация спутниковых снимков NDVI. */
    public static byte[] forSatellite(byte[] original) {
        return compress(original, SATELLITE_MAX_DIM_PX, JPEG_QUALITY);
    }

    /**
     * Уменьшает разрешение до {@code maxDimPx} по большей стороне и пережимает
     * в JPEG c качеством {@code quality}. Уже компактный JPEG, помещающийся в
     * лимит, возвращается как есть (без двойного пережатия, портящего качество).
     */
    public static byte[] compress(byte[] original, int maxDimPx, float quality) {
        if (original == null || original.length == 0) {
            return original;
        }

        ImageKind kind = ImageUtils.detectKind(original);
        if (kind != ImageKind.JPEG && kind != ImageKind.PNG
                && kind != ImageKind.GIF && kind != ImageKind.BMP) {
            // Неизвестный/недешифруемый формат (WebP, PDF и т.п.) — отдаём как есть,
            // чтобы не потерять картинку: выше по стеку уже есть фолбэки.
            return original;
        }

        BufferedImage source = read(original);
        if (source == null) {
            return original;
        }

        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= 0 || height <= 0) {
            return original;
        }

        boolean fits = width <= maxDimPx && height <= maxDimPx;
        // Компактный JPEG в лимите не трогаем — двойное сжатие только портит качество.
        if (fits && kind == ImageKind.JPEG) {
            return original;
        }

        double scale = Math.min(1.0, (double) maxDimPx / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage resized = targetWidth == width && targetHeight == height
                ? toRgbWithoutAlpha(source)
                : scaleToRgb(source, targetWidth, targetHeight);

        byte[] jpeg = writeJpeg(resized, quality);
        if (jpeg == null) {
            return original;
        }
        // Не отдаём результат, если он не меньше исходника (некорректный фолбэк).
        if (jpeg.length >= original.length) {
            return original;
        }

        log.debug("Сжато изображение {}x{} -> {}x{} ({} байт -> {} байт)",
                width, height, targetWidth, targetHeight, original.length, jpeg.length);
        return jpeg;
    }

    private static BufferedImage read(byte[] data) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            return ImageIO.read(in);
        } catch (IOException e) {
            log.warn("Не удалось декодировать изображение: {}", e.getMessage());
            return null;
        }
    }

    /** Приводит к непрозрачному RGB: альфа-канал рисуется поверх белого фона. */
    private static BufferedImage toRgbWithoutAlpha(BufferedImage source) {
        BufferedImage rgb = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    /**
     * Масштабирует с сохранением пропорций и сразу превращает в RGB.
     * Двухступенчатое уменьшение (вдвое за шаг) даёт лучшее качество
     * и производительность, чем один большой сдвиг.
     */
    private static BufferedImage scaleToRgb(BufferedImage source, int targetWidth,
            int targetHeight) {
        int currentWidth = source.getWidth();
        int currentHeight = source.getHeight();

        Image intermediate = source;
        while (currentWidth / 2 >= targetWidth && currentHeight / 2 >= targetHeight) {
            currentWidth /= 2;
            currentHeight /= 2;
            intermediate = intermediate.getScaledInstance(
                    currentWidth, currentHeight, Image.SCALE_SMOOTH);
        }

        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, targetWidth, targetHeight);
            g.drawImage(intermediate, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }

    private static byte[] writeJpeg(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            return null;
        }
        ImageWriter writer = null;
        ImageOutputStream out = null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            writer = writers.next();
            out = ImageIO.createImageOutputStream(baos);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
            out.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            log.warn("Не удалось пережать изображение в JPEG: {}", e.getMessage());
            return null;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // Пренебрегаем ошибкой закрытия потока.
                }
            }
            if (writer != null) {
                writer.dispose();
            }
        }
    }
}
