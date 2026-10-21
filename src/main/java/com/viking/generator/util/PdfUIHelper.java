package com.viking.generator.util;

import com.viking.generator.model.common.TmcItem;
import com.viking.generator.model.media.ChartImage;
import com.viking.generator.model.media.SatelliteImage;
import com.viking.generator.model.tables.NoteTableRow;
import com.viking.generator.model.tables.OperationTableRow;
import com.viking.generator.model.tables.TechJournalTableRow;
import org.openpdf.text.*;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.*;
import org.openpdf.text.pdf.draw.DottedLineSeparator;
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

    /** Ширина текстовой области A4: страница минус поля (левое 3 см, правое 1.5 см). */
    private static final float TOC_TEXT_WIDTH = PageSize.A4.getWidth() - MARGIN_LEFT - MARGIN_RIGHT;

    // Отступы текста и заголовков
    private static final float SPACING_AFTER_TITLE = 12f;
    private static final float SPACING_AFTER_PASSPORT_TITLE = 20f;
    private static final float SPACING_AFTER_PARAGRAPH = 8f;
    private static final float SPACING_AFTER_BULLET_POINT = 4f;

    // Шрифты
    private static final Font FONT_TITLE;
    private static final Font FONT_TEXT;
    private static final Font FONT_TABLE_TITLE;
    private static final Font FONT_TABLE_BODY;
    /** Заголовок «Паспорт поля» — крупнее основного текста (14 pt, обычный). */
    private static final Font FONT_PASSPORT_TITLE;

    /** Единый плейсхолдер для отсутствующих данных во всех разделах паспорта. */
    public static final String NO_DATA = "— нет данных —";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");


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
        Font tempPassportTitle;

        try {
            BaseFont bfBold = BaseFont.createFont("fonts/NotoSans-Bold.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            BaseFont bfReg = BaseFont.createFont("fonts/NotoSans-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);

            tempTitle = new Font(bfBold, 12);
            tempText = new Font(bfReg, 12);
            tempTableTitle = new Font(bfBold, 10);
            tempTableBody = new Font(bfReg, 10);
            tempPassportTitle = new Font(bfReg, 14);

        } catch (Exception e) {
            tempTitle = new Font(Font.HELVETICA, 12, 1);
            tempText = new Font(Font.HELVETICA, 12);
            tempTableTitle = new Font(Font.HELVETICA, 10, 1);
            tempTableBody = new Font(Font.HELVETICA, 10);
            tempPassportTitle = new Font(Font.HELVETICA, 14);
            log.error("Error loading fonts: {}", e.getMessage());
        }

        FONT_TITLE = tempTitle;
        FONT_TEXT = tempText;
        FONT_TABLE_TITLE = tempTableTitle;
        FONT_TABLE_BODY = tempTableBody;
        FONT_PASSPORT_TITLE = tempPassportTitle;

    }

    private static PdfPTable createStandardTable(int colCount, float[] widths) {
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

    /**
     * Шапка паспорта: заголовок «Паспорт поля» по центру первой страницы.
     * Крупнее основного текста (14 pt), обычное начертание.
     */
    public static Paragraph createPassportTitle() {
        Paragraph p = new Paragraph("Паспорт поля", FONT_PASSPORT_TITLE);
        p.setAlignment(Element.ALIGN_CENTER);
        // Увеличенный отступ до содержания: в образце шапку отделяют два пустых абзаца.
        p.setSpacingAfter(SPACING_AFTER_PASSPORT_TITLE);
        return p;
    }

    /**
     * Строка содержания — как оглавление из образца ПС10-01 (поле Word TOC):
     * название раздела слева, пунктирный лидер, номер страницы прижат к правому
     * краю текстовой области. OpenPDF не поддерживает табуляцию с заполнителем,
     * поэтому лидер рисуется сепаратором в средней колонке, ширина которой
     * вычисляется по реальной ширине текста: точки заполняют ровно промежуток
     * между названием и номером страницы.
     */
    public static PdfPTable createTocLine(String title, int page) {
        String pageText = String.valueOf(page);
        float titleTick = Math.max(measureText(title, FONT_TEXT) + 2f, 20f);
        float pageTick = Math.max(measureText(pageText, FONT_TEXT) + 2f, 14f);
        float dotsTick = Math.max(TOC_TEXT_WIDTH - titleTick - pageTick, 10f);

        PdfPTable line = new PdfPTable(3);
        line.setLockedWidth(true);
        line.setTotalWidth(TOC_TEXT_WIDTH);
        line.setHorizontalAlignment(Element.ALIGN_LEFT);
        // Между строками содержания — увеличенный отступ (как в образце).
        line.setSpacingAfter(5f);
        try {
            // Доли ширины в процентах от общей ширины таблицы: сумма ровно 100.
            line.setWidths(new float[] {
                    titleTick / TOC_TEXT_WIDTH * 100f,
                    dotsTick / TOC_TEXT_WIDTH * 100f,
                    pageTick / TOC_TEXT_WIDTH * 100f
            });
        } catch (DocumentException e) {
            log.error("Failed to set TOC line widths: {}", e.getMessage());
        }

        PdfPCell titleCell = new PdfPCell(new Phrase(title, FONT_TEXT));
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setPadding(0f);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        DottedLineSeparator dots = new DottedLineSeparator();
        dots.setGap(4f);
        dots.setLineWidth(1f);

        PdfPCell dotsCell = new PdfPCell();
        dotsCell.setBorder(Rectangle.NO_BORDER);
        dotsCell.setPadding(0f);
        // Колонка прижата к низу: точечный лидер идёт по базовой линии текста,
        // как заполнитель Word в оглавлении образца.
        dotsCell.addElement(dots);
        dotsCell.setVerticalAlignment(Element.ALIGN_BOTTOM);

        PdfPCell pageCell = new PdfPCell(new Phrase(pageText, FONT_TEXT));
        pageCell.setBorder(Rectangle.NO_BORDER);
        pageCell.setPadding(0f);
        pageCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        pageCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        line.addCell(titleCell);
        line.addCell(dotsCell);
        line.addCell(pageCell);
        return line;
    }

    /**
     * Ширина текста в пунктах для шрифта. Если встроенный BaseFont недоступен
     * (фолбэк на системный шрифт), используется грубая оценка: в среднем половина
     * размера шрифта на символ.
     */
    private static float measureText(String text, Font font) {
        BaseFont baseFont = font.getBaseFont();
        if (baseFont != null) {
            return baseFont.getWidthPoint(text, font.getSize());
        }
        return font.getSize() * 0.5f * text.length();
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

    /** Возвращает значение или единый плейсхолдер, если значение null/пустое. */
    public static String orNoData(String value) {
        return (value == null || value.isBlank()) ? NO_DATA : value;
    }

    /** Таблица-заглушка для раздела, в котором нет данных. */
    private static PdfPTable placeholderTable() {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(10f);
        PdfPCell cell = new PdfPCell(new Phrase(NO_DATA, FONT_TEXT));
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
        PdfPCell sectionTitle = createStyledCell(new Phrase("Расход ТМЦ:", FONT_TITLE),
                null, Element.ALIGN_LEFT);
        sectionTitle.setBorder(Rectangle.NO_BORDER);
        sectionTitle.setPaddingBottom(10f);
        sectionTitle.setColspan(5);
        table.addCell(sectionTitle);

        String[] headers = {"№", "Операция/дата", "ТМЦ (наименование)", "Кол-во", "Ед. изм."};
        for (String h : headers) {
            table.addCell(createStyledCell(new Phrase(h, FONT_TABLE_TITLE), Color.LIGHT_GRAY, Element.ALIGN_CENTER));
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");
        int num = 1;
        for (OperationTableRow op : opsWithTmc) {
            String opDate = (op.start() != null) ? op.start().format(dtf) : "";
            String opLabel = op.operationName() + (opDate.isEmpty() ? "" : ", " + opDate);
            for (TmcItem item : op.tmcItemList()) {
                table.addCell(createStyledCell(new Phrase(String.valueOf(num++), FONT_TABLE_BODY), null, Element.ALIGN_CENTER));
                table.addCell(createStyledCell(new Phrase(opLabel, FONT_TABLE_BODY), null, Element.ALIGN_LEFT));
                table.addCell(createStyledCell(new Phrase(item.name() != null ? item.name() : "", FONT_TABLE_BODY), null, Element.ALIGN_LEFT));
                table.addCell(createStyledCell(
                        new Phrase(item.amount() != null ? String.format("%.2f", item.amount()) : "", FONT_TABLE_BODY),
                        null, Element.ALIGN_CENTER));
                table.addCell(createStyledCell(
                        new Phrase(item.unit() != null ? item.unit() : "", FONT_TABLE_BODY),
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
        titleCell.addElement(PdfUIHelper.createParagraph("Расход ТМЦ:"));
        container.addCell(titleCell);

        PdfPCell contentCell = new PdfPCell();
        contentCell.setBorder(Rectangle.NO_BORDER);
        contentCell.addElement(new Paragraph(NO_DATA, FONT_TEXT));
        container.addCell(contentCell);

        return container;
    }


    public static PdfPTable createNotesTable(List<NoteTableRow> notes) {
        if (notes == null || notes.isEmpty()) return placeholderTable();

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
            return placeholderTable();
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

    public static PdfPTable createTechJournalTable(List<TechJournalTableRow> techJournalTableRows) {
        if (techJournalTableRows == null || techJournalTableRows.isEmpty()) {
            return placeholderTable();
        }

        float[] columnWidths = {50f, 30f, 20f};
        PdfPTable table = createStandardTable(3, columnWidths);

        for (TechJournalTableRow row : techJournalTableRows) {
            String machineWithTool = row.getFullEquipmentName();
            table.addCell(createStyledCell(new Phrase(machineWithTool, FONT_TABLE_BODY), null, Element.ALIGN_LEFT));

            table.addCell(createStyledCell(new Phrase(row.driver(), FONT_TABLE_BODY), null, Element.ALIGN_LEFT));

            table.addCell(createStyledCell(new Phrase(row.period(), FONT_TABLE_BODY), null, Element.ALIGN_LEFT));
        }
        return table;
    }

    public static void addChartImage(Document document, ChartImage chartImage) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(20f);
        table.setKeepTogether(true);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph title = createSectionTitle("Раздел 4. Индексы");
        title.setAlignment(Element.ALIGN_LEFT);
        cell.addElement(title);

        if (chartImage == null || chartImage.getImageBytes() == null) {
            cell.addElement(new Phrase(NO_DATA, FONT_TEXT));
        } else {
            try {
                Image img = Image.getInstance(chartImage.getImageBytes());
                img.setAlignment(Image.ALIGN_CENTER);
                cell.addElement(img);
            } catch (Exception e) {
                log.error("Error rendering chartImage: {}", e.getMessage());
                cell.addElement(new Phrase("Ошибка отрисовки графика", FONT_TEXT));
            }
        }
        table.addCell(cell);
        document.add(table);
    }

    /**
     * Формирует подпись к спутниковому снимку в едином виде:
     * метка, затем «Плановая дата · Съёмка». Если снимка нет — в поле съёмки плейсхолдер.
     */
    public static String formatSatelliteCaption(SatelliteImage img) {
        String plan = img.getPlanDate() != null ? img.getPlanDate().format(DATE_FORMAT) : NO_DATA;
        String actual = img.getActualDate() != null ? img.getActualDate().format(DATE_FORMAT) : NO_DATA;
        return String.format("%s\nПлановая дата: %s · Съёмка: %s",
                orNoData(img.getDescription()), plan, actual);
    }

    /**
     * Выводит спутниковые снимки один за другим в столбик
     */
    public static void addSatelliteImagesColumn(Document document, List<SatelliteImage> images) throws DocumentException {
        for (SatelliteImage satImg : images) {
            // Создаем таблицу из одной колонки, чтобы картинка и текст не разрывались переносом страницы
            PdfPTable table = new PdfPTable(1);
            table.setWidthPercentage(100f);
            table.setSpacingBefore(20f);
            table.setKeepTogether(true); // Критично: держит картинку и подпись на одной странице

            PdfPCell cell = new PdfPCell();
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);

            try {
                Paragraph p = new Paragraph(formatSatelliteCaption(satImg), FONT_TABLE_BODY);
                p.setAlignment(Element.ALIGN_CENTER);
                p.setSpacingAfter(10f);
                cell.addElement(p);

                if (satImg.getImageBytes() != null) {
                    Image img = Image.getInstance(satImg.getImageBytes());

                    img.scaleToFit(500f, 1000f);
                    img.setAlignment(Image.ALIGN_CENTER);
                    cell.addElement(img);
                }
            } catch (Exception e) {
                log.error("Error rendering satellite image: {}", e.getMessage());
                cell.addElement(new Phrase("Ошибка отрисовки снимка", FONT_TABLE_BODY));
            }
            table.addCell(cell);
            document.add(table);
        }
    }
}
