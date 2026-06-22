package com.viking.generator.config;

import java.nio.file.Path;

public record SatelliteConfigInternal(
        Path cachePath,
        String fromDate,
        String toDate,
        int scanWindowDays,
        double maxCloudThreshold,
        double cloudWeightFactor,
        String extension
) {}
