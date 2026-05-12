package com.viking.field_passport_generator.model;

import com.viking.field_passport_generator.model.note.NoteSection;

import java.util.List;

public record FieldPassport(
    GeneralInfo generalInfo,
    List<OperationTableRow> operations,
    NoteSection notesSection,
    ChartImage indexChart,
    List<SatelliteImage> satelliteImages,
    List<TechJournalTableRow> resources
) {
    public void clearImageData() {
        if (this.satelliteImages != null) {
            this.satelliteImages.forEach(img -> img.setImageBytes(null));
        }
        if (this.indexChart != null) {
            this.indexChart.setImageBytes(null);
        }
        if (this.notesSection != null && this.notesSection.images() != null) {
            this.notesSection.images().forEach(img -> img.setImageBytes(null));
        }
    }
}
