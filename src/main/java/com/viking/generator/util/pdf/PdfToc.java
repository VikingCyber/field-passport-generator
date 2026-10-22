package com.viking.generator.util.pdf;

import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.PageSize;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.draw.DottedLineSeparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Содержание PDF-паспорта: строка оглавления с пунктирным лидером и номером страницы.
 * Выделено из бывшего {@code PdfUIHelper}: здесь только содержание, остальные элементы
 * страницы — в {@link PdfTypography}, {@link PdfTables} и {@link PdfImages}.
 */
public final class PdfToc {

    private static final Logger log = LoggerFactory.getLogger(PdfToc.class);

    /** Ширина текстовой области A4: страница минус поля (левое 3 см, правое 1.5 см). */
    private static final float TOC_TEXT_WIDTH =
            PageSize.A4.getWidth() - PdfTypography.MARGIN_LEFT - PdfTypography.MARGIN_RIGHT;

    private PdfToc() {
        throw new UnsupportedOperationException("Utility class, constructors are forbidden");
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
        float titleTick = Math.max(PdfTypography.measureText(title, PdfTypography.FONT_TEXT) + 2f, 20f);
        float pageTick = Math.max(PdfTypography.measureText(pageText, PdfTypography.FONT_TEXT) + 2f, 14f);
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

        PdfPCell titleCell = new PdfPCell(new Phrase(title, PdfTypography.FONT_TEXT));
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

        PdfPCell pageCell = new PdfPCell(new Phrase(pageText, PdfTypography.FONT_TEXT));
        pageCell.setBorder(Rectangle.NO_BORDER);
        pageCell.setPadding(0f);
        pageCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        pageCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        line.addCell(titleCell);
        line.addCell(dotsCell);
        line.addCell(pageCell);
        return line;
    }
}