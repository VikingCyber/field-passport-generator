package com.viking.generator.model.sections;

public record GeneralInfo(
    String department,
    String fieldName,
    double fieldArea,
    int year,
    CropRotation rotation
) {}
