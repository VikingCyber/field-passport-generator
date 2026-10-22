package com.viking.generator.config.record;

public record AgroPerformanceConfig(
        int notesMaxConcurrent,
        int satelliteMaxConcurrent,
        int metaMaxConcurrent
) {}
