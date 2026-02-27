package com.viking.field_passport_generator.utils;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;


import com.viking.field_passport_generator.models.OperationTableRow;
import com.viking.field_passport_generator.models.TmcItem;
import org.openpdf.text.*;
import org.openpdf.text.Font;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;


/**
 * Утилитарный класс для унификации оформления PDF-паспорта поля.
 * Реализует стандарты отступов и централизованное управление шрифтами.
 */
public final class PdfUIHelper {
    
    // Переводим см в пункты (1 см = 28.35f)
    private static final float CM_TO_PT = 28.35f;
    
    // Отступы страницы
    private static final float MARGIN_LEFT = 3.0f * CM_TO_PT;
    private static final float MARGIN_RIGHT = 1.5f * CM_TO_PT;
    private static final float MARGIN_TOP = 2.0f * CM_TO_PT;
    private static final float MARGIN_BOTTOM = 2.0f * CM_TO_PT;
    private static final float FIRST_LINE_INDENT = 0.75f * CM_TO_PT;

    // Отступы текста и заголовков
    private static final float SPACING_AFTER_TITLE = 12f;
    private static final float SPACING_AFTER_PARAGRAPH = 8f;
    private static final float SPACING_AFTER_BULLET_POINT = 4f;

    // Шрифты
    private static final Font FONT_TITLE;
    private static final Font FONT_TEXT;
    private static final Font FONT_TABLE_TITLE;
    private static final Font FONT_TABLE_BODY;


    /**
     * Закрытый конструктор, чтобы нельзя было создать экземпляр утилитарного класса
     */
    private PdfUIHelper() {
        throw new UnsupportedOperationException("Utility class, constructors are forbidden");
    }

    static {
        Font tempTitle;
        Font tempText;
        Font tempTableTitle;
        Font tempTableBody;

        try {
            BaseFont bfBold = BaseFont.createFont("fonts/NotoSans-Bold.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            BaseFont bfReg = BaseFont.createFont("fonts/NotoSans-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);

            tempTitle = new Font(bfBold, 12);
            tempText = new Font(bfReg, 12);
            tempTableTitle = new Font(bfBold, 10);
            tempTableBody = new Font(bfReg, 10);

        } catch (Exception e) {
            tempTitle = new Font(Font.HELVETICA, 12, 1);
            tempText = new Font(Font.HELVETICA, 12);
            tempTableTitle = new Font(Font.HELVETICA, 10, 1);
            tempTableBody = new Font(Font.HELVETICA, 10);
            System.err.println("Ошибка загрузки шрифтов: " + e.getMessage());
        }

        FONT_TITLE = tempTitle;
        FONT_TEXT = tempText;
        FONT_TABLE_TITLE = tempTableTitle;
        FONT_TABLE_BODY = tempTableBody;

    }

    public static Paragraph createParagraph(String text) {
        Paragraph p = new Paragraph(text, FONT_TEXT);
        p.setSpacingAfter(SPACING_AFTER_PARAGRAPH);
        p.setAlignment(Element.ALIGN_LEFT);
        return p;
    }

    public static Paragraph createSectionTitle(String text) {
        Paragraph p = new Paragraph(text, FONT_TITLE);
        p.setSpacingAfter(SPACING_AFTER_TITLE);
        return p;
    }

    public static Paragraph createBulletPoint(String text) {
        Paragraph p = new Paragraph("• " + text, FONT_TEXT);
        p.setIndentationLeft(FIRST_LINE_INDENT);
        p.setSpacingAfter(SPACING_AFTER_BULLET_POINT);
        return p;
    }

    public static Document createDocument() {
        return new Document(PageSize.A4, MARGIN_LEFT, MARGIN_RIGHT, MARGIN_TOP, MARGIN_BOTTOM);
    }

