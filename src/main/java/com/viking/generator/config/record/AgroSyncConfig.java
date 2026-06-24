package com.viking.generator.config.record;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Configuration for Agrosignal metadata synchronization.
 *
 * @param timezone the timezone for current API requirements
 * @param fromDate the start date of the sync period
 * @param toDate the end date of the sync period
 * @param companyId companyId the Agrosignal identifier of the agricultural holding.
 */
public record AgroSyncConfig(
        ZoneId timezone,
        Instant fromDate,
        Instant toDate,
        long companyId
) {

    /**
     * Returns the current UTC offset for the configured timezone in minutes.
     *
     * <p>Example: for {@code Asia/Krasnoyarsk} (UTC+7) this returns {@code 420}.</p>
     *
     * @return offset in minutes from UTC.
     */
    public int timezoneOffsetMinutes() {
        return timezone.getRules().getOffset(Instant.now()).getTotalSeconds() / 60;
    }
}
