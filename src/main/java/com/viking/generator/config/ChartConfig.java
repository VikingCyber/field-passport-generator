package com.viking.generator.config;

import java.nio.file.Path;
import java.time.ZoneId;

public record ChartConfig(
        String dir,
        String extension,
        String filePrefix,
        int width,
        int height,
        Path cachePath,
        String fontPath,
        ZoneId timezone,
        Double cloudThreshold
) {
}
