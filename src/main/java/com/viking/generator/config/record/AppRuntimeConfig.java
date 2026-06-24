package com.viking.generator.config.record;

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

