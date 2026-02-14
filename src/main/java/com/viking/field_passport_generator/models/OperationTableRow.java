package com.viking.field_passport_generator.models;

import java.time.Duration;
import java.time.LocalDateTime;

public record OperationTableRow(
    String operationName,
    LocalDateTime start,
    LocalDateTime end,
    double measuredArea,
    double actualArea,
    double fuelCost,
    Duration workDuration,
    double productivity,
    double averageSpeed
) {
    
}
