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
) {
    public Path getNotesPath() {
        return cacheBaseDir.resolve(notesDir);
    }

    public Path getChartsPath() {
        return cacheBaseDir.resolve(chartsDir);
    }

    public Path getFieldDir(String fieldId) {
        return cacheBaseDir.resolve(fieldId);
    }
}
