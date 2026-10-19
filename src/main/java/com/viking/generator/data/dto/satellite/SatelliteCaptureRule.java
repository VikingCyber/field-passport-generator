package com.viking.generator.data.dto.satellite;

import java.util.List;

public record SatelliteCaptureRule(
        String key,
        List<Integer> offsets,
        List<String> labels
) {
}
