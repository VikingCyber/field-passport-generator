package com.viking.generator.util.pdf;

import com.viking.generator.model.FieldPassport;
import com.viking.generator.model.media.SatelliteImage;
import java.util.List;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Раздел 5 «Спутниковые снимки NDVI». Без снимков — заголовок и плейсхолдер;
 * со снимками заголовок передаётся внутрь {@link PdfImages#addSatelliteImagesColumn},
 * где вшивается в первую keepTogether-таблицу и не отрывается от первого снимка.
 * Начинается с новой страницы.
 */
public final class PdfSection5Satellites implements PdfSectionRenderer {

    public static final String TITLE = "Раздел 5. Спутниковые снимки NDVI";

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public void render(Document document, PdfWriter writer, SectionPageRecorder recorder,
            FieldPassport passport) throws DocumentException {
        document.newPage();
        markSection(recorder, writer);

        List<SatelliteImage> satelliteImages = passport.satelliteImages();
        if (satelliteImages == null || satelliteImages.isEmpty()) {
            document.add(PdfTypography.createSectionTitle(TITLE));
            document.add(PdfTypography.createParagraph(PdfTypography.NO_DATA));
        } else {
            PdfImages.addSatelliteImagesColumn(document, TITLE, satelliteImages);
        }
    }
}