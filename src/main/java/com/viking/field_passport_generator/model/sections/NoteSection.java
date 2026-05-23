package com.viking.field_passport_generator.model.sections;

import com.viking.field_passport_generator.model.media.NoteImage;
import com.viking.field_passport_generator.model.tables.NoteTableRow;

import java.util.List;

public record NoteSection(
        List<NoteTableRow> notes,
        List<NoteImage> images
) {

    public boolean isEmpty() {
        return notes.isEmpty() && images.isEmpty();
    }
    public NoteSection withImages(List<NoteImage> newImages) {
        return new NoteSection(this.notes, newImages);
    }
}
