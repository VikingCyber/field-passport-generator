package com.viking.generator.util.pdf;

import com.viking.generator.model.FieldPassport;
import com.viking.generator.model.tables.OperationTableRow;
import java.util.List;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Раздел 2 «Выполненные работы»: таблица операций и расход ТМЦ. Начинается
 * с новой (альбомной) страницы — ориентацию уже переключил Раздел 1.
 */
public final class PdfSection2Operations implements PdfSectionRenderer {

    public static final String TITLE = "Раздел 2. Выполненные работы";

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

        List<OperationTableRow> operationTableRows = passport.operations();
        document.add(PdfTables.createOperationsTable(operationTableRows));
        document.add(PdfTables.createTmcContainer(operationTableRows));
    }
}