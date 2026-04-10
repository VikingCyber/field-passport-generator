package com.viking.field_passport_generator.model;

import java.util.List;

public record FieldPassport(
    GeneralInfo generalInfo,
    List<OperationTableRow> operations,
    NoteSection notesSection,
    List<TechJournalTableRow> resources
) {}
