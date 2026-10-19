package com.viking.generator.model.sections;

import java.util.List;

public record GeneralInfo(
    String department,
    String fieldName,
    double fieldArea,
    int year,
    List<CropRotation> rotations
) {}