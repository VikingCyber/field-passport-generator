package com.viking.generator.http.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.viking.generator.config.record.AgroApiConfig;
import com.viking.generator.http.InternalHttpClient;
import com.viking.generator.model.geometry.FieldGeometry;
import com.viking.generator.model.media.FieldMapImage;
import com.viking.generator.service.FieldMapRenderer;
import com.viking.generator.service.KmlSyncService;
import com.viking.generator.util.KmlGeometryParser;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/**
 * Офлайн-тесты стратегии «снимок поля с карты»: KML заранее подкладывается в kmlDir
 * (свежий — сервер не вызывается), тайлы подложки отдаёт fake-фетчер без сети.
 */
class FieldMapStrategyTest {

    private static final String KML_BODY = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <Placemark>
                  <name>ПС10-01</name>
                  <Polygon>
                    <outerBoundaryIs>
                      <LinearRing>
                        <coordinates>
                          93.250000,56.400000,0.0 93.260000,56.400000,0.0
                          93.260000,56.410000,0.0 93.250000,56.410000,0.0 93.250000,56.400000,0.0
                        </coordinates>
                      </LinearRing>
                    </outerBoundaryIs>
                  </Polygon>
                </Placemark>
              </Document>
            </kml>
            """;

    /** Тайл подложки 256×256 — один на все запросы. */
    private static byte[] tilePng;

    /** Парсер общий для всех тестов (класс без состояния). */
    private final KmlGeometryParser parser = new KmlGeometryParser();

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setUpTiles() throws IOException {
        BufferedImage tile = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                tile.setRGB(x, y, new Color(240, 240, 240).getRGB());
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(tile, "png", baos);
        tilePng = baos.toByteArray();
    }

    /** Считающий фетчер: всегда отдаёт подложку, замеряет число вызовов. */
    private static final class CountingTileFetcher implements FieldMapRenderer.TileFetcher {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public byte[] fetch(int z, int x, int y) {
            calls.incrementAndGet();
            return tilePng;
        }
    }

    private FieldMapStrategy newStrategy(CountingTileFetcher fetcher, Path kmlDir, Path cacheDir) {
        InternalHttpClient client = Mockito.mock(InternalHttpClient.class);
        KmlSyncService kmlSync = new KmlSyncService(
                client, new AgroApiConfig("k", "http://test.local", "T", 0L, 1000L,
                        null, null, null, null, null, null), kmlDir);
        FieldMapRenderer renderer = new FieldMapRenderer(fetcher);
        return new FieldMapStrategy(kmlSync, parser, renderer, cacheDir, "maps", "png");
    }

    private void writeFreshKml(Path kmlDir) throws IOException {
        Files.createDirectories(kmlDir);
        Files.writeString(kmlDir.resolve("394851.kml"), KML_BODY);
    }

    @Test
    @DisplayName("process наполняет imageBytes и пишет PNG в кэш cacheBaseDir/<id>/maps/")
    void shouldRenderAndCacheSnapshot() throws Exception {
        CountingTileFetcher fetcher = new CountingTileFetcher();
        Path kmlDir = tempDir.resolve("kml");
        writeFreshKml(kmlDir);
        Path cacheDir = tempDir.resolve("cache");
        FieldMapStrategy strategy = newStrategy(fetcher, kmlDir, cacheDir);

        FieldMapImage image = new FieldMapImage("394851", "ПС10-01");
        strategy.process(image);

        assertNotNull(image.getImageBytes(), "imageBytes должны заполниться PNG");
        assertTrue(image.getImageBytes().length > 0);
        assertTrue(fetcher.calls.get() > 0, "Рендер должен загружать тайлы подложки");

        Path cached = cacheDir.resolve("394851").resolve("maps")
                .resolve("fieldmap_394851.png");
        assertTrue(Files.exists(cached), "PNG должен сохраниться в кэш: " + cached);
        assertTrue(Files.size(cached) > 0);

        BufferedImage png = ImageIO.read(new ByteArrayInputStream(image.getImageBytes()));
        assertNotNull(png);
        assertEquals(1600, png.getWidth());
        assertEquals(767, png.getHeight());
    }

    @Test
    @DisplayName("Без force повторный process не перерисовывает и не трогает диск")
    void shouldReuseImageBytesAndCacheWhenNotForced() throws Exception {
        CountingTileFetcher fetcher = new CountingTileFetcher();
        Path kmlDir = tempDir.resolve("kml");
        writeFreshKml(kmlDir);
        Path cacheDir = tempDir.resolve("cache");
        FieldMapStrategy strategy = newStrategy(fetcher, kmlDir, cacheDir);

        FieldMapImage image = new FieldMapImage("394851", "ПС10-01");
        strategy.process(image);
        int callsAfterFirst = fetcher.calls.get();
        Path cached = cacheDir.resolve("394851").resolve("maps")
                .resolve("fieldmap_394851.png");

        strategy.process(image);

        assertEquals(callsAfterFirst, fetcher.calls.get(),
                "Повторный process без force не должен перерисовывать");
        assertTrue(Files.exists(cached), "Файл в кэше должен остаться");
    }

    @Test
    @DisplayName("force=true выполняет новый рендер (новые запросы тайлов)")
    void shouldForceRegenerationWhenForced() throws Exception {
        CountingTileFetcher fetcher = new CountingTileFetcher();
        Path kmlDir = tempDir.resolve("kml");
        writeFreshKml(kmlDir);
        Path cacheDir = tempDir.resolve("cache");
        FieldMapStrategy strategy = newStrategy(fetcher, kmlDir, cacheDir);

        FieldMapImage image = new FieldMapImage("394851", "ПС10-01");
        strategy.process(image);
        int callsAfterFirst = fetcher.calls.get();

        strategy.process(image, true);

        assertTrue(fetcher.calls.get() > callsAfterFirst,
                "force=true должен выполнить новый рендер (новые запросы тайлов)");
    }

    @Test
    @DisplayName("Без KML на диске imageBytes остаются пустыми (без исключений)")
    void shouldLeaveImageEmptyWhenKmlMissing() {
        CountingTileFetcher fetcher = new CountingTileFetcher();
        Path cacheDir = tempDir.resolve("cache");
        FieldMapStrategy strategy =
                newStrategy(fetcher, tempDir.resolve("empty-kml"), cacheDir);

        FieldMapImage image = new FieldMapImage("999999", "Нет поля");
        strategy.process(image);

        assertNull(image.getImageBytes(), "Без KML снимок не должен появиться");
        assertEquals(0, fetcher.calls.get(), "Рендер без геометрии запускаться не должен");
        assertFalse(Files.exists(cacheDir), "Пустой кэш не должен создаваться");
    }

    @Test
    @DisplayName("Тестовый KML разбирается в геометрию с одним кольцом")
    void shouldParseFixtureGeometry() {
        FieldGeometry geometry = parser.parse(KML_BODY).orElseThrow();
        assertEquals("ПС10-01", geometry.name());
        assertEquals(1, geometry.rings().size());
        assertTrue(geometry.netAreaHa() > 0,
                "Площадь тестового участка должна быть положительной");
    }
}