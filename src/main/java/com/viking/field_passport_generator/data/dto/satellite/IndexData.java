package com.viking.field_passport_generator.data.dto.satellite;

import com.fasterxml.jackson.annotation.JsonProperty;


public record IndexData(
        @JsonProperty("max") Double max,
        @JsonProperty("mean") Double mean,
        @JsonProperty("min") Double min,
        @JsonProperty("imageUrl") String url
) {
}
