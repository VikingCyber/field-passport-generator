package com.viking.field_passport_generator.data.dto.satellite;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FieldSpectralResponse(
        Long id,
        @JsonProperty("data")
        List<SatelliteScan> data
) {}
