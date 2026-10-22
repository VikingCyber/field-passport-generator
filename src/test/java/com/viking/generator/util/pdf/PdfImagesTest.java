package com.viking.generator.util.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.viking.generator.model.common.SpectralIndex;
import com.viking.generator.model.media.SatelliteImage;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PdfImagesTest {

    @Test
    @DisplayName("Подпись снимка: метка, плановая и фактическая дата в формате dd.MM.yyyy")
    void satelliteCaptionWithActualDate() {
        SatelliteImage img = new SatelliteImage(
                "1", LocalDate.of(2026, 5, 18), "3 дня после СЗР", SpectralIndex.NDVI);
        img.setActualDate(LocalDate.of(2026, 5, 20));

        assertEquals("3 дня после СЗР\nПлановая дата: 18.05.2026 · Съёмка: 20.05.2026",
                PdfImages.formatSatelliteCaption(img));
    }

    @Test
    @DisplayName("Подпись снимка: при отсутствии съёмки в поле даты — единый плейсхолдер")
    void satelliteCaptionWithoutActualDateUsesPlaceholder() {
        SatelliteImage img = new SatelliteImage(
                "1", LocalDate.of(2026, 5, 18), "3 дня после СЗР", SpectralIndex.NDVI);

        assertEquals("3 дня после СЗР\nПлановая дата: 18.05.2026 · Съёмка: " + PdfTypography.NO_DATA,
                PdfImages.formatSatelliteCaption(img));
    }
}