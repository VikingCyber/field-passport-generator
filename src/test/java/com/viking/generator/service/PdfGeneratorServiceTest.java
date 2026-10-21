package com.viking.generator.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.viking.generator.model.FieldPassport;
import com.viking.generator.model.sections.GeneralInfo;
import com.viking.generator.model.sections.NoteSection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfGeneratorServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Содержание: шапка и все разделы на первой странице, нумерация без пропусков")
    void generatesTocAndSquashedSectionNumbers() throws Exception {
        FieldPassport passport = passport("ПС10-01");
        PdfGeneratorService service = new PdfGeneratorService(1_000_000L, tempDir);

        service.generate(passport);

        Path pdf = service.resolvePassportPath(passport);
        assertTrue(Files.exists(pdf), "PDF должен быть создан: " + pdf);

        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String all = stripper.getText(doc).replaceAll("\\s+", " ");

            // Нумерация ужата: Раздел 5 = спутниковые, Раздел 6 = механизаторы, нет «Раздел 7»
            assertTrue(all.contains("Раздел 5. Спутниковые снимки NDVI"), all);
            assertTrue(all.contains("Раздел 6. Работающие механизаторы"), all);
            assertFalse(all.contains("Раздел 7"), all);

            // Первая страница — шапка + содержание со всеми разделами
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String page1 = stripper.getText(doc).replaceAll("\\s+", " ");
            assertTrue(page1.contains("Паспорт поля"), page1);
            assertTrue(page1.contains("Раздел 1. Общая информация."), page1);
            assertTrue(page1.contains("Раздел 2. Выполненные работы"), page1);
            assertTrue(page1.contains("Раздел 3. Заметки"), page1);
            assertTrue(page1.contains("Раздел 4. Индексы"), page1);
            assertTrue(page1.contains("Раздел 5. Спутниковые снимки NDVI"), page1);
            assertTrue(page1.contains("Раздел 6. Работающие механизаторы"), page1);
        }
    }

    @Test
    @DisplayName("Раздел 1 начинается со второй страницы (после содержания)")
    void sectionOneStartsAfterToc() throws Exception {
        FieldPassport passport = passport("Тест-2");
        PdfGeneratorService service = new PdfGeneratorService(1_000_000L, tempDir);

        service.generate(passport);

        try (PDDocument doc = Loader.loadPDF(service.resolvePassportPath(passport).toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String page1 = stripper.getText(doc).replaceAll("\\s+", " ");
            assertFalse(page1.startsWith("Раздел 1"), page1);

            stripper.setStartPage(2);
            stripper.setEndPage(2);
            String page2 = stripper.getText(doc).replaceAll("\\s+", " ");
            assertTrue(page2.contains("Раздел 1. Общая информация."), page2);
        }
    }

    private static FieldPassport passport(String fieldName) {
        return new FieldPassport(
                "field-1",
                new GeneralInfo("Хозяйство", fieldName, 100.0, 2025, List.of()),
                List.of(),
                new NoteSection(List.of(), List.of()),
                null,
                List.of(),
                List.of());
    }
}