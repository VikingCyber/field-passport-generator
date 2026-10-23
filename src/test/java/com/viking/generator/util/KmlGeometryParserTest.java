package com.viking.generator.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.viking.generator.model.geometry.FieldGeometry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KmlGeometryParserTest {

    private static final String KML_FIXTURE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document id="Геозоны АгроСигнал">
                <Folder id="Агрокопула">
                  <name>Агрокопула</name>
                  <Placemark>
                    <name>Участок-тест</name>
                    <MultiGeometry>
                      <Polygon id="P1">
                        <extrude>1</extrude>
                        <outerBoundaryIs>
                          <LinearRing id="LR1">
                            <coordinates>
                              93.300000,56.400000,0.0 93.304000,56.400000,0.0
                              93.304000,56.402200,0.0 93.300000,56.402200,0.0 93.300000,56.400000,0.0
                            </coordinates>
                          </LinearRing>
                        </outerBoundaryIs>
                        <innerBoundaryIs>
                          <LinearRing id="LR2">
                            <coordinates>
                              93.301000,56.400800,0.0 93.302000,56.400800,0.0
                              93.302000,56.401400,0.0 93.301000,56.401400,0.0 93.301000,56.400800,0.0
                            </coordinates>
                          </LinearRing>
                        </innerBoundaryIs>
                      </Polygon>
                    </MultiGeometry>
                  </Placemark>
                </Folder>
              </Document>
            </kml>
            """;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Разбор KML из строки: имя, два кольца, нетто-площадь (внешняя минус дыра)")
    void shouldParseInlineKml() throws Exception {
        KmlGeometryParser parser = new KmlGeometryParser();

        FieldGeometry geometry = parser.parse(KML_FIXTURE).orElseThrow();

        assertEquals("Участок-тест", geometry.name());
        assertNotNull(geometry.rings());
        assertEquals(2, geometry.rings().size(), "Должно быть 2 кольца: внешнее + дыра");

        double areaHa = geometry.netAreaHa();
        assertTrue(areaHa > 5.0 && areaHa < 15.0,
                "Площадь участка ≈ 9 га, получено: " + areaHa);

        // Кольца в проекции Web Mercator: центр участка должен лежать рядом с
        // ожидаемым меркатор-центром (поля ~93.3°, 56.4°).
        double[] merc = KmlGeometryParser.lonLatToMercator(93.302, 56.4011);
        assertTrue(merc[0] > 1.0e7 && merc[1] > 7.0e6,
                "Меркатор-координаты выглядят нереалистично: " + java.util.Arrays.toString(merc));
        assertTrue(geometry.rings().get(0)[0][0] > 1.0e7,
                "Первый пикет внешнего кольца должен быть в Web Mercator X ~ 1.0e7");
    }

    @Test
    @DisplayName("Разбор реального KML поля ПС10-01: 35 колец, нетто-площадь ≈ 467.5 га")
    void shouldParseRealFieldKmlFromResources() throws Exception {
        KmlGeometryParser parser = new KmlGeometryParser();
        Path kml = extractFixture();

        FieldGeometry geometry = parser.parse(kml).orElseThrow();

        assertEquals("ПС10-01", geometry.name());
        assertEquals(35, geometry.rings().size(),
                "Реальная геометрия: 1 внешний контур + 34 дыры");

        assertTrue(geometry.netAreaHa() > 440.0 && geometry.netAreaHa() < 490.0,
                "Нетто-площадь ПС10-01 ≈ 467.5 га, получено: " + geometry.netAreaHa());

        // Центр поля в Web Mercator (из эталонной геометрии).
        double[] merc = KmlGeometryParser.lonLatToMercator(93.26, 56.404);
        double expectedX = 10381800.0;
        double expectedY = 7639147.0;
        assertTrue(Math.abs(merc[0] - expectedX) < 200_000,
                "X центра поля должен быть близок к " + expectedX);
        assertTrue(Math.abs(merc[1] - expectedY) < 200_000,
                "Y центра поля должен быть близок к " + expectedY);
    }

    @Test
    @DisplayName("Пустой и несуществующий файл дают пустой Optional")
    void shouldReturnEmptyForMissingFile() throws Exception {
        KmlGeometryParser parser = new KmlGeometryParser();
        assertEquals(Optional.empty(), parser.parse(tempDir.resolve("nope.kml")));
        assertEquals(Optional.empty(), parser.parse("  "));
    }

    @Test
    @DisplayName("Параллельный разбор из множества потоков: общий парсер потокобезопасен")
    void shouldParseConcurrently() throws Exception {
        KmlGeometryParser parser = new KmlGeometryParser();
        Path kml = extractFixture();

        // Запускаем разбор одного и того же файла одновременно из 8 потоков:
        // общий DocumentBuilder в поле экземпляра вызывал гонку XERCES
        // (FWK005 parse may not be called while parsing), из-за чего в батче
        // снимки многих полей (в т.ч. МН02) не рендерились.
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<FieldGeometry>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                tasks.add(() -> parser.parse(kml).orElseThrow());
            }
            List<Future<FieldGeometry>> futures = new ArrayList<>();
            tasks.forEach(task -> futures.add(pool.submit(task)));

            for (Future<FieldGeometry> future : futures) {
                FieldGeometry geometry = future.get();
                assertEquals("ПС10-01", geometry.name());
                assertEquals(35, geometry.rings().size());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** Копирует реальный KML ПС10-01 (id 394851) из test/resources во временный каталог. */
    private Path extractFixture() throws IOException {
        Path target = tempDir.resolve("field_394851.kml");
        try (InputStream is = getClass().getResourceAsStream("/geometry/field_394851.kml")) {
            assertNotNull(is, "Тестовый KML должен лежать в src/test/resources/geometry/");
            Files.copy(is, target);
        }
        assertTrue(Files.size(target) > 30_000, "Реальный KML должен весить > 30 КБ");
        return target;
    }
}
