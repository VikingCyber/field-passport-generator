package com.viking.field_passport_generator.config;

import java.nio.file.Path;

public record NoteConfig(
        String dir,
        String extension,
        Path cachePath
) {
}
