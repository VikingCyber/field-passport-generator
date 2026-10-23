package com.viking.generator.util.pdf;

import com.viking.generator.model.FieldPassport;
import com.viking.generator.model.media.FieldMapImage;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Раздел «Снимок поля с карты»: PNG-снимок поля (OSM-подложка + контур) сразу после
 * раздела 1. Заголовок вшит в keepTogether-таблицу с изображением (по образцу
 * {@link PdfSection4Indexes}), чтобы они не разъезжались между страницами.
 * Начинается с новой страницы (альбомной, как и последующие разделы).
 */
public final class PdfSectionFieldMap implements PdfSectionRenderer {

    public static final String TITLE = "Снимок поля с карты";

    @Override
    public String title() {
        return TITLE;
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
