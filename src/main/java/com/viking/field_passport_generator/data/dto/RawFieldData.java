package com.viking.field_passport_generator.data.dto;

public record RawFieldData(
    String department,
    String field,
    double fieldArea,
    String crop,
    String variety,
    String reproduction
) {}
