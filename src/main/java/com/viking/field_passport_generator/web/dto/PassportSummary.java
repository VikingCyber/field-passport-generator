package com.viking.field_passport_generator.web.dto;

public record PassportSummary(
        String id,
        String fieldName,
        String cropName,
        int year,
        double area,
        String status
) {
}