    public static String formatArea(double area) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setDecimalSeparator('.');
        DecimalFormat df = new DecimalFormat("0.##", symbols);
        return df.format(area);
    }

    public static String formatDuration(Duration duration) {
        Objects.requireNonNull(duration);

        if (duration.isNegative()) {
            throw new IllegalArgumentException("Отрицательные длительности не поддерживаются: " + duration);

        }

        return String.format("%d:%02d:%02d",
            duration.toHours(),
            duration.toMinutesPart(),
            duration.toSecondsPart()
        );
    }

    public static PdfPTable createOperationsTable(List<OperationTableRow> rows) {
        PdfPTable table = new PdfPTable(9);
        table.getDefaultCell().setHorizontalAlignment(Element.ALIGN_CENTER);
        table.getDefaultCell().setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.getDefaultCell().setPadding(5f);
        table.getDefaultCell().setLeading(12f, 0f);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(10f);
        table.setSpacingAfter(10f);

        float[] columnWidths = {
            2.0f,  // Объект (шире, т.к. текст длинный)
            1.5f,  // Начало
            1.5f,  // Окончание
            1.3f,  // По пробегу, Га
            1.3f,  // Фактически, Га
            1.5f,  // Затраты ГСМ (самая широкая колонка!)
            1.0f,  // Время работы
            0.8f,  // Га/час
            1.0f   // Средняя скорость
        };

        try {
            table.setWidths(columnWidths);
        } catch (DocumentException e) {
            e.printStackTrace();
        }

        String[] headers = {"Объект", "Начало", "Окончание", "По пробегу, Га", "Фактически, Га",
                "Затраты ГСМ,руб.//факт+рыночная стоимость", "Время работы", "Га/час", "Средняя скорость"};
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, FONT_TABLE_TITLE));
            cell.setBackgroundColor(Color.LIGHT_GRAY);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setLeading(12f, 0f);
            table.addCell(cell);
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");
        for (OperationTableRow row : rows) {
            table.addCell(new Phrase(row.operationName(), FONT_TABLE_BODY));
            table.addCell(new Phrase(row.start().format(dtf), FONT_TABLE_BODY));
            table.addCell(new Phrase(row.end().format(dtf), FONT_TABLE_BODY));
            table.addCell(new Phrase(String.format("%.2f", row.measuredArea()), FONT_TABLE_BODY));
            table.addCell(new Phrase(String.format("%.2f", row.actualArea()), FONT_TABLE_BODY));
            table.addCell(new Phrase(String.format("%.2f", row.fuelCost()), FONT_TABLE_BODY));
            table.addCell(new Phrase(formatDuration(row.workDuration()), FONT_TABLE_BODY));
            table.addCell(new Phrase(String.format("%.2f", row.productivity()), FONT_TABLE_BODY));
            table.addCell(new Phrase(String.format("%.2f", row.averageSpeed()), FONT_TABLE_BODY));
        }

        return table;
    }

    public static PdfPTable createTmcTable(List<OperationTableRow> operations) {
        if (operations == null || operations.isEmpty()) {
            return new PdfPTable(1);
        }

        // 1. Оставляем только операции с ТМЦ
        List<OperationTableRow> opsWithTmc = operations.stream()
                .filter(op -> op.tmcItemList() != null && !op.tmcItemList().isEmpty())
                .toList().reversed();

        if (opsWithTmc.isEmpty()) {
            return new PdfPTable(1);
        }

        // 2. Собираем все ТМЦ в мапу ID → список значений (в порядке появления)
        Map<Long, List<String>> tmcValues = new LinkedHashMap<>();

        for (OperationTableRow op : opsWithTmc) {
            for (TmcItem item : op.tmcItemList()) {
                tmcValues.computeIfAbsent(item.id(), k -> new ArrayList<>())
                        .add(item.formatForPassport());
            }
        }

        for (List<String> values : tmcValues.values()) {
            Collections.reverse(values);
        }

        // 3. Определяем количество колонок и строк
        int columnCount = tmcValues.size();
        int maxRows = tmcValues.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);

        // 4. Создаём таблицу
        PdfPTable table = new PdfPTable(columnCount);
        table.setKeepTogether(true);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(10f);
        table.setSpacingAfter(10f);

        // 5. Заголовки колонок
        int colNum = 1;
        for (Long id : tmcValues.keySet()) {
            PdfPCell header = new PdfPCell(new Phrase("ТМЦ №" + colNum++ + "/расход", FONT_TABLE_TITLE));
            header.setHorizontalAlignment(Element.ALIGN_CENTER);
            header.setBackgroundColor(Color.LIGHT_GRAY);
            header.setPadding(5f);
            table.addCell(header);
        }

        // 6. Заполняем строки
        for (int row = 0; row < maxRows; row++) {
            for (List<String> values : tmcValues.values()) {
                String text = row < values.size() ? values.get(row) : "";
                PdfPCell cell = new PdfPCell(new Phrase(text, FONT_TABLE_BODY));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(5f);
                table.addCell(cell);
            }
        }

        return table;
    }

    public static PdfPTable createTmcContainer(List<OperationTableRow> rows) {

        // Контейнер с заголовком
        PdfPTable container = new PdfPTable(1);
        container.setHorizontalAlignment(Element.ALIGN_LEFT);
        container.setKeepTogether(true);

        // Заголовок
        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.addElement(PdfUIHelper.createParagraph("Расход ТМЦ:"));
        container.addCell(titleCell);

        boolean hasTmc = rows != null && rows.stream()
                .flatMap(op -> op.tmcItemList() == null ? Stream.empty() : op.tmcItemList().stream())
                .anyMatch(item -> true);


        PdfPCell contentCell = new PdfPCell();
        contentCell.setBorder(Rectangle.NO_BORDER);

        if (hasTmc) {
            contentCell.addElement(createTmcTable(rows));
        } else {
            Paragraph noDataMsg = new Paragraph("Нет данных о расходе ТМЦ", FONT_TEXT);
            contentCell.addElement(noDataMsg);
        }
        container.addCell(contentCell);

        return container;
    }

}
