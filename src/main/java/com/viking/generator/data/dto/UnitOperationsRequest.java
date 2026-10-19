package com.viking.generator.data.dto;


import java.util.List;

public record UnitOperationsRequest(
        String reportId,
        String from,
        String to,
        long companyId,
        List<Long> entityId,
        List<String> strEntityId,
        Filters filters,
        int limit,
        int page,
        int start,
        int tzOffset
) {
    public record Filters(
            List<Long> fieldsFilter
    ) {}
}
