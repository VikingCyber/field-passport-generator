package com.viking.generator.util.pdf;

import com.viking.generator.model.FieldPassport;
import com.viking.generator.model.media.NoteImage;
import com.viking.generator.model.tables.NoteTableRow;
import com.viking.generator.util.NoteImageComparators;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Раздел 3 «Заметки»: сводная информация, таблица заметок и фотографии.
 * Фото снимков сортируются по индексу комплекса и выводятся сеткой на отдельной
 * странице внутри раздела ({@code newPage()} перед сеткой).
 * Начинается с новой страницы.
 */
public final class PdfSection3Notes implements PdfSectionRenderer {

    private static final Logger log = LoggerFactory.getLogger(PdfSection3Notes.class);

    public static final String TITLE = "Раздел 3. Заметки";

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
        document.add(PdfTypography.createParagraph("3.1. Сводная информация"));

        List<NoteTableRow> noteTableRows = passport.notesSection().notes();
        document.add(PdfTables.createNotesTable(noteTableRows));

        List<NoteImage> noteImages = passport.notesSection().images();
        Map<String, byte[]> photoMap = new LinkedHashMap<>();
        noteImages.stream()
                .sorted(NoteImageComparators.byComplexIndex())
                .forEach(img -> {
                    byte[] bytes = img.getImageBytes();
                    if (bytes != null) {
                        photoMap.put(img.getComplexIndex(), bytes);
                        log.debug("Добавлено фото: индекс={}, размер={} байт",
                                img.getComplexIndex(), bytes.length);
                    } else {
                        log.warn("Пустые данные для фото: индекс={}", img.getComplexIndex());
                    }
                });

        document.newPage();
        document.add(PdfImages.createPhotoGrid(writer, photoMap));
    }
}