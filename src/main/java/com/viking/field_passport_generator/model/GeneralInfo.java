package com.viking.field_passport_generator.model;

public record GeneralInfo(
    String department,
    String fieldName,
    double fieldArea,
    int year,
    CropRotation rotation
) {}
