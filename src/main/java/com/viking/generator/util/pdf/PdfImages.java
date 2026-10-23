package com.viking.generator.util.pdf;

import com.viking.generator.model.media.ChartImage;
import com.viking.generator.model.media.FieldMapImage;
import com.viking.generator.model.media.SatelliteImage;
import com.viking.generator.util.ImageOptimizer;
import java.awt.Color;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfTemplate;
import org.openpdf.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Изображения PDF-паспорта: фотографии заметок, график индексов и спутниковые снимки.
 * Выделено из бывшего {@code PdfUIHelper}: здесь только изображения, шрифты и поля —
 * из {@link PdfTypography}. Сжатие снимков и фото делает {@link ImageOptimizer}
 * (слой подготовки изображений), а не рендера.
 */
public final class PdfImages {

    private static final Logger log = LoggerFactory.getLogger(PdfImages.class);

    /**
     * Максимальная ширина спутникового снимка в Разделе 5, pt. Ограничение по ширине
     * текстовой области альбомной страницы (~728 pt); сами снимки NDVI обычно
     * берутся с запасом по высоте, поэтому лимитируется в основном высота.
     */
    private static final float SATELLITE_IMG_MAX_WIDTH = 500f;
    /**
     * Максимальная высота спутникового снимка, pt. Подпись + снимок должны помещаться
     * в полезную высоту альбомной страницы (~447 pt), чтобы keepTogether-таблица
     * не разрывалась: подпись на одной странице, снимок на другой.
     */
    private static final float SATELLITE_IMG_MAX_HEIGHT = 380f;
    /**
     * Лимит высоты для ПЕРВОГО снимка Раздела 5, pt — на 20 pt меньше общего.
     * В ту же keepTogether-таблицу вшит заголовок раздела, поэтому первому снимку
     * доступно меньше места: иначе заголовок отрывается от снимка.
     */
    private static final float SATELLITE_IMG_MAX_HEIGHT_FIRST = 360f;
    /**
     * Максимальная высота графика Раздела 4, pt. Заголовок «Раздел 4. Индексы» лежит
     * в той же keepTogether-таблице, что и график: сумма «заголовок + график» должна
     * умещаться в полезную высоту альбомной страницы (~447 pt). Заголовок ≈24 pt
     * + отступ ячейки 4 pt + отступ таблицы 20 pt ≈ 48 pt, поэтому графику доступно
     * ≈399 pt — берём с запасом 395 pt.
     */
    private static final float CHART_IMG_MAX_HEIGHT = 395f;
    /**
     * Максимальная высота снимка поля с карты, pt. Кадр снимка 1600×767 (≈2.09:1),
     * поэтому при вписывании в ширину текстовой области (~714 pt) его высота ≈342 pt
     * и с запасом умещается вместе с заголовком в полезную высоту альбомной страницы.
     */
    private static final float FIELD_MAP_IMG_MAX_HEIGHT = 380f;
    /**
     * Ширина текстовой области альбомной страницы: 842 − 3 см − 1.5 см.
     * Считается от публичных отступов PdfTypography.
     */
    private static final float LANDSCAPE_TEXT_WIDTH =
            PageSize.A4.rotate().getWidth() - PdfTypography.MARGIN_LEFT - PdfTypography.MARGIN_RIGHT;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private PdfImages() {
        throw new UnsupportedOperationException("Utility class, constructors are forbidden");
    }

