package com.viking.generator.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.viking.generator.util.ImageUtils.ImageKind;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Проверяет сжатие изображений перед встраиванием в PDF.
 */
class ImageOptimizerTest {

    @Test
    @DisplayName("Большой JPEG уменьшается по разрешению и пережимается в меньший JPEG")
    void compressLargeJpeg() throws Exception {
        byte[] original = jpeg(4000, 3000);

        byte[] compressed = ImageOptimizer.compress(original, 1600, 0.8f);

        assertNotNull(compressed);
        assertTrue(compressed.length < original.length,
                "Сжатый файл должен быть меньше исходного: %d >= %d"
                        .formatted(compressed.length, original.length));
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(compressed));
        assertEquals(1600, out.getWidth(), "Ширина уменьшена до лимита");
        assertEquals(1200, out.getHeight(), "Пропорции 4:3 сохранены");
        assertEquals(ImageKind.JPEG, ImageUtils.detectKind(compressed));
    }

    @Test
    @DisplayName("RGBA PNG с прозрачностью схлопывается на белый фон в непрозрачный JPEG")
    void compressRgbaPngFlattensToRgb() throws Exception {
        byte[] original = transparentPng(2000, 1200);

        byte[] compressed = ImageOptimizer.compress(original, 1600, 0.8f);

        assertNotNull(compressed);
        assertTrue(compressed.length < original.length);
        assertEquals(ImageKind.JPEG, ImageUtils.detectKind(compressed));
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(compressed));
        assertTrue(!out.getColorModel().hasAlpha(),
                "Альфа-канал должен быть удалён (JPEG не поддерживает прозрачность)");
    }

    @Test
    @DisplayName("Компактный JPEG в пределах лимита возвращается как есть без пережатия")
    void smallJpegLeftUntouched() throws Exception {
        byte[] original = jpeg(800, 600);

        assertSame(original, ImageOptimizer.compress(original, 1600, 0.8f),
                "Уже компактный JPEG не должен пережиматься повторно");
    }

    @Test
    @DisplayName("Некорректные байты возвращаются как есть (фолбэк)")
    void brokenBytesFallback() {
        byte[] broken = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        byte[] result = ImageOptimizer.compress(broken, 1600, 0.8f);

        assertSame(broken, result, "Битые данные должны вернуться без изменений");
    }

    @Test
    @DisplayName("Неизвестный формат (WebP) не декодируется и возвращается как есть")
    void unsupportedFormatFallback() {
        // Сигнатура WebP: "RIFF" + ... + "WEBP"
        byte[] webp = {0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00,
                0x57, 0x45, 0x42, 0x50, 0x56, 0x50, 0x38};

        assertSame(webp, ImageOptimizer.compress(webp, 1600, 0.8f));
    }

    @Test
    @DisplayName("Малая PNG без прозрачности конвертируется в JPEG, если это уменьшает размер")
    void smallPngReencodedToJpeg() throws Exception {
        // Шумная 300x300 PNG занимает больше компактного JPEG.
        BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setPaint(new GradientPaint(0, 0, Color.WHITE, 300, 300, Color.BLACK));
            g.fillRect(0, 0, 300, 300);
        } finally {
            g.dispose();
        }
        // Рандомный шум делает PNG «дороже» компактного JPEG.
        for (int i = 0; i < 15000; i++) {
            int x = (int) (Math.random() * 300);
            int y = (int) (Math.random() * 300);
            image.setRGB(x, y, 0xFFFFFF & (int) (Math.random() * 0xFFFFFF));
        }
        byte[] png = toBytes(image, "png");

        byte[] jpeg = ImageOptimizer.compress(png, 1600, 0.8f);

        assertEquals(ImageKind.JPEG, ImageUtils.detectKind(jpeg));
        assertTrue(jpeg.length <= png.length, "JPEG не должен быть больше исходного PNG");
    }

    @Test
    @DisplayName("Null и пустой массив проходят насквозь")
    void nullAndEmpty() {
        assertSame(null, ImageOptimizer.compress(null, 1600, 0.8f));
        byte[] empty = new byte[0];
        assertSame(empty, ImageOptimizer.compress(empty, 1600, 0.8f));
    }

    @Test
    @DisplayName("Хелперы фото и снимков используют свои лимиты")
    void helpersUseOwnLimits() throws Exception {
        byte[] original = jpeg(4000, 3000);

        byte[] photo = ImageOptimizer.forPhoto(original);
        byte[] satellite = ImageOptimizer.forSatellite(original);

        BufferedImage photoImg = ImageIO.read(new ByteArrayInputStream(photo));
        BufferedImage satImg = ImageIO.read(new ByteArrayInputStream(satellite));
        assertEquals(ImageOptimizer.PHOTO_MAX_DIM_PX, photoImg.getWidth());
        assertEquals(ImageOptimizer.SATELLITE_MAX_DIM_PX, satImg.getWidth());
    }

    private static byte[] jpeg(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setPaint(new GradientPaint(0, 0, Color.WHITE, width, height, Color.BLUE));
            g.fillRect(0, 0, width, height);
            // Добавляем «сложный» шум, чтобы JPEG получился весомым.
            for (int i = 0; i < 2000; i++) {
                int x = (int) (Math.random() * width);
                int y = (int) (Math.random() * height);
                image.setRGB(x, y, (int) (Math.random() * 0xFFFFFF));
            }
        } finally {
            g.dispose();
        }
        return toBytes(image, "jpg");
    }

    private static byte[] transparentPng(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            // Полупрозрачный градиент поверх полностью прозрачного фона.
            g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, 0.5f));
            g.setPaint(new GradientPaint(0, 0, Color.RED, width, height, Color.GREEN));
            g.fillOval(0, 0, width, height);
        } finally {
            g.dispose();
        }
        return toBytes(image, "png");
    }

    private static byte[] toBytes(BufferedImage image, String format) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean written = ImageIO.write(image, format, baos);
        assertTrue(written, "Должен быть записан формат " + format);
        return baos.toByteArray();
    }
}
