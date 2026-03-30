package com.viking.field_passport_generator.util;

import com.viking.field_passport_generator.model.NoteTableRow;
import com.viking.field_passport_generator.model.OperationTableRow;
import com.viking.field_passport_generator.model.TmcItem;
import org.openpdf.text.*;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;


/**
 * Утилитарный класс для унификации оформления PDF-паспорта поля.
 * Реализует стандарты отступов и централизованное управление шрифтами.
 */
public final class PdfUIHelper {

    private static final Logger log = LoggerFactory.getLogger(PdfUIHelper.class);
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

    private static PdfPTable createStandardTable(int colCount, float[] widths) {
        PdfPTable table = new PdfPTable(colCount);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(10f);
        table.setSpacingAfter(10f);
        table.setKeepTogether(true);
        if (widths != null) {
            try {
                table.setWidths(widths);
            } catch (DocumentException e) {
                log.error("Failed to set table widths for {} columns. Falling back to default widths.", colCount, e);
            }
        }
        return table;
    }

    private static PdfPCell createStyledCell(Phrase phrase, Color bgColor, int align) {
        PdfPCell cell = new PdfPCell(phrase);
        if (bgColor != null) cell.setBackgroundColor(bgColor);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5f);
        cell.setLeading(12f, 0f);
        return cell;
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
        float[] widths = {2.0f, 1.5f, 1.5f, 1.3f, 1.3f, 1.5f, 1.0f, 0.8f, 1.0f};
        PdfPTable table = createStandardTable(9, widths);

