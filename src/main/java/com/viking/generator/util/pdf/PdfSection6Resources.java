package com.viking.generator.util.pdf;

import com.viking.generator.model.FieldPassport;
import com.viking.generator.model.tables.TechJournalTableRow;
import java.util.List;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Раздел 6 «Работающие механизаторы, техника, агрегаты»: техжурнал в таблице.
 * Начинается с новой страницы.
 */
public final class PdfSection6Resources implements PdfSectionRenderer {

    public static final String TITLE = "Раздел 6. Работающие механизаторы, техника, агрегаты.";

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public void render(Document document, PdfWriter writer, SectionPageRecorder recorder,
            FieldPassport passport) throws DocumentException {
        document.newPage();
        markSection(recorder, writer);
        document.add(PdfTypography.createSectionTitle(TITLE));

        List<TechJournalTableRow> techJournalTableRows = passport.resources();
        document.add(PdfTables.createTechJournalTable(techJournalTableRows));
    }
}