package com.viking.generator.util.pdf;

import com.viking.generator.model.FieldPassport;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Компонент секции паспорта: собирает свои элементы и отмечает свою страницу
 * в рекордере (только в черновом проходе). Каждый раздел — отдельный класс,
 * поэтому добавление нового раздела не трогает оркестрацию и другие секции.
 */
public interface PdfSectionRenderer {

    /** Название раздела — единый источник для заголовка и содержания. */
    String title();

    /** Отмечает страницу раздела, если идёт черновой проход (recorder != null). */
    default void markSection(SectionPageRecorder recorder, PdfWriter writer) {
        if (recorder != null) {
            recorder.mark(title(), writer);
        }
    }

    /**
     * Рисует раздел в документ.
     *
     * @param document документ OpenPDF
     * @param writer   writer для страниц (нужен рекордеру)
     * @param recorder рекордер страниц раздела; {@code null} в финальном проходе
     * @param passport данные поля
     */
    void render(Document document, PdfWriter writer, SectionPageRecorder recorder,
            FieldPassport passport) throws DocumentException;
}