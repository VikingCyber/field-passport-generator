package com.viking.field_passport_generator.models;

import java.util.List;

public record FieldPassport(
    GeneralInfo generalInfo,
    List<OperationTableRow> operations
) {}
