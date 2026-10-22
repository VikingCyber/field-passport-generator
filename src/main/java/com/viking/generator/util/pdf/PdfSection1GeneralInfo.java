package com.viking.generator.util.pdf;

import com.viking.generator.model.FieldPassport;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.PageSize;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Раздел 1 «Общая информация»: подразделение, наименование, площадь и севооборот.
 * Раздел снимает прежние настройки страницы и переключает документ в альбомную
 * ориентацию — разделы 2–6 идут на повёрнутых листах.
 * <p>
 * Раздел 1 начинается с текущей страницы ({@code newPage()} перед ним нет):
 * в черновике это страница 1, в финале — страница после содержания.
 */
public final class PdfSection1GeneralInfo implements PdfSectionRenderer {

    public static final String TITLE = "Раздел 1. Общая информация.";

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public void render(Document document, PdfWriter writer, SectionPageRecorder recorder,
            FieldPassport passport) throws DocumentException {
        markSection(recorder, writer);
        document.add(PdfTypography.createSectionTitle(TITLE));

        document.add(PdfTypography.createParagraph(
                "1.1. Подразделение: " + PdfTypography.orNoData(passport.generalInfo().department())));
        document.add(PdfTypography.createParagraph(
                "1.2. Наименование: " + PdfTypography.orNoData(passport.generalInfo().fieldName())));
        document.add(PdfTypography.createParagraph("1.3. Текущая площадь: " + PdfTypography.formatArea(
                passport.generalInfo().fieldArea()) + " Га"));

        document.add(PdfTypography.createParagraph(
                "1.4. Севооборот в " + passport.generalInfo().year() + " году:"));

        var rotations = passport.generalInfo().rotations();
        if (rotations == null || rotations.isEmpty()) {
            document.add(PdfTypography.createParagraph(PdfTypography.NO_DATA));
        } else {
            // Подписанные строки без маркера: «Культура: …», «Сорт: …», «Репродукция: …».
            // Отсутствующие данные подставляются плейсхолдером orNoData, поэтому всегда
            // понятно, какого именно значения не хватает («Культура: — нет данных —»).
            for (var rotation : rotations) {
                document.add(PdfTypography.createRotationItem("Культура", rotation.crop()));
                document.add(PdfTypography.createRotationItem("Сорт", rotation.variety()));
                document.add(PdfTypography.createRotationItem("Репродукция", rotation.reproduction()));
            }
        }

        // Альбомные листы шире: шапка на всю ширину (842 pt) выше портретной,
        // поэтому верхнее поле увеличивается (действует со следующей страницы).
        document.setPageSize(PageSize.A4.rotate());
        document.setMargins(PdfTypography.MARGIN_LEFT, PdfTypography.MARGIN_RIGHT,
                PdfTypography.LANDSCAPE_MARGIN_TOP, PdfTypography.MARGIN_BOTTOM);
    }
}