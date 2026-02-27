package com.viking.field_passport_generator.data.aggregator;

import com.viking.field_passport_generator.data.dictionary.TmcDictionary;
import com.viking.field_passport_generator.data.dto.RawOperationData;
import com.viking.field_passport_generator.models.OperationTableRow;
import com.viking.field_passport_generator.models.TmcItem;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class GroupedOperationData {
    private static final ZoneId TIMEZONE = ZoneId.of("Asia/Krasnoyarsk");

    private String operationName;
    private long minStart = Long.MAX_VALUE;
    private long maxEnd = Long.MIN_VALUE;

    private double totalArea;
    private double totalActualArea;
    private double totalFuelLiters;
    private long totalDuration;
    private double speedWeightedSum;
    private long totalSpeedDuration;

    private Map<Long, Double> tmcAmounts = new HashMap<>();

    public void add(RawOperationData data) {
        operationName = data.getOperation();
        totalArea += nullSafe(data.getArea());
        totalActualArea += nullSafe(data.getValidHa());
        totalFuelLiters += nullSafe(data.getFuelC());

        long duration = nullSafeLong(data.getDuration());
        totalDuration += duration;

        double avgSpeed = nullSafe(data.getAvgSpeed());
        if (avgSpeed > 0 && duration > 0) {
            speedWeightedSum += avgSpeed * duration;
            totalSpeedDuration += duration;
        }

        if (data.getStartTime() != null) minStart = Math.min(minStart, data.getStartTime());
        if (data.getEndTime() != null) maxEnd = Math.max(maxEnd, data.getEndTime());

        data.getTmcAmounts().forEach((id, amount) -> tmcAmounts.merge(id, amount, Double::sum));
    }

    public OperationTableRow toTableRow(TmcDictionary dictionary) {
        double finalAvgSpeed = (totalSpeedDuration > 0) ? speedWeightedSum / totalSpeedDuration : 0.0;

        double hours = totalDuration / 3600000.0;
        double productivity = (hours > 0) ? totalActualArea / hours : 0.0;

        List<TmcItem> tmcItems = tmcAmounts.entrySet().stream()
                .map(e -> dictionary.createTmcItem(e.getKey(), e.getValue()))
                .flatMap(Optional::stream)
                .toList();


        return new OperationTableRow(
            operationName,
            LocalDateTime.ofInstant(Instant.ofEpochMilli(minStart), TIMEZONE),
            LocalDateTime.ofInstant(Instant.ofEpochMilli(maxEnd), TIMEZONE),
            totalArea,
            totalActualArea,
            totalFuelLiters,
            Duration.ofMillis(totalDuration),
            productivity,
            finalAvgSpeed,
            tmcItems
        );
    }

    private double nullSafe(Double value) { return value != null ? value : 0.0; }
    private long nullSafeLong(Long value) { return value != null ? value : 0L; }
}
