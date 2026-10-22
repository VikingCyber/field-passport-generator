package com.viking.generator.util.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.Element;
import org.openpdf.text.Paragraph;

class PdfTypographyTest {

    @Test
    @DisplayName("Шапка паспорта: «Паспорт поля» по центру, 14 pt обычным шрифтом")
    void passportTitleCentered() {
        Paragraph p = PdfTypography.createPassportTitle();

        assertEquals("Паспорт поля", p.getContent());
        assertEquals(Element.ALIGN_CENTER, p.getAlignment());
        assertEquals(14, p.getFont().getSize(), "Заголовок должен быть 14 pt");
    }

    @Test
    @DisplayName("orNoData: пустое или пробельное значение заменяется единым плейсхолдером")
    void orNoDataReplacesBlank() {
        assertEquals(PdfTypography.NO_DATA, PdfTypography.orNoData(null));
        assertEquals(PdfTypography.NO_DATA, PdfTypography.orNoData("   "));
        assertEquals("Пшеница", PdfTypography.orNoData("Пшеница"));
    }

    @Test
    @DisplayName("orNoData: прочерк (маркер отсутствия из API) заменяется информативным плейсхолдером")
    void orNoDataReplacesDashPlaceholder() {
        // PassportMapper.UNKNOWN_ROTATION и FieldDataAggregator.toCropRotation подставляют "-"
        // при отсутствии данных — он не должен попадать в отчёт как пустой прочерк.
        assertEquals(PdfTypography.NO_DATA, PdfTypography.orNoData("-"));
        assertEquals(PdfTypography.NO_DATA, PdfTypography.orNoData("–"));
        assertEquals(PdfTypography.NO_DATA, PdfTypography.orNoData("—"));
        assertEquals("Арго СЭ", PdfTypography.orNoData("Арго СЭ"));
    }

    @Test
    @DisplayName("Строка севооборота: подпись с двоеточием и значением без маркера")
    void rotationItemHasLabelAndNoMarker() {
        Paragraph item = PdfTypography.createRotationItem("Культура", "Картофель 2026");
        assertEquals("Культура: Картофель 2026", item.getContent());
        assertTrue(!item.getContent().startsWith("•"),
                "Строка севооборота не должна иметь маркер списка, только подпись");

        Paragraph missing = PdfTypography.createRotationItem("Сорт", null);
        assertEquals("Сорт: " + PdfTypography.NO_DATA, missing.getContent(),
                "Отсутствующие данные подписываются плейсхолдером, чтобы было видно, чего нет");
    }
}