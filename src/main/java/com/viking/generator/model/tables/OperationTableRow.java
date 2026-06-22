package com.viking.generator.model.tables;

import com.viking.generator.model.common.TmcItem;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public record OperationTableRow(
    String operationName,
    LocalDateTime start,
    LocalDateTime end,
    double measuredArea,
    double actualArea,
    double fuelCost,
    Duration workDuration,
    double productivity,
    double averageSpeed,
    List<TmcItem> tmcItemList
) {
    public boolean hasTmcList() {
        return tmcItemList != null && !tmcItemList.isEmpty();
    }
}
