package com.viking.generator.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.viking.generator.model.common.SpectralIndex;
import com.viking.generator.model.common.TmcItem;
import com.viking.generator.model.media.SatelliteImage;
import com.viking.generator.model.tables.OperationTableRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.openpdf.text.Element;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

class PdfUIHelperTest {

    @Test
    @DisplayName("Подпись снимка: метка, плановая и фактическая дата в формате dd.MM.yyyy")
    void satelliteCaptionWithActualDate() {
        SatelliteImage img = new SatelliteImage(
                "1", LocalDate.of(2026, 5, 18), "3 дня после СЗР", SpectralIndex.NDVI);
        img.setActualDate(LocalDate.of(2026, 5, 20));

        assertEquals("3 дня после СЗР\nПлановая дата: 18.05.2026 · Съёмка: 20.05.2026",
                PdfUIHelper.formatSatelliteCaption(img));
    }

    @Test
    @DisplayName("Подпись снимка: при отсутствии съёмки в поле даты — единый плейсхолдер")
    void satelliteCaptionWithoutActualDateUsesPlaceholder() {
        SatelliteImage img = new SatelliteImage(
                "1", LocalDate.of(2026, 5, 18), "3 дня после СЗР", SpectralIndex.NDVI);

        assertEquals("3 дня после СЗР\nПлановая дата: 18.05.2026 · Съёмка: " + PdfUIHelper.NO_DATA,
                PdfUIHelper.formatSatelliteCaption(img));
    }

    @Test
    @DisplayName("Шапка паспорта: «Паспорт поля» по центру, обычный шрифт текста")
    void passportTitleCentered() {
        Paragraph p = PdfUIHelper.createPassportTitle();

        assertEquals("Паспорт поля", p.getContent());
        assertEquals(Element.ALIGN_CENTER, p.getAlignment());
    }

    @Test
    @DisplayName("Строка содержания: название слева, лидер по ширине, номер страницы справа")
    void tocLineLayout() {
        PdfPTable line = PdfUIHelper.createTocLine("Раздел 1. Общая информация.", 2);

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
    @DisplayName("orNoData: пустое или пробельное значение заменяется единым плейсхолдером")
    void orNoDataReplacesBlank() {
        assertEquals(PdfUIHelper.NO_DATA, PdfUIHelper.orNoData(null));
        assertEquals(PdfUIHelper.NO_DATA, PdfUIHelper.orNoData("   "));
        assertEquals("Пшеница", PdfUIHelper.orNoData("Пшеница"));
    }

    @Test
    @DisplayName("Таблица ТМЦ: 5 колонок, одна строка на каждый расход, с операцией и датой")
    void tmcTableRowsPerConsumption() {
        OperationTableRow opProtect = operation(
                "Защита растений", LocalDateTime.of(2026, 5, 18, 10, 0),
                new TmcItem(1L, "Гербицид", 2.5, "л"),
                new TmcItem(2L, "Прилипатель", 0.5, "л"));
        OperationTableRow opFertilizer = operation(
                "Внесение удобрений", LocalDateTime.of(2026, 5, 10, 8, 0),
                new TmcItem(3L, "Аммиачная селитра", 100.0, "кг"));
        OperationTableRow opNoTmc = operation("Вспашка", LocalDateTime.of(2026, 5, 1, 7, 0));

        PdfPTable table = PdfUIHelper.createTmcTable(
                List.of(opProtect, opFertilizer, opNoTmc));

        // 5 колонок: № | Операция/дата | ТМЦ (наименование) | Кол-во | Ед. изм.
        assertEquals(5, table.getNumberOfColumns());
        // Заголовок секции + шапка + 3 расхода (операция без ТМЦ отфильтрована)
        assertEquals(5, table.getRows().size());

        // Заголовок секции вшит в таблицу — не должен отрываться от данных
        assertEquals("Расход ТМЦ:", cell(table, 0, 0));

        // Шапка колонок
        assertEquals("№", cell(table, 1, 0));
        assertEquals("Операция/дата", cell(table, 1, 1));
        assertEquals("ТМЦ (наименование)", cell(table, 1, 2));
        assertEquals("Кол-во", cell(table, 1, 3));
        assertEquals("Ед. изм.", cell(table, 1, 4));

        // Строка 1 — опять операции идут в обратном порядке, как и раньше
        assertEquals("1", cell(table, 2, 0));
        assertEquals("Внесение удобрений, 10.05.26 08:00", cell(table, 2, 1));
        assertEquals("Аммиачная селитра", cell(table, 2, 2));
        assertEquals(fmt(100.0), cell(table, 2, 3));
        assertEquals("кг", cell(table, 2, 4));

        // Строки 2-3 — оба ТМЦ операции «Защита растений»
        assertEquals("2", cell(table, 3, 0));
        assertEquals("Защита растений, 18.05.26 10:00", cell(table, 3, 1));
        assertEquals("Гербицид", cell(table, 3, 2));
        assertEquals(fmt(2.5), cell(table, 3, 3));
        assertEquals("л", cell(table, 3, 4));

        assertEquals("3", cell(table, 4, 0));
        assertEquals("Прилипатель", cell(table, 4, 2));
        assertEquals(fmt(0.5), cell(table, 4, 3));
        assertEquals("л", cell(table, 4, 4));
    }

    @Test
    @DisplayName("Таблица ТМЦ: количество и единица измерения допускают null без падения")
    void tmcTableHandlesNullAmountAndUnit() {
        OperationTableRow op = operation(
                "Протравливание", LocalDateTime.of(2026, 4, 2, 9, 30),
                new TmcItem(9L, "Протравитель", null, null));

        PdfPTable table = PdfUIHelper.createTmcTable(List.of(op));

        assertEquals(5, table.getNumberOfColumns());
        assertEquals(3, table.getRows().size()); // заголовок + шапка + 1 расход
        assertEquals("1", cell(table, 2, 0));
        assertEquals("Протравитель", cell(table, 2, 2)); // наименование на месте
        assertEquals("", cell(table, 2, 3)); // кол-во пустое (amount = null)
        assertEquals("", cell(table, 2, 4)); // единица пустая (unit = null)
    }

    @Test
    @DisplayName("Таблица ТМЦ: пустой список или список без ТМЦ — таблица-заглушка на 1 колонку")
    void tmcTableEmptyReturnsPlaceholder() {
        assertEquals(1, PdfUIHelper.createTmcTable(null).getNumberOfColumns());
        assertEquals(1, PdfUIHelper.createTmcTable(List.of()).getNumberOfColumns());
        assertEquals(1, PdfUIHelper.createTmcTable(
                List.of(operation("Вспашка", LocalDateTime.of(2026, 5, 1, 7, 0))))
                .getNumberOfColumns());
    }

    private static OperationTableRow operation(String name, LocalDateTime start, TmcItem... tmcs) {
        return new OperationTableRow(
                name, start, start.plusHours(2),
                10.0, 10.0, 0.0, Duration.ofHours(2), 5.0, 10.0,
                tmcs.length == 0 ? List.of() : List.of(tmcs));
    }

    private static String cell(PdfPTable table, int row, int col) {
        PdfPCell[] cells = table.getRows().get(row).getCells();
        if (cells == null || col >= cells.length || cells[col] == null) return null;
        return cells[col].getPhrase() != null ? cells[col].getPhrase().getContent() : null;
    }

    /** Формат количества — тот же, что в таблице (зависит от локали). */
    private static String fmt(double value) {
        return String.format("%.2f", value);
    }
}