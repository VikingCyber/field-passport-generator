package com.viking.generator.util.pdf;

import com.viking.generator.model.FieldPassport;
import com.viking.generator.model.media.FieldMapImage;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Снимок поля с картой: PNG-изображение (OSM-подложка + контур поля) сразу после
 * раздела 1. Заголовок вшит в keepTogether-таблицу с изображением (по образцу
 * {@link PdfSection4Indexes}), чтобы они не разъезжались между страницами.
 * Начинается с новой страницы (альбомной, как и последующие разделы).
 * Карта — не раздел, поэтому из содержания исключена ({@link #inToc()} = false).
 */
public final class PdfSectionFieldMap implements PdfSectionRenderer {

    public static final String TITLE = "Контур поля на карте";

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public boolean inToc() {
        return false;
    }

    @Override
    public void render(Document document, PdfWriter writer, SectionPageRecorder recorder,
            FieldPassport passport) throws DocumentException {
        document.newPage();
        markSection(recorder, writer);

        FieldMapImage fieldMap = passport.fieldMap();
        PdfImages.addFieldMapImage(document, fieldMap, TITLE);
    }
}
