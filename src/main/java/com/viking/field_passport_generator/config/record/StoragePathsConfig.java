package com.viking.field_passport_generator.config.record;

import java.nio.file.Path;

public record StoragePathsConfig(
        Path outputDir,
        Path cacheBaseDir,
        Path notesDir,
        Path chartsDir,
        long minFreeSpaceBytes,
        String passportExtension,
        String notesExtension,
        String satelliteExtension,
        String chartExtension,
        String chartPrefix,
        int chartWidth,
        int chartHeight,
        Path chartFontPath
) {}
