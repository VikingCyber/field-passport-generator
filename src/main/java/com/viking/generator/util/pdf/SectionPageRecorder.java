package com.viking.generator.util.pdf;

import java.util.LinkedHashMap;
import java.util.Map;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Запоминает номер страницы для каждого раздела во время чернового прохода.
 * Каждый раздел начинается с новой страницы (перед ним вызывается {@code newPage()}),
 * поэтому отметка ставится сразу после {@code newPage()} либо на первой странице
 * перед «Разделом 1». Выделен из вложенного класса {@code PdfGeneratorService}
 * в отдельный класс — секции рендера вызывают {@link #mark(String, PdfWriter)}
 * напрямую.
 */
public class SectionPageRecorder {

    private final Map<String, Integer> pages = new LinkedHashMap<>();

    public void mark(String sectionTitle, PdfWriter writer) {
        pages.putIfAbsent(sectionTitle, writer.getCurrentPageNumber());
    }

    /** Названия разделов в порядке следования с номерами страниц черновика. */
    public Map<String, Integer> sectionTitles() {
        return new LinkedHashMap<>(pages);
    }
}