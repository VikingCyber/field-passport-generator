package com.viking.generator.config.record;

import java.time.Instant;
import java.time.ZoneId;

public record AgroSyncConfig(
        ZoneId timezone,
        Instant fromDate,
        Instant toDate,
        long companyId
) {
}
