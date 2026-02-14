package com.viking.field_passport_generator.utils;

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


    /**
     * Закрытый конструктор, чтобы нельзя было создать экземпляр утилитарного класса
     */
    private PdfUIHelper() {
        throw new UnsupportedOperationException("Utility class, constructors are forbidden");
    }

    static {
        Font tempTitle;
        Font tempText;

        try {
            BaseFont bfBold = BaseFont.createFont("fonts/NotoSans-Bold.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            BaseFont bfReg = BaseFont.createFont("fonts/NotoSans-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);

            tempTitle = new Font(bfBold, 12);
            tempText = new Font(bfReg, 12);

        } catch (Exception e) {
            tempTitle = new Font(Font.HELVETICA, 12, 1);
            tempText = new Font(Font.HELVETICA, 12);
            System.err.println("Ошибка загрузки шрифтов: " + e.getMessage());
        }

        FONT_TITLE = tempTitle;
        FONT_TEXT = tempText;

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
    
}
