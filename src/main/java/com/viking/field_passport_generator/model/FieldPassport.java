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
) {}
