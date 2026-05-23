package com.viking.field_passport_generator.config.record;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.Duration;

public record AppRuntimeConfig(
        String mode,
        int serverPort,
        String serverHost,
        ZoneId timezone,
        String defaultEmptyLabel,
        String equipmentSeparator,
        int maxConcurrentTasks,
        Duration aggregationThreshold
) {}

