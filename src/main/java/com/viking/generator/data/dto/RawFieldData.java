package com.viking.generator.data.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RawFieldData(
    String department,
    @JsonProperty("sourceId") Long fieldId,
    String field,
    double fieldArea,
    String crop,
    String variety,
    String reproduction
) {}
