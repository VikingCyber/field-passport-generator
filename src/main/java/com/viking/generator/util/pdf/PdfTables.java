package com.viking.generator.util.pdf;

import com.viking.generator.model.common.TmcItem;
import com.viking.generator.model.tables.NoteTableRow;
import com.viking.generator.model.tables.OperationTableRow;
import com.viking.generator.model.tables.TechJournalTableRow;
import java.awt.Color;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Таблицы PDF-паспорта: работы, ТМЦ, заметки, техжурнал. Выделено из бывшего
 * {@code PdfUIHelper}: здесь только таблицы и ячейки, шрифты и отступы страницы —
 * из {@link PdfTypography}.
 */
public final class PdfTables {

    private static final Logger log = LoggerFactory.getLogger(PdfTables.class);

    private PdfTables() {
        throw new UnsupportedOperationException("Utility class, constructors are forbidden");
    }

    /** Стандартная таблица: 100% ширины, типовые отступы, заданные доли колонок. */
    static PdfPTable createStandardTable(int colCount, float[] widths) {
        PdfPTable table = new PdfPTable(colCount);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(10f);
        table.setSpacingAfter(10f);
        if (widths != null) {
            try {
                table.setWidths(widths);
            } catch (DocumentException e) {
                log.error("Failed to set table widths for {} columns. Falling back to default widths.", colCount, e);
            }
        }
        return table;
    }

