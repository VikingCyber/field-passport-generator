package com.viking.generator.data.dto;

import java.util.List;

public record CropRotationRequest(
        String from,
        String to,
        List<Long> entityId,
        List<String> strEntityId,
        Filters filters,
        int limit,
        int page,
        int start,
        String reportId,
        String reportMode,
        int tzOffset
) {
    public record Filters(
            List<Long> fieldsFilter,
            List<Long> unitsFilter,
            List<Long> driversFilter,
            List<Long> extraParamsFilter
    ) {}
}
