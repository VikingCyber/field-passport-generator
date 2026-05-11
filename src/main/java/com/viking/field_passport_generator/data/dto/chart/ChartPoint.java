package com.viking.field_passport_generator.data.dto.chart;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

public record ChartPoint(
        LocalDate date,
        double ndviMean,
        double ndviMin,
        double ndviMax,
        double ndwiMean,
        double msiMean,
        double gliMean
) {
    public Date toDate(ZoneId timezone) {
        return java.util.Date.from(date.atStartOfDay(timezone).toInstant());
    }
}
