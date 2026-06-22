package com.viking.generator.config;

import java.nio.file.Path;

public record NoteConfig(
        String dir,
        String extension,
        Path cachePath
) {
}