    /** Ячейка с типовыми отступами, вертикальным выравниванием по середине и фоном. */
    static PdfPCell createStyledCell(Phrase phrase, Color bgColor, int align) {
        PdfPCell cell = new PdfPCell(phrase);
        if (bgColor != null) cell.setBackgroundColor(bgColor);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5f);
        cell.setLeading(12f, 0f);
        return cell;
    }

    /** Таблица-заглушка для раздела, в котором нет данных. */
    private static PdfPTable placeholderTable() {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(10f);
        PdfPCell cell = new PdfPCell(new Phrase(PdfTypography.NO_DATA, PdfTypography.FONT_TEXT));
        cell.setBorder(Rectangle.NO_BORDER);
        table.addCell(cell);
        return table;
    }

    public static PdfPTable createOperationsTable(List<OperationTableRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return placeholderTable();
        }
        float[] widths = {2.0f, 1.5f, 1.5f, 1.3f, 1.3f, 1.5f, 1.0f, 0.8f, 1.0f};
        PdfPTable table = createStandardTable(9, widths);

        String[] headers = {"Объект", "Начало", "Окончание", "По пробегу, Га", "Фактически, Га",
                "Затраты ГСМ,руб.//факт+рыночная стоимость", "Время работы", "Га/час", "Средняя скорость"};
        for (String h : headers) {
            table.addCell(createStyledCell(new Phrase(h, PdfTypography.FONT_TABLE_TITLE),
                    Color.LIGHT_GRAY, Element.ALIGN_CENTER));
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");
        for (OperationTableRow row : rows) {
            table.addCell(createStyledCell(new Phrase(row.operationName(), PdfTypography.FONT_TABLE_BODY),
                    null, Element.ALIGN_LEFT));
            table.addCell(createStyledCell(new Phrase(row.start().format(dtf), PdfTypography.FONT_TABLE_BODY),
                    null, Element.ALIGN_CENTER));
            table.addCell(createStyledCell(new Phrase(row.end().format(dtf), PdfTypography.FONT_TABLE_BODY),
                    null, Element.ALIGN_CENTER));
            table.addCell(createStyledCell(
                    new Phrase(String.format("%.2f", row.measuredArea()), PdfTypography.FONT_TABLE_BODY),
                    null, Element.ALIGN_CENTER));
            table.addCell(createStyledCell(
                    new Phrase(String.format("%.2f", row.actualArea()), PdfTypography.FONT_TABLE_BODY),
                    null, Element.ALIGN_CENTER));
            table.addCell(createStyledCell(
                    new Phrase(String.format("%.2f", row.fuelCost()), PdfTypography.FONT_TABLE_BODY),
                    null, Element.ALIGN_CENTER));
            table.addCell(createStyledCell(
                    new Phrase(PdfTypography.formatDuration(row.workDuration()), PdfTypography.FONT_TABLE_BODY),
                    null, Element.ALIGN_CENTER));
            table.addCell(createStyledCell(
                    new Phrase(String.format("%.2f", row.productivity()), PdfTypography.FONT_TABLE_BODY),
                    null, Element.ALIGN_CENTER));
            table.addCell(createStyledCell(
                    new Phrase(String.format("%.2f", row.averageSpeed()), PdfTypography.FONT_TABLE_BODY),
                    null, Element.ALIGN_CENTER));
        }
        return table;
    }

    public static PdfPTable createTmcTable(List<OperationTableRow> operations) {
        if (operations == null || operations.isEmpty()) {
            return new PdfPTable(1);
        }

        // Оставляем только операции с ТМЦ (порядок — как раньше)
        List<OperationTableRow> opsWithTmc = operations.stream()
                .filter(op -> op.hasTmcList())
                .toList().reversed();

        if (opsWithTmc.isEmpty()) {
            return new PdfPTable(1);
        }

        // Вертикальная раскладка: одна строка = один расход ТМЦ.
        // Так таблица читаема при любом количестве позиций (раньше под каждую
        // позицию отводилась колонка — у ТК14-12 выходило 29 узких колонок).
        float[] columnWidths = {5f, 24f, 41f, 15f, 15f};
        PdfPTable table = createStandardTable(5, columnWidths);
        // Заголовок секции — часть этой же таблицы: keepTogether гарантирует,
        // что «Расход ТМЦ:» и сама таблица не разъедутся по страницам.
        table.setKeepTogether(true);

        // Заголовок — как в «Разделе 7»: шрифт заголовка секции, без рамки поверх,
        // но вшит в таблицу, чтобы не отрывался от данных между страницами.
        PdfPCell sectionTitle = createStyledCell(new Phrase("Расход ТМЦ:", PdfTypography.FONT_TITLE),
                null, Element.ALIGN_LEFT);
        sectionTitle.setBorder(Rectangle.NO_BORDER);
        sectionTitle.setPaddingBottom(10f);
        sectionTitle.setColspan(5);
        table.addCell(sectionTitle);

        String[] headers = {"№", "Операция/дата", "ТМЦ (наименование)", "Кол-во", "Ед. изм."};
        for (String h : headers) {
            table.addCell(createStyledCell(new Phrase(h, PdfTypography.FONT_TABLE_TITLE),
                    Color.LIGHT_GRAY, Element.ALIGN_CENTER));
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");
        int num = 1;
        for (OperationTableRow op : opsWithTmc) {
            String opDate = (op.start() != null) ? op.start().format(dtf) : "";
            String opLabel = op.operationName() + (opDate.isEmpty() ? "" : ", " + opDate);
            for (TmcItem item : op.tmcItemList()) {
                table.addCell(createStyledCell(new Phrase(String.valueOf(num++), PdfTypography.FONT_TABLE_BODY),
                        null, Element.ALIGN_CENTER));
                table.addCell(createStyledCell(new Phrase(opLabel, PdfTypography.FONT_TABLE_BODY),
                        null, Element.ALIGN_LEFT));
                table.addCell(createStyledCell(
                        new Phrase(item.name() != null ? item.name() : "", PdfTypography.FONT_TABLE_BODY),
                        null, Element.ALIGN_LEFT));
                table.addCell(createStyledCell(
                        new Phrase(item.amount() != null ? String.format("%.2f", item.amount())
                                : "", PdfTypography.FONT_TABLE_BODY),
                        null, Element.ALIGN_CENTER));
                table.addCell(createStyledCell(
                        new Phrase(item.unit() != null ? item.unit() : "", PdfTypography.FONT_TABLE_BODY),
                        null, Element.ALIGN_CENTER));
            }
        }

        return table;
    }

    public static PdfPTable createTmcContainer(List<OperationTableRow> rows) {

        // При наличии расходов отдаём единую таблицу, в которую вшит заголовок
        // «Расход ТМЦ:» — она с keepTogether, поэтому заголовок и таблица
        // не разъезжаются по разным страницам.
        if (rows != null && rows.stream().anyMatch(OperationTableRow::hasTmcList)) {
            return createTmcTable(rows);
        }

        // Без расходов — компактный блок с плейсхолдером
        PdfPTable container = new PdfPTable(1);
        container.setHorizontalAlignment(Element.ALIGN_LEFT);
        container.setKeepTogether(true);

        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.addElement(PdfTypography.createParagraph("Расход ТМЦ:"));
        container.addCell(titleCell);

        PdfPCell contentCell = new PdfPCell();
        contentCell.setBorder(Rectangle.NO_BORDER);
        contentCell.addElement(createNoDataParagraph());
        container.addCell(contentCell);

        return container;
    }

    /** Абзац-плейсхолдер для пустых таблиц-контейнеров. */
    private static Paragraph createNoDataParagraph() {
        return new Paragraph(PdfTypography.NO_DATA, PdfTypography.FONT_TEXT);
    }

    public static PdfPTable createNotesTable(List<NoteTableRow> notes) {
        if (notes == null || notes.isEmpty()) return placeholderTable();

        float[] columnWidths = {5f, 20f, 45f, 15f, 20f};
        PdfPTable table = createStandardTable(5, columnWidths);

        String[] headers = {"№", "Заметки", "Текст", "Изменено", "Дата создания"};
        for (String h : headers) {
            table.addCell(createStyledCell(new Phrase(h, PdfTypography.FONT_TABLE_TITLE),
                    Color.LIGHT_GRAY, Element.ALIGN_CENTER));
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

        for (NoteTableRow note : notes) {
            table.addCell(createStyledCell(new Phrase(String.valueOf(note.index()),
                    PdfTypography.FONT_TABLE_BODY), null, Element.ALIGN_CENTER));

            String title = note.title() != null ? note.title() : "-";
            table.addCell(createStyledCell(new Phrase(title, PdfTypography.FONT_TABLE_BODY),
                    null, Element.ALIGN_LEFT));

            String text = (note.text() != null && !note.text().isBlank()) ? note.text() : "";
            table.addCell(createStyledCell(new Phrase(text, PdfTypography.FONT_TABLE_BODY),
                    null, Element.ALIGN_LEFT));

            String updated = (note.updatedAt() != null) ? note.updatedAt().format(dtf) : "";
            table.addCell(createStyledCell(new Phrase(updated, PdfTypography.FONT_TABLE_BODY),
                    null, Element.ALIGN_CENTER));

            String created = (note.createdAt() != null) ? note.createdAt().format(dtf) : "";
            table.addCell(createStyledCell(new Phrase(created, PdfTypography.FONT_TABLE_BODY),
                    null, Element.ALIGN_CENTER));
        }

        return table;
    }

    public static PdfPTable createTechJournalTable(List<TechJournalTableRow> techJournalTableRows) {
        if (techJournalTableRows == null || techJournalTableRows.isEmpty()) {
            return placeholderTable();
        }

        float[] columnWidths = {50f, 30f, 20f};
        PdfPTable table = createStandardTable(3, columnWidths);

        for (TechJournalTableRow row : techJournalTableRows) {
            String machineWithTool = row.getFullEquipmentName();
            table.addCell(createStyledCell(new Phrase(machineWithTool, PdfTypography.FONT_TABLE_BODY),
                    null, Element.ALIGN_LEFT));

            table.addCell(createStyledCell(new Phrase(row.driver(), PdfTypography.FONT_TABLE_BODY),
                    null, Element.ALIGN_LEFT));

            table.addCell(createStyledCell(new Phrase(row.period(), PdfTypography.FONT_TABLE_BODY),
                    null, Element.ALIGN_LEFT));
        }
        return table;
    }
}