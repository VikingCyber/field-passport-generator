package com.viking.field_passport_generator.mapper;

import com.viking.field_passport_generator.data.dto.chart.ChartPoint;
import com.viking.field_passport_generator.data.dto.satellite.IndexData;
import com.viking.field_passport_generator.data.dto.satellite.SatelliteScan;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public class ChartMapper {
    private final double cloudThreshold;
    private static final double EPSILON = 1e-6;

    public ChartMapper(double cloudThreshold) {
        this.cloudThreshold = cloudThreshold;
    }

    public List<ChartPoint> toChartPoints(List<SatelliteScan> scans) {
        if (scans == null) return Collections.emptyList();

        return scans.stream()
                .filter(this::isValidForChart)
                .map(this::mapToPoint)
                .toList();
    }

    private boolean isValidForChart(SatelliteScan scan) {
        if (scan.cloud() != null && scan.cloud() > cloudThreshold) {
            return false;
        }

        double sum = Math.abs(getSafeMean(scan.ndvi())) +
                Math.abs(getSafeMean(scan.ndwi())) +
                Math.abs(getSafeMean(scan.msi())) +
                Math.abs(getSafeMean(scan.gli()));

        return sum > EPSILON;
    }

    private double getSafeMean(IndexData data) {
        return (data != null && data.mean() != null) ? data.mean() : 0.0;
    }

    private double getSafeMax(IndexData data) {
        return (data != null && data.max() != null) ? data.max() : 0.0;
    }

    private double getSafeMin(IndexData data) {
        return (data != null && data.min() != null) ? data.min() : 0.0;
    }

    private ChartPoint mapToPoint(SatelliteScan scan) {
        LocalDate date = LocalDate.parse(scan.date());
        return new ChartPoint(
                date,
                getSafeMean(scan.ndvi()),
                getSafeMin(scan.ndvi()),
                getSafeMax(scan.ndvi()),
                getSafeMean(scan.ndwi()),
                getSafeMean(scan.msi()),
                getSafeMean(scan.gli())
        );
    }
}
