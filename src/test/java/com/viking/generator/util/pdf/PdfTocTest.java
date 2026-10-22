package com.viking.generator.util.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;

class PdfTocTest {

    @Test
    @DisplayName("Строка содержания: название слева, лидер по ширине, номер страницы справа")
    void tocLineLayout() {
        PdfPTable line = PdfToc.createTocLine("Раздел 1. Общая информация.", 2);

        assertEquals(3, line.getNumberOfColumns());
        assertEquals("Раздел 1. Общая информация.", cell(line, 0, 0));
        assertEquals("2", cell(line, 0, 2));

        // Ширины считаются по тексту: лидер занимает весь промежуток,
        // колонка названия шире колонки номера (номер прижат к правому краю).
        float[] widths = line.getAbsoluteWidths();
        assertEquals(3, widths.length);
        assertTrue(widths[0] > widths[2], "колонка названия уже колонки номера");
        assertTrue(widths[1] > 0f, "лидер должен занимать ненулевую ширину");
    }

    @Test
    @DisplayName("Высота строки содержания не зависит от номера — заглушка черновика совпадает с финалом")
    void tocLineHeightIndependentOfPageNumber() {
        PdfPTable lineWithZero = PdfToc.createTocLine("Раздел 3. Заметки", 0);
        PdfPTable lineWithMorePages = PdfToc.createTocLine("Раздел 3. Заметки", 234);

        lineWithZero.calculateHeights(true);
        lineWithMorePages.calculateHeights(true);

        // Ширина колонки номера растёт с длиной числа (минимальная ширина 14 pt),
        // но строка всегда одна — высота не меняется. Это и позволяет черновику
        // рисовать содержание с номерами-заглушками без сдвига пагинации.
        assertEquals(3, lineWithZero.getAbsoluteWidths().length);
        assertEquals(lineWithMorePages.getTotalHeight(), lineWithZero.getTotalHeight(), 0.01f,
                "высота строки содержания не должна зависеть от значения номера страницы");
    }

    private static String cell(PdfPTable table, int row, int col) {
        PdfPCell[] cells = table.getRows().get(row).getCells();
        if (cells == null || col >= cells.length || cells[col] == null) return null;
        return cells[col].getPhrase() != null ? cells[col].getPhrase().getContent() : null;
    }
}