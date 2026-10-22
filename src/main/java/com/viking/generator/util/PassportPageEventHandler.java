package com.viking.generator.util;

import com.viking.generator.util.pdf.PdfTypography;
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
 *   <li>верхний колонтитул — фирменная шапка-логотип: на портретных страницах
 *       {@code images/letterhead.jpg}, на альбомных — {@code images/letterhead_landscape.jpg},
 *       где надпись «СХП ДАРЫ МАЛИНОВКИ агрохолдинг» выполнена тем же тёмно-зелёным
 *       фирменным цветом, но уменьшена, чтобы на широком листе она не выглядела крупнее;</li>
 *   <li>нижний колонтитул — 3 строки реквизитов по центру (Arial 8 pt) и номер страницы,
 *       прижатый к правому углу (поле PAGE в образце отсутствует — нумерация добавлена).</li>
 * </ul>
 * Колонтитулы действуют на всех страницах, включая первую (в образце «отдельная первая
 * страница» нет), и адаптируются к повёрнутым (альбомным) страницам: координаты считаются
 * от текущего размера страницы {@link PdfWriter#getPageSize()}.
 */
public class PassportPageEventHandler extends PdfPageEventHelper {

    private static final Logger log = LoggerFactory.getLogger(PassportPageEventHandler.class);

    /** Версия шапки для портретных страниц: надпись тёмно-зелёная, оригинальный размер. */
    static final String HEADER_RESOURCE = "images/letterhead.jpg";
    /** Версия шапки для альбомных (повёрнутых) страниц: надпись тёмно-зелёная и уменьшенная. */
    static final String HEADER_LANDSCAPE_RESOURCE = "images/letterhead_landscape.jpg";

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

    /**
     * Гарантированный зазор (в пунктах) между нижним краем шапки и верхней границей
     * контента. Без него на альбомных (повёрнутых) страницах шапка заполняет всё верхнее
     * поле и нижним краем ложится ровно на границу контента — а продолжающаяся на новую
     * страницу таблица начинает повторную шапку ровно в этой точке, и картинка
     * закрашивает её верхнюю границу.
     */
    static final float HEADER_GAP = 10f;

    /** Фирменная шапка для портретных страниц; {@code null}, если ресурс не загрузился. */
    private Image headerImage;
    /** Фирменная шапка для альбомных страниц (надпись уменьшена); {@code null}, если ресурс не загрузился. */
    private Image headerImageLandscape;

    public PassportPageEventHandler() {
        headerImage = loadImage(HEADER_RESOURCE);
        headerImageLandscape = loadImage(HEADER_LANDSCAPE_RESOURCE);
    }

    static Image loadImage(String resource) {        try (InputStream is = PassportPageEventHandler.class.getClassLoader().getResourceAsStream(resource)) {
            if (is == null) {
                log.warn("Не найден ресурс {} — верхний колонтитул (шапка) не будет нарисован", resource);
                return null;
            }
            return Image.getInstance(is.readAllBytes());
        } catch (Exception e) {
            log.error("Не удалось загрузить шапку {}: {}", resource, e.getMessage());
            return null;
        }
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        drawHeader(writer);
        drawFooter(writer);
    }

    /**
     * Версия шапки для страницы: на альбомной — с уменьшенной надписью
     * ({@link #headerImageLandscape}), на портретной — оригинального размера
     * ({@link #headerImage}). При отсутствии нужной версии — фолбэк на другую.
     */
    Image headerImageFor(boolean landscape) {
        Image image = landscape ? headerImageLandscape : headerImage;
        if (image == null) {
            image = landscape ? headerImage : headerImageLandscape;
        }
        return image;
    }

    /**
     * Фирменная шапка сверху страницы — как в образце «26ХХХХХ Письмо(БланкЧистыйЛист)»:
     * растянута на всю ширину листа от левого края страницы ({@code positionH
     * relativeFrom="page" align="left"}, extent 592.7×56.4 pt против страницы 595 pt).
     * На повёрнутых (альбомных) страницах полная ширина дала бы картинку выше верхнего
     * поля — там высота ограничивается зазором {@link #HEADER_GAP}, чтобы шапка не
     * закрашивала верхнюю границу таблиц, продолжающихся с предыдущей страницы.
     */
    private void drawHeader(PdfWriter writer) {
        Rectangle page = writer.getPageSize();
        boolean landscape = page.getWidth() > page.getHeight();
        Image image = headerImageFor(landscape);
        if (image == null) {
            return;
        }
        float pageWidth = page.getWidth();
        float pageHeight = page.getHeight();
        float imageWidth = image.getWidth();
        float imageHeight = image.getHeight();
        if (Float.isNaN(imageWidth) || imageWidth <= 0f
                || Float.isNaN(imageHeight) || imageHeight <= 0f) {
            log.warn("Некорректные размеры шапки ({}x{}) — пропускаю отрисовку", imageWidth, imageHeight);
            return;
        }
        Rectangle rect = headerPlacement(pageWidth, pageHeight, imageWidth, imageHeight);
        if (rect == null) {
            return;
        }

        image.scaleAbsolute(rect.getWidth(), rect.getHeight());
        image.setAbsolutePosition(rect.getLeft(), rect.getBottom());
        PdfContentByte cb = writer.getDirectContent();
        cb.saveState();
        cb.addImage(image);
        cb.restoreState();
    }

    /**
     * Прямоугольник шапки в координатах PDF (нижний левый угол — начало координат).
     * Повторяет раскладку образца: шапка растянута на всю ширину листа и прижата к левому
     * краю страницы (в образце {@code positionH relativeFrom="page" align="left"}),
     * сохраняя пропорции картинки. На портретной странице высота выходит впритык к верхнему
     * полю (как extent из образца: 56.4–56.6 pt при поле 56.7 pt). На альбомной (повёрнутой)
     * странице контент сдвинут вниз увеличенным полем {@link PdfTypography#LANDSCAPE_MARGIN_TOP},
     * поэтому шапке разрешена полная ширина листа (842 pt) — цветочки уходят в правый угол,
     * а зазор {@link #HEADER_GAP} перед контентом сохраняется. Возвращает {@code null}
     * при некорректных пропорциях. Вынесено в статический метод для юнит-тестов геометрии.
     */
    static Rectangle headerPlacement(float pageWidth, float pageHeight,
            float imageWidth, float imageHeight) {
        if (!Float.isFinite(imageWidth) || imageWidth <= 0f
                || !Float.isFinite(imageHeight) || imageHeight <= 0f) {
            return null;
        }
        // Проекция на всю ширину страницы по пропорциям картинки (образец: align="left" от края страницы).
        float aspect = imageWidth / imageHeight;
        float height = pageWidth / aspect;
        if (height <= 0f) {
            return null;
        }
        // Полная ширина не всегда помещается в верхнее поле. На портретной странице
        // (595 pt) высота 56.6 pt вписывается в поле 56.7 pt. На альбомной (842 pt) —
        // контент сдвинут вниз полем LANDSCAPE_MARGIN_TOP, и полная ширина даёт высоту
        // ~80 pt при зазоре HEADER_GAP, так что картинка не перекрывает контент.
        float maxHeight = (pageWidth > pageHeight)
                ? PdfTypography.LANDSCAPE_MARGIN_TOP - HEADER_GAP
                : PdfTypography.MARGIN_TOP;
        if (height > maxHeight) {
            height = maxHeight;
        }
        if (height <= 0f) {
            return null;
        }
        float width = height * aspect;
        float x = 0f; // прижата к левому краю страницы, как в образце
        float y = pageHeight - height;
        // Rectangle хранит углы (llx, lly, urx, ury), а не (x, y, w, h).
        return new Rectangle(x, y, x + width, y + height);
    }

    /**
     * Реквизиты по центру нижнего колонтитула и номер страницы справа внизу,
     * на отдельной строке под ними.
     */
    private void drawFooter(PdfWriter writer) {
        BaseFont baseFont = PdfTypography.textBaseFont();
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
        float right = pageWidth - PdfTypography.MARGIN_RIGHT;
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