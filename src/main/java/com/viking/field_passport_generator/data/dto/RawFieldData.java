package com.viking.field_passport_generator.data.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RawFieldData(
    String department,
    @JsonProperty("sourceId") String fieldId,
    String field,
    double fieldArea,
    String crop,
    String variety,
    String reproduction
) {}
