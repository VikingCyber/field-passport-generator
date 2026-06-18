package com.viking.field_passport_generator.config.record;

public record AgroSyncConfig(
        String fromDate,
        String toDate,
        long companyId
) {
}
