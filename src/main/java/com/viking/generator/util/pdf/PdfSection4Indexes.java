package com.viking.generator.util.pdf;

import com.viking.generator.model.FieldPassport;
import com.viking.generator.model.media.ChartImage;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Раздел 4 «Индексы»: график спектральных индексов. Заголовок раздела вшит
 * в keepTogether-таблицу с графиком ({@link PdfImages#addChartImage}), чтобы
 * он не отрывался от графика между страницами. Начинается с новой страницы.
 */
public final class PdfSection4Indexes implements PdfSectionRenderer {

    public static final String TITLE = "Раздел 4. Индексы";

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public void render(Document document, PdfWriter writer, SectionPageRecorder recorder,
            FieldPassport passport) throws DocumentException {
        document.newPage();
        markSection(recorder, writer);

        ChartImage chartImage = passport.indexChart();
        PdfImages.addChartImage(document, chartImage);
    }
}