        String[] headers = {"Объект", "Начало", "Окончание", "По пробегу, Га", "Фактически, Га",
                "Затраты ГСМ,руб.//факт+рыночная стоимость", "Время работы", "Га/час", "Средняя скорость"};
        for (String h : headers) {
            table.addCell(createStyledCell(new Phrase(h, FONT_TABLE_TITLE), Color.LIGHT_GRAY, Element.ALIGN_CENTER));
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");
        for (OperationTableRow row : rows) {
            table.addCell(createStyledCell(new Phrase(row.operationName(), FONT_TABLE_BODY), null, Element.ALIGN_LEFT));
            table.addCell(createStyledCell(new Phrase(row.start().format(dtf), FONT_TABLE_BODY), null, Element.ALIGN_CENTER));
            table.addCell(createStyledCell(new Phrase(row.end().format(dtf), FONT_TABLE_BODY), null, Element.ALIGN_CENTER));
            table.addCell(createStyledCell(new Phrase(String.format("%.2f", row.measuredArea()), FONT_TABLE_BODY), null, Element.ALIGN_CENTER));
            table.addCell(createStyledCell(new Phrase(String.format("%.2f", row.actualArea()), FONT_TABLE_BODY), null, Element.ALIGN_CENTER));
            table.addCell(createStyledCell(new Phrase(String.format("%.2f", row.fuelCost()), FONT_TABLE_BODY), null, Element.ALIGN_CENTER));
            table.addCell(createStyledCell(new Phrase(formatDuration(row.workDuration()), FONT_TABLE_BODY), null, Element.ALIGN_CENTER));
            table.addCell(createStyledCell(new Phrase(String.format("%.2f", row.productivity()), FONT_TABLE_BODY), null, Element.ALIGN_CENTER));
            table.addCell(createStyledCell(new Phrase(String.format("%.2f", row.averageSpeed()), FONT_TABLE_BODY), null, Element.ALIGN_CENTER));
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

        Map<Long, List<String>> tmcValues = new LinkedHashMap<>();
        for (OperationTableRow op : opsWithTmc) {
            for (TmcItem item : op.tmcItemList()) {
                tmcValues.computeIfAbsent(item.id(), k -> new ArrayList<>())
                        .add(item.formatForPassport());
            }
        }
        tmcValues.values().forEach(Collections::reverse);

        // 3. Определяем количество колонок и строк
        int columnCount = tmcValues.size();
        int maxRows = tmcValues.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);

        PdfPTable table = createStandardTable(columnCount, null);
        int colNum = 1;
        for (Long id : tmcValues.keySet()) {
            table.addCell(createStyledCell(
                    new Phrase("ТМЦ №" + colNum++ + "/расход", FONT_TABLE_TITLE),
                    Color.LIGHT_GRAY,
                    Element.ALIGN_CENTER));
        }

        for (int row = 0; row < maxRows; row++) {
            for (List<String> values : tmcValues.values()) {
                String text = row < values.size() ? values.get(row) : "";
                table.addCell(createStyledCell(new Phrase(text, FONT_TABLE_BODY), null, Element.ALIGN_CENTER));
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

        boolean hasTmc = rows != null && rows.stream().anyMatch(OperationTableRow::hasTmcList);


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


    public static PdfPTable createNotesTable(List<NoteTableRow> notes) {
        if (notes == null || notes.isEmpty()) return new PdfPTable(1);

        float[] columnWidths = {5f, 20f, 45f, 15f, 20f};
        PdfPTable table = createStandardTable(5, columnWidths);

        String[] headers = {"№", "Заметки", "Текст", "Изменено", "Дата создания"};
        for (String h : headers) {
            table.addCell(createStyledCell(new Phrase(h, FONT_TABLE_TITLE), Color.LIGHT_GRAY, Element.ALIGN_CENTER));
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

        for (NoteTableRow note : notes) {
            table.addCell(createStyledCell(new Phrase(String.valueOf(note.index()), FONT_TABLE_BODY), null, Element.ALIGN_CENTER));

            String title = note.title() != null ? note.title() : "-";
            table.addCell(createStyledCell(new Phrase(title, FONT_TABLE_BODY), null, Element.ALIGN_LEFT));

            String text = (note.text() != null && !note.text().isBlank()) ? note.text() : "";
            table.addCell(createStyledCell(new Phrase(text, FONT_TABLE_BODY), null, Element.ALIGN_LEFT));

            String updated = (note.updatedAt() != null) ? note.updatedAt().format(dtf) : "";
            table.addCell(createStyledCell(new Phrase(updated, FONT_TABLE_BODY), null, Element.ALIGN_CENTER));

            String created = (note.createdAt() != null) ? note.createdAt().format(dtf) : "";
            table.addCell(createStyledCell(new Phrase(created, FONT_TABLE_BODY), null, Element.ALIGN_CENTER));
        }

        return table;
    }

    public static PdfPCell createPhotoBlock(PdfWriter writer, byte[] imageBytes, String complexId) {
        PdfPCell cell = new PdfPCell();
        float size = 230f;
        cell.setFixedHeight(size);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(1f);

        try {
            if (imageBytes != null && imageBytes.length > 0) {
                Image img = Image.getInstance(imageBytes);

                float width = img.getWidth();
                float height = img.getHeight();
                float scale = Math.max(size / width, size / height);

                PdfContentByte cb = writer.getDirectContent();
                PdfTemplate template = cb.createTemplate(size, size);

                float newWidth = width * scale;
                float newHeight = height * scale;
                float offX = (size - newWidth) / 2;
                float offY = (size - newHeight) / 2;

                template.addImage(img, newWidth, 0, 0, newHeight, offX, offY);

                // --- ЭТАП 4: Накладываем текст ID с белым ореолом (Интеграция) ---
                template.beginText();
                template.setFontAndSize(FONT_TABLE_TITLE.getBaseFont(), 10f);

                float x = 5f;          // Базовая позиция X
                float y = size - 15f;  // Базовая позиция Y

                // 1. Рисуем белый "ореол" (сдвиг на 0.5 пункта в 4 стороны)
                template.setColorFill(Color.WHITE);
                template.setTextMatrix(x - 0.5f, y);
                template.showText(complexId);
                template.setTextMatrix(x + 0.5f, y);
                template.showText(complexId);
                template.setTextMatrix(x, y - 0.5f);
                template.showText(complexId);
                template.setTextMatrix(x, y + 0.5f);
                template.showText(complexId);

                // 2. Рисуем основной черный текст поверх ореола
                template.setColorFill(Color.BLACK);
                template.setTextMatrix(x, y);
                template.showText(complexId);

                template.endText();

                Image finalImg = Image.getInstance(template);
                cell.addElement(finalImg);
            }
        } catch (Exception e) {
            log.error("Crop/Watermark error {}: {}", complexId, e.getMessage());
            cell.addElement(new Phrase("Ошибка фото " + complexId));
        }
        return cell;
    }

    public static PdfPTable createPhotoGrid(PdfWriter writer, Map<String, byte[]> photos) {
        if (photos == null || photos.isEmpty()) {
            return new PdfPTable(1);
        }

        PdfPTable grid = new PdfPTable(3);
        grid.setWidthPercentage(100f);
        grid.setSpacingBefore(10f);

        grid.setKeepTogether(false);
        grid.setSplitRows(true);

        PdfPCell titleCell = new PdfPCell(PdfUIHelper.createParagraph("3.2 Фотографии"));
        titleCell.setColspan(3);
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setPaddingBottom(10f);
        grid.addCell(titleCell);

        for (Map.Entry<String, byte[]> entry : photos.entrySet()) {
            grid.addCell(createPhotoBlock(writer, entry.getValue(), entry.getKey()));
        }

        int remainder = photos.size() % 3;
        if (remainder != 0) {
            for (int i = 0; i < (3 - remainder); i++) {
                PdfPCell emptyCell = new PdfPCell(new Phrase(""));
                emptyCell.setBorder(Rectangle.NO_BORDER);
                grid.addCell(emptyCell);
            }
        }
        return grid;
    }
}
