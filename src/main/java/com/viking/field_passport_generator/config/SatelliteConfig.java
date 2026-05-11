package com.viking.field_passport_generator.config;

import com.viking.field_passport_generator.data.dto.satellite.SatelliteCaptureRule;

import java.nio.file.Path;
import java.util.List;

public record SatelliteConfig(
        Path cachePath,
        String fromDate,
        String toDate,
        int scanWindowDays,
        double maxCloudThreshold,
        double cloudWeightFactor,
        String extension
) {}
