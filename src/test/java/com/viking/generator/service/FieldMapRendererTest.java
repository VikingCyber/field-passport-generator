package com.viking.generator.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FieldMapRendererTest {

    /** Один закешированный PNG-тайл подложки 256×256 (светло-серый). */
    private static byte[] tilePng;

    @BeforeAll
    static void setUpTiles() throws IOException {
        BufferedImage tile = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                tile.setRGB(x, y, new Color(235, 235, 240).getRGB());
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(tile, "png", baos);
        tilePng = baos.toByteArray();
    }

    /** Тайл-фетчер без сети: всегда отдаёт подложку. */
    private FieldMapRenderer.TileFetcher solidTiles() {
        return (z, x, y) -> tilePng;
    }

    @Test
    @DisplayName("Рендер маленького контура с тайлами-подложкой возвращает PNG 1600×767")
    void shouldRenderFieldSnapshotPng() throws Exception {
        FieldMapRenderer renderer = new FieldMapRenderer(solidTiles());

        // Квадрат ~200×200 м вокруг центра ПС10-01 в Web Mercator.
        double cx = 10381800.0;
        double cy = 7639147.0;
        double[] c0 = {cx - 100, cy - 100};
        double[] c1 = {cx + 100, cy - 100};
        double[] c2 = {cx + 100, cy + 100};
        double[] c3 = {cx - 100, cy + 100};
        List<double[][]> rings = List.<double[][]>of(new double[][] {c0, c1, c2, c3, c0});

        byte[] png = renderer.renderPng(rings, "Тестовое поле");

        assertNotNull(png);
        assertTrue(png.length > 0, "PNG должен быть непустым");
        assertPngDimensions(png, 1600, 767);
    }

    @Test
    @DisplayName("Рендер отключает сеть: тайлы только из фетчера")
    void shouldUseInjectedTileFetcherOnly() throws Exception {
        FieldMapRenderer renderer = new FieldMapRenderer(solidTiles());
        double cx = 10381800.0;
        double cy = 7639147.0;
        List<double[][]> rings = List.<double[][]>of(new double[][] {
                {cx, cy}, {cx + 50, cy}, {cx + 50, cy + 50}, {cx, cy + 50}, {cx, cy}
        });
        byte[] png = renderer.renderPng(rings, "Поле");
        assertPngDimensions(png, 1600, 767);
    }

    @Test
    @DisplayName("Нулевая или пустая геометрия — IOException, а не краш")
    void shouldThrowOnEmptyGeometry() {
        FieldMapRenderer renderer = new FieldMapRenderer(solidTiles());
        org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> renderer.renderPng(List.of(), ""));
    }

    @Test
    @DisplayName("Вытянутое поле: контур не обрезается по длинной оси кадра")
    void shouldNotCropElongatedField() throws Exception {
        FieldMapRenderer renderer = new FieldMapRenderer(solidTiles());

        // Поле 4000×50 м в Web Mercator — сильно вытянуто по горизонтали.
        // Раньше зум выбирался так, что шаг оказывался меньше допустимого:
        // охват кадра (1600 px * res) становился < 4000 м, и контур вылезал
        // за границы снимка. Теперь зум берётся с запасом — контур внутри.
        double cx = 10381800.0;
        double cy = 7639147.0;
        double hw = 2000.0;
        double hh = 25.0;
        List<double[][]> rings = List.<double[][]>of(new double[][] {
                {cx - hw, cy - hh}, {cx + hw, cy - hh},
                {cx + hw, cy + hh}, {cx - hw, cy + hh}, {cx - hw, cy - hh}
        });

        byte[] png = renderer.renderPng(rings, "Вытянутое поле");

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(image, "PNG должен корректно читаться ImageIO");
        assertPngDimensions(png, 1600, 767);

        // Контур не должен касаться ни одной границы кадра: проверяем периметр
        // (крайняя колонка слева/справа и крайняя строка сверху/снизу).
        boolean contourAtEdge = false;
        for (int y = 0; y < image.getHeight(); y++) {
            if (isContourColor(image, 0, y) || isContourColor(image, image.getWidth() - 1, y)) {
                contourAtEdge = true;
            }
        }
        for (int x = 0; x < image.getWidth(); x++) {
            if (isContourColor(image, x, 0) || isContourColor(image, x, image.getHeight() - 1)) {
                contourAtEdge = true;
            }
        }
        org.junit.jupiter.api.Assertions.assertFalse(contourAtEdge,
                "Контур вытянутого поля не должен касаться границ кадра");
    }

    /** Красноватые пиксели контура (обводка поля на снимке). */
    private boolean isContourColor(BufferedImage image, int x, int y) {
        Color c = new Color(image.getRGB(x, y));
        int r = c.getRed();
        int g = c.getGreen();
        int b = c.getBlue();
        // Контур (198,40,40) и (120,15,15): красный доминирует, зелёный и синий малы.
        return r > 100 && g < 90 && b < 90;
    }

    private void assertPngDimensions(byte[] png, int width, int height) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(image, "PNG должен корректно читаться ImageIO");
        assertArrayEquals(new int[] {width, height},
                new int[] {image.getWidth(), image.getHeight()});
    }
}
