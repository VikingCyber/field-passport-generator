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

    private void assertPngDimensions(byte[] png, int width, int height) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(image, "PNG должен корректно читаться ImageIO");
        assertArrayEquals(new int[] {width, height},
                new int[] {image.getWidth(), image.getHeight()});
    }
}