    /**
     * Ячейка фото в сетке «3.2 Фотографии»: уменьшенное фото по центру квадратной
     * ячейки 208 pt и поверх — подпись с ID комплекса (белый ореол вокруг текста,
     * чтобы надпись читалась на любом фоне фото).
     * <p>
     * 208 pt — максимальный размер блока, при котором на альбомную страницу с учётом
     * шапки и подвала помещаются два ряда по три фото (два ряда дают 416 pt против
     * 447.6 pt полезной высоты). Было 230 pt — второй ряд не влезал, и 6 фото
     * разъезжались на две страницы.
     */
    public static PdfPCell createPhotoBlock(PdfWriter writer, byte[] imageBytes, String complexId) {
        PdfPCell cell = new PdfPCell();
        float size = 208f;
        cell.setFixedHeight(size);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(1f);

        try {
            if (imageBytes != null && imageBytes.length > 0) {
                // Фото в PDF не нужно в оригинальном разрешении (до 15 МБ): уменьшаем
                // и пережимаем в JPEG. Ошибки декодирования отдаются оригиналом.
                byte[] fitBytes = ImageOptimizer.forPhoto(imageBytes);
                Image img = Image.getInstance(fitBytes);

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

                // Этап 4: накладываем текст ID с белым ореолом
                template.beginText();
                template.setFontAndSize(PdfTypography.FONT_TABLE_TITLE.getBaseFont(), 10f);

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

    /** Сетка фотографий из заметок: 3 колонки, в шапке «3.2 Фотографии». */
    public static PdfPTable createPhotoGrid(PdfWriter writer, Map<String, byte[]> photos) {
        if (photos == null || photos.isEmpty()) {
            return placeholderTable();
        }

        PdfPTable grid = new PdfPTable(3);
        grid.setWidthPercentage(100f);
        grid.setSpacingBefore(10f);

        grid.setKeepTogether(false);
        grid.setSplitRows(true);

        PdfPCell titleCell = new PdfPCell(PdfTypography.createParagraph("3.2 Фотографии"));
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

    /**
     * График спектральных индексов Раздела 4 в keepTogether-таблице вместе с
     * заголовком, чтобы они не разъезжались по страницам. Исходный график
     * не пережимается — только вписывается в текстовую область.
     */
    public static void addChartImage(Document document, ChartImage chartImage) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(20f);
        table.setKeepTogether(true);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph title = PdfTypography.createSectionTitle("Раздел 4. Индексы");
        title.setAlignment(Element.ALIGN_LEFT);
        cell.addElement(title);

        if (chartImage == null || chartImage.getImageBytes() == null) {
            cell.addElement(new Phrase(PdfTypography.NO_DATA, PdfTypography.FONT_TEXT));
        } else {
            try {
                // График в оригинале может быть очень крупным (например 2000×1200 px,
                // встраивается в точках как есть). Без scaleToFit график вместе
                // с заголовком не помещается в полезную высоту альбомной страницы,
                // и keepTogether-таблица разрывается: заголовок на одной странице,
                // график на другой. Ограничиваем по ширине текстовой области и высоте,
                // чтобы заголовок + график всегда были рядом. По качеству не сжимаем:
                // в PDF уходит исходный график, при этом пользователь просил графики
                // не пережимать.
                Image img = Image.getInstance(chartImage.getImageBytes());
                float maxW = Math.min(LANDSCAPE_TEXT_WIDTH, img.getWidth());
                float maxH = Math.min(CHART_IMG_MAX_HEIGHT, img.getHeight());
                img.scaleToFit(maxW, maxH);
                img.setAlignment(Image.ALIGN_CENTER);
                cell.addElement(img);
            } catch (Exception e) {
                log.error("Error rendering chartImage: {}", e.getMessage());
                cell.addElement(new Phrase("Ошибка отрисовки графика", PdfTypography.FONT_TEXT));
            }
        }
        table.addCell(cell);
        document.add(table);
    }

    /**
     * Контур поля на карте в keepTogether-таблице вместе с заголовком.
     * Аналог {@link #addChartImage(Document, ChartImage)}, но для снимка поля:
     * изображение не сжимается, только вписывается в текстовую область альбомной
     * страницы. Если снимка нет — placeholder «нет данных».
     */
    public static void addFieldMapImage(Document document, FieldMapImage fieldMap,
            String sectionTitle) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(20f);
        table.setKeepTogether(true);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph title = PdfTypography.createSectionTitle(sectionTitle);
        title.setAlignment(Element.ALIGN_LEFT);
        cell.addElement(title);

        if (fieldMap == null || fieldMap.getImageBytes() == null) {
            cell.addElement(new Phrase(PdfTypography.NO_DATA, PdfTypography.FONT_TEXT));
        } else {
            try {
                Image img = Image.getInstance(fieldMap.getImageBytes());
                float maxW = Math.min(LANDSCAPE_TEXT_WIDTH, img.getWidth());
                float maxH = Math.min(FIELD_MAP_IMG_MAX_HEIGHT, img.getHeight());
                img.scaleToFit(maxW, maxH);
                img.setAlignment(Image.ALIGN_CENTER);
                cell.addElement(img);
            } catch (Exception e) {
                log.error("Ошибка вставки снимка поля с карты: {}", e.getMessage());
                cell.addElement(new Phrase("Ошибка отрисовки снимка поля", PdfTypography.FONT_TEXT));
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
        String plan = img.getPlanDate() != null ? img.getPlanDate().format(DATE_FORMAT)
                : PdfTypography.NO_DATA;
        String actual = img.getActualDate() != null ? img.getActualDate().format(DATE_FORMAT)
                : PdfTypography.NO_DATA;
        return String.format("%s\nПлановая дата: %s · Съёмка: %s",
                PdfTypography.orNoData(img.getDescription()), plan, actual);
    }

    /**
     * Выводит спутниковые снимки один за другим в столбик.
     * <p>
     * Заголовок раздела (если передан) встраивается внутрь первой keepTogether-таблицы,
     * прямо перед подписью первого снимка. Так заголовок гарантированно остаётся
     * на одной странице с первым снимком и не отрывается. Из-за дополнительного
     * заголовка первому снимку выделяется меньший лимит высоты
     * (SATELLITE_IMG_MAX_HEIGHT_FIRST), остальным — обычный.
     */
    public static void addSatelliteImagesColumn(Document document, String sectionTitle,
            List<SatelliteImage> satelliteImages) throws DocumentException {
        for (int i = 0; i < satelliteImages.size(); i++) {
            SatelliteImage satImg = satelliteImages.get(i);
            // Таблица из одной колонки: картинка и подпись не разрываются переносом страницы
            PdfPTable table = new PdfPTable(1);
            table.setWidthPercentage(100f);
            table.setSpacingBefore(20f);
            table.setKeepTogether(true); // Критично: держит картинку и подпись на одной странице

            PdfPCell cell = new PdfPCell();
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);

            try {
                if (i == 0 && sectionTitle != null) {
                    Paragraph title = PdfTypography.createSectionTitle(sectionTitle);
                    title.setAlignment(Element.ALIGN_LEFT);
                    cell.addElement(title);
                }

                Paragraph p = new Paragraph(formatSatelliteCaption(satImg), PdfTypography.FONT_TABLE_BODY);
                p.setAlignment(Element.ALIGN_CENTER);
                p.setSpacingAfter(10f);
                cell.addElement(p);

                if (satImg.getImageBytes() != null) {
                    // Спутниковые снимки до 4 МБ в оригинале не нужны в PDF: уменьшаем
                    // до ширины текстового поля и пережимаем в JPEG (снимки NDVI — RGBA,
                    // фон склеивается в непрозрачный белый). Ошибка декодирования
                    // вернёт исходные байты.
                    byte[] fitBytes = ImageOptimizer.forSatellite(satImg.getImageBytes());
                    Image img = Image.getInstance(fitBytes);

                    // Не растягиваем снимок сверх нативного размера (иначе мелкие
                    // исходники ~400 px начинают «пикселить») и ограничиваем высоту,
                    // чтобы подпись + снимок влезали в полезную высоту альбомной
                    // страницы (~447 pt) — иначе keepTogether разрывает блок:
                    // подпись остаётся на одной странице, снимок уходит на другую.
                    // Первый снимок идёт в той же таблице, что и заголовок раздела,
                    // поэтому для него запас по высоте меньше.
                    float maxW = Math.min(SATELLITE_IMG_MAX_WIDTH, img.getWidth());
                    float maxMaxH = i == 0 ? SATELLITE_IMG_MAX_HEIGHT_FIRST : SATELLITE_IMG_MAX_HEIGHT;
                    float maxH = Math.min(maxMaxH, img.getHeight());
                    img.scaleToFit(maxW, maxH);
                    img.setAlignment(Image.ALIGN_CENTER);
                    cell.addElement(img);
                }
            } catch (Exception e) {
                log.error("Error rendering satellite image: {}", e.getMessage());
                cell.addElement(new Phrase("Ошибка отрисовки снимка", PdfTypography.FONT_TABLE_BODY));
            }
            table.addCell(cell);
            document.add(table);
        }
    }

    /** Таблица-заглушка для раздела снимков/фото без данных. */
    private static PdfPTable placeholderTable() {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(10f);
        PdfPCell cell = new PdfPCell(new Phrase(PdfTypography.NO_DATA, PdfTypography.FONT_TEXT));
        cell.setBorder(Rectangle.NO_BORDER);
        table.addCell(cell);
        return table;
    }
}