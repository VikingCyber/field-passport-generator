package com.viking.generator.config.record;

import com.viking.generator.data.dto.satellite.SatelliteCaptureRule;
import com.viking.generator.model.common.SpectralIndex;

import java.util.List;
import java.util.Set;

public record SatelliteConfig(
        double cloudThreshold,
        double cloudWeightFactor,
        int scanWindowDays,
        String fromDate,
        String toDate,
        Set<SpectralIndex> indices,
        List<SatelliteCaptureRule> mappingRules
) {}
