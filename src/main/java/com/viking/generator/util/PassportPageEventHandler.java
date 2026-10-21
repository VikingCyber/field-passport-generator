package com.viking.generator.util;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Image;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * Рисует колонтитулы на каждой странице PDF — по образцу
 * «26ХХХХХ Письмо(БланкЧистыйЛист).docx»:
 * <ul>
 *   <li>верхний колонтитул — фирменная шапка-логотип ({@code images/letterhead.jpg});</li>
 *   <li>нижний колонтитул — 3 строки реквизитов по центру (Arial 8 pt) и номер страницы,
 *       прижатый к правому углу (поле PAGE в образце отсутствует — нумерация добавлена).</li>
 * </ul>
 * Колонтитулы действуют на всех страницах, включая первую (в образце «отдельная первая
 * страница» нет), и адаптируются к повёрнутым (альбомным) страницам: координаты считаются
 * от текущего размера страницы {@link PdfWriter#getPageSize()}.
 */
public class PassportPageEventHandler extends PdfPageEventHelper {

    private static final Logger log = LoggerFactory.getLogger(PassportPageEventHandler.class);

    /** Реквизиты организации — нижний колонтитул образца (Arial 8 pt, по центру). */
    private static final String[] FOOTER_LINES = {
            "ООО «СХП «Дары Малиновки»",
            "660118, Красноярский край, г. Красноярск, ш. Северное, д.17, пом.7.",
            "+7 (391) 205-11-22 / e-mail: dm@darym.ru / ОГРН 1132411001128 / ИНН / КПП 2435006330 / 246601001"
    };

    /** Размер шрифта реквизитов, как в образце: 8 pt. */
    private static final float FOOTER_FONT_SIZE = 8f;
    /** Межстрочный интервал строк нижнего колонтитула. */
    private static final float FOOTER_LEADING = 10f;
    /** Отступ нижней строки колонтитула от нижнего края страницы. */
    private static final float FOOTER_BOTTOM_OFFSET = 18f;

    /** Фирменная шапка из classpath; {@code null}, если ресурс не загрузился. */
    private Image headerImage;

    public PassportPageEventHandler() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("images/letterhead.jpg")) {
            if (is == null) {
                log.warn("Не найден ресурс images/letterhead.jpg — верхний колонтитул (шапка) не будет нарисован");
                return;
            }
            headerImage = Image.getInstance(is.readAllBytes());
        } catch (Exception e) {
            log.error("Не удалось загрузить шапку images/letterhead.jpg: {}", e.getMessage());
        }
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        drawHeader(writer);
        drawFooter(writer);
    }

    /**
     * Фирменная шапка сверху страницы: вписывается в ширину текстовой области,
     * но не выше верхнего поля, чтобы не наползать на содержимое (на портретных
     * страницах получается в полную ширину, на альбомных ограничена высотой).
     */
    private void drawHeader(PdfWriter writer) {
        if (headerImage == null) {
            return;
        }
        Rectangle page = writer.getPageSize();
        float pageWidth = page.getWidth();
        float pageHeight = page.getHeight();
        float left = PdfUIHelper.MARGIN_LEFT;
        float availableWidth = pageWidth - left - PdfUIHelper.MARGIN_RIGHT;
        float aspect = headerImage.getWidth() / headerImage.getHeight();
        float height = availableWidth / aspect;
        if (height > PdfUIHelper.MARGIN_TOP) {
            height = PdfUIHelper.MARGIN_TOP;
        }
        float width = height * aspect;
        float x = left + (availableWidth - width) / 2f;
        float y = pageHeight - height;

        headerImage.scaleAbsolute(width, height);
        headerImage.setAbsolutePosition(x, y);
        PdfContentByte cb = writer.getDirectContent();
        cb.saveState();
        cb.addImage(headerImage);
        cb.restoreState();
    }

    /**
     * Реквизиты по центру нижнего колонтитула и номер страницы справа внизу,
     * на отдельной строке под ними.
     */
    private void drawFooter(PdfWriter writer) {
        BaseFont baseFont = PdfUIHelper.textBaseFont();
        if (baseFont == null) {
            try {
                baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, false);
            } catch (Exception e) {
                log.error("Нет шрифта для нижнего колонтитула: {}", e.getMessage());
                return;
            }
        }
        Rectangle page = writer.getPageSize();
        float pageWidth = page.getWidth();
        float right = pageWidth - PdfUIHelper.MARGIN_RIGHT;
        String pageNumber = String.valueOf(writer.getCurrentPageNumber());

        PdfContentByte cb = writer.getDirectContent();
        cb.saveState();
        cb.beginText();
        cb.setFontAndSize(baseFont, FOOTER_FONT_SIZE);

        // Номер страницы — нижняя строка колонтитула, прижата к правому углу.
        cb.showTextAligned(Element.ALIGN_RIGHT, pageNumber, right, FOOTER_BOTTOM_OFFSET, 0);
        // Реквизиты — по центру, выше номера страницы.
        float y = FOOTER_BOTTOM_OFFSET + FOOTER_LEADING;
        for (String line : FOOTER_LINES) {
            cb.showTextAligned(Element.ALIGN_CENTER, line, pageWidth / 2f, y, 0);
            y += FOOTER_LEADING;
        }
        cb.endText();
        cb.restoreState();
    }
}