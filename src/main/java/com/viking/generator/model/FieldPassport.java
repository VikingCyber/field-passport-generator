package com.viking.generator.model;

import com.viking.generator.model.media.ChartImage;
import com.viking.generator.model.media.SatelliteImage;
import com.viking.generator.model.sections.NoteSection;
import com.viking.generator.model.sections.GeneralInfo;
import com.viking.generator.model.tables.OperationTableRow;
import com.viking.generator.model.tables.TechJournalTableRow;

import java.util.List;
import java.util.Objects;

public record FieldPassport(
    String fieldId,
    GeneralInfo generalInfo,
    List<OperationTableRow> operations,
    NoteSection notesSection,
    ChartImage indexChart,
    List<SatelliteImage> satelliteImages,
    List<TechJournalTableRow> resources
) {
    public void clearImageData() {
        if (this.satelliteImages != null) {
            this.satelliteImages.stream()
                    .filter(Objects::nonNull)
                    .forEach(img -> img.setImageBytes(null));
        }
        if (this.indexChart != null) {
            this.indexChart.setImageBytes(null);
        }
        if (this.notesSection != null && this.notesSection.images() != null) {
            this.notesSection.images().stream()
                    .filter(Objects::nonNull)
                    .forEach(img -> img.setImageBytes(null));
        }
    }
}
