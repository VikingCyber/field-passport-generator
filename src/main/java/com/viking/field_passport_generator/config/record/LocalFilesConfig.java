package com.viking.field_passport_generator.config.record;

import java.nio.file.Path;

public record LocalFilesConfig(
        Path fieldDataPath,
        Path operationsPath,
        Path tmcPath,
        Path notesPath,
        Path unitsPath
) {}
