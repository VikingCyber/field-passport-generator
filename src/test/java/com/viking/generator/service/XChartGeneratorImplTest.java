package com.viking.generator.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class XChartGeneratorImplTest {

    @Test
    @DisplayName("Резолв шрифта: несуществующий путь из конфигурации — откат на встроенный Arial")
    void resolveMissingConfigFontFallsBackToArial() {
        // В resources лежат только fonts/arial.ttf и fonts/arialbd.ttf,
        // а в конфиге может быть указан несуществующий NotoSans.
        Font font = XChartGeneratorImpl.resolveChartFont("fonts/NotoSans-Regular.ttf");

        assertNotNull(font, "Должен быть загружен запасной шрифт, а не null");
        assertTrue(font.getFamily().contains("Arial"),
                "Ожидался встроенный Arial, получили: " + font.getFamily());
    }

    @Test
    @DisplayName("Резолв шрифта: полностью пустой путь — тоже откат на встроенный Arial")
    void resolveBlankFontPathFallsBackToArial() {
        Font font = XChartGeneratorImpl.resolveChartFont("  ");

        assertNotNull(font);
        assertTrue(font.getFamily().contains("Arial"),
                "Ожидался встроенный Arial, получили: " + font.getFamily());
    }

    @Test
    @DisplayName("Резолв шрифта: существующий fonts/arial.ttf грузится напрямую")
    void resolveExistingArialLoadsDirectly() {
        Font font = XChartGeneratorImpl.resolveChartFont("fonts/arial.ttf");

        assertNotNull(font);
        assertTrue(font.getFamily().contains("Arial"),
                "Ожидался Arial, получили: " + font.getFamily());
    }

    @Test
    @DisplayName("Резолв шрифта: Arial из resources — не системный SANS_SERIF")
    void arialIsNotSystemSansSerif() {
        Font font = XChartGeneratorImpl.resolveChartFont("fonts/arial.ttf");

        assertNotNull(font);
        Font systemFallback = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        assertTrue(!font.getFamily().equalsIgnoreCase(systemFallback.getFamily()),
                "Загруженный TTF из resources не должен быть системным SANS_SERIF, получен: "
                        + font.getFamily());
    }
}
