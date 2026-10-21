package com.viking.generator.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.viking.generator.model.FieldPassport;
import com.viking.generator.model.sections.GeneralInfo;
import com.viking.generator.model.sections.NoteSection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
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

    @Test
    @DisplayName("Колонтитулы на всех страницах: шапка-логотип, реквизиты и номер страницы справа внизу")
    void headerAndFooterOnAllPages() throws Exception {
        FieldPassport passport = passport("ПС10-01");
        PdfGeneratorService service = new PdfGeneratorService(1_000_000L, tempDir);

        service.generate(passport);

        try (PDDocument doc = Loader.loadPDF(service.resolvePassportPath(passport).toFile())) {
            int pages = doc.getNumberOfPages();
            assertTrue(pages >= 7, "Страниц должно быть не меньше 7 (содержание + 6 разделов), было: " + pages);

            for (int i = 1; i <= pages; i++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                // Сортируем по позиции (сверху вниз, слева направо):
                // самый нижний элемент страницы — номер в правом углу подвала.
                stripper.setSortByPosition(true);
                String text = stripper.getText(doc).replaceAll("\\s+", " ");

                // Реквизиты в нижнем колонтитуле — на каждой странице
                assertTrue(text.contains("ООО «СХП «Дары Малиновки»"),
                        "Страница " + i + " должна содержать реквизиты в подвале: " + text);
                // Номер страницы — последняя строка подвала (самая нижняя), прижата к правому углу
                assertTrue(text.trim().endsWith(String.valueOf(i)),
                        "Страница " + i + " должна заканчиваться своим номером в правом углу: " + text.trim());

                // Фирменная шапка — XObject-изображение на каждой странице
                PDPage pdPage = doc.getPage(i - 1);
                PDResources resources = pdPage.getResources();
                assertNotNull(resources, "У страницы " + i + " должны быть ресурсы");
                boolean hasHeaderImage = false;
                for (COSName name : resources.getXObjectNames()) {
                    if (resources.getXObject(name) instanceof PDImageXObject) {
                        hasHeaderImage = true;
                        break;
                    }
                }
                assertTrue(hasHeaderImage, "Страница " + i + " должна содержать шапку (XObject-изображение)");
            }

            // Первая страница — по-прежнему «Паспорт поля» с содержанием
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String page1 = stripper.getText(doc).replaceAll("\\s+", " ");
            assertTrue(page1.contains("Паспорт поля"), page1);
            assertTrue(page1.contains("Раздел 1. Общая информация."), page1);
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