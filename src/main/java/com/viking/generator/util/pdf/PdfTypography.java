package com.viking.generator.util.pdf;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.BaseFont;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Типографика PDF-паспорта: шрифты, поля страницы, отступы и примитивы текста.
 * <p>
 * Выделено из бывшего {@code PdfUIHelper}: здесь только оформление и форматтеры,
 * без таблиц, изображений и содержания (это {@link PdfTables}, {@link PdfImages},
 * {@link PdfToc}).
 */
public final class PdfTypography {

    private static final Logger log = LoggerFactory.getLogger(PdfTypography.class);

    // Переводим см в пункты (1 см = 28.35f)
    private static final float CM_TO_PT = 28.35f;

    // Отступы страницы
    public static final float MARGIN_LEFT = 3.0f * CM_TO_PT;
    public static final float MARGIN_RIGHT = 1.5f * CM_TO_PT;
    public static final float MARGIN_TOP = 2.0f * CM_TO_PT;
    public static final float MARGIN_BOTTOM = 2.0f * CM_TO_PT;
    /**
     * Верхнее поле повёрнутых (альбомных) страниц. Шапка на всю ширину листа (842 pt
     * при пропорциях 1240×118) имеет высоту ~80 pt — это больше портретного поля (56.7 pt),
     * поэтому под альбомные листы поле увеличивается, чтобы шапка не перекрывала контент,
     * а цветочки шапки уходили в правый угол листа. Применяется к следующей странице
     * через {@link Document#setMargins} перед переходом в альбомную ориентацию.
     */
    public static final float LANDSCAPE_MARGIN_TOP = 3.2f * CM_TO_PT;
    private static final float FIRST_LINE_INDENT = 0.75f * CM_TO_PT;

    // Отступы текста и заголовков
    private static final float SPACING_AFTER_TITLE = 12f;
    private static final float SPACING_AFTER_PASSPORT_TITLE = 20f;
    private static final float SPACING_AFTER_PARAGRAPH = 8f;
    private static final float SPACING_AFTER_BULLET_POINT = 4f;

    // Шрифты — видны соседям по пакету (PdfTables/PdfImages/PdfToc)
    static final Font FONT_TITLE;
    static final Font FONT_TEXT;
    static final Font FONT_TABLE_TITLE;
    static final Font FONT_TABLE_BODY;
    /** Заголовок «Паспорт поля» — крупнее основного текста (14 pt, обычный). */
    static final Font FONT_PASSPORT_TITLE;

    /** Единый плейсхолдер для отсутствующих данных во всех разделах паспорта. */
    public static final String NO_DATA = "— нет данных —";

    static {
        Font tempTitle;
        Font tempText;
        Font tempTableTitle;
        Font tempTableBody;
        Font tempPassportTitle;

        try {
            BaseFont bfBold = BaseFont.createFont("fonts/arialbd.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            BaseFont bfReg = BaseFont.createFont("fonts/arial.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);

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

    private PdfTypography() {
        throw new UnsupportedOperationException("Utility class, constructors are forbidden");
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

    public static Paragraph createBulletPoint(String text) {
        Paragraph p = new Paragraph("• " + text, FONT_TEXT);
        p.setIndentationLeft(FIRST_LINE_INDENT);
        p.setSpacingAfter(SPACING_AFTER_BULLET_POINT);
        return p;
    }

    /**
     * Строка раздела «1.4. Севооборот»: подпись с двоеточием и значением без маркера,
     * с тем же отступом, что у маркированного списка. Пусть и отсутствующие данные
     * подписываются: «Культура: — нет данных —» — видно, чего именно не хватает.
     */
    public static Paragraph createRotationItem(String label, String value) {
        Paragraph p = new Paragraph(label + ": " + orNoData(value), FONT_TEXT);
        p.setIndentationLeft(FIRST_LINE_INDENT);
        p.setSpacingAfter(SPACING_AFTER_BULLET_POINT);
        return p;
    }

    public static Document createDocument() {
        return new Document(PageSize.A4, MARGIN_LEFT, MARGIN_RIGHT, MARGIN_TOP, MARGIN_BOTTOM);
    }

    /**
     * BaseFont обычного текстового шрифта (Arial) — для колонтитулов.
     * Может вернуть {@code null}, если встроенный шрифт не загрузился
     * (фолбэк на системный Helvetica); вызывающий сам решает про запасной вариант.
     */
    public static BaseFont textBaseFont() {
        return FONT_TEXT.getBaseFont();
    }

    /**
     * Ширина текста в пунктах для шрифта. Если встроенный BaseFont недоступен
     * (фолбэк на системный шрифт), используется грубая оценка: в среднем половина
     * размера шрифта на символ. Нужен PdfToc для расчёта лидера содержания.
     */
    static float measureText(String text, Font font) {
        BaseFont baseFont = font.getBaseFont();
        if (baseFont != null) {
            return baseFont.getWidthPoint(text, font.getSize());
        }
        return font.getSize() * 0.5f * text.length();
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

    /**
     * Возвращает значение или единый плейсхолдер, если значение null/пустое/прочерк.
     * Прочерки ("-", "–", "—") приходят из API как маркер отсутствия данных — они
     * тоже заменяются плейсхолдером, чтобы в отчёте было видно «чего именно нет».
     */
    public static String orNoData(String value) {
        if (value == null || value.isBlank()) {
            return NO_DATA;
        }
        String trimmed = value.trim();
        if (trimmed.equals("-") || trimmed.equals("–") || trimmed.equals("—")) {
            return NO_DATA;
        }
        return trimmed;
    }
}