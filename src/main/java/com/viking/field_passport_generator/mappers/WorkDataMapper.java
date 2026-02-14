package com.viking.field_passport_generator.mappers;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


import com.viking.field_passport_generator.dto.RawOperationData;
import com.viking.field_passport_generator.models.OperationTableRow;

public class WorkDataMapper {
    private static final ZoneId TIMEZONE = ZoneId.of("Asia/Krasnoyarsk");

    public List<OperationTableRow> mapToTableRow(
        List<RawOperationData> rawData, String targetFieldName, String passportYear
    ) {
        if (rawData == null || rawData.isEmpty()) {
            return Collections.emptyList();
        }
        
        int targetYear = Integer.parseInt(passportYear);
        List<RawOperationData> sortedOperationData = rawData.stream()
            .filter(r -> r.isValid() && r.operation() != null)
            .filter(r -> r.area() > 0)
            .filter(r -> targetFieldName.equals(r.geoZone()))
            .filter(r -> {
                ZonedDateTime zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(r.startTime()), TIMEZONE);
                return zdt.getYear() == targetYear;
            })
            .sorted(Comparator.comparing(RawOperationData::startTime))
            .collect(Collectors.toList());

        if (sortedOperationData.isEmpty()) {
            return Collections.emptyList();
        }

        List<List<RawOperationData>> operationGroups = groupByOperation(sortedOperationData);

        List<OperationTableRow> tableRows = operationGroups.stream()
            .map(this::convertGroupToTableRow)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        Collections.reverse(tableRows);

        return tableRows;
    }

    private List<List<RawOperationData>> groupByOperation(List<RawOperationData> sortedData) {
        List<List<RawOperationData>> groups = new ArrayList<>();
        
        if (sortedData.isEmpty()) {
            throw new IllegalArgumentException("Ошибка список отсортированных операций пришёл пустым!");
        }

        List<RawOperationData> currentGroup = new ArrayList<>();
        String currentOperation = null;

        for (RawOperationData data : sortedData) {
            if (currentGroup.isEmpty() || !data.operation().equals(currentOperation)) {
                if (!currentGroup.isEmpty()) {
                    groups.add(new ArrayList<>(currentGroup));
                    currentGroup.clear();
                }
                currentOperation = data.operation();
            }
            currentGroup.add(data);
        }
        if (!currentGroup.isEmpty()) {
            groups.add(new ArrayList<>(currentGroup));
        }

        return groups;
    }

    private OperationTableRow convertGroupToTableRow(List<RawOperationData> group) {
        if (group == null || group.isEmpty()) {
            throw new IllegalArgumentException("Ошибка конвертации группы операций в строку таблицы!");
        }

        double totalArea = 0.0;
        double totalActualArea = 0.0;
        double totalFuelLiters = 0.0;
        long totalDuration = 0L;
        double speedWeightedSum = 0.0;
        long totalSpeedDuration = 0L;

        long minStart = Long.MAX_VALUE;
        long maxEnd = Long.MIN_VALUE;

        String operationName = group.get(0).operation();
        for (RawOperationData data : group) {
            totalArea += (data.area() != null) ? data.area() : 0.0;
            totalActualArea += (data.validHa() != null) ? data.validHa() : 0.0;
            totalFuelLiters += (data.fuelC() != null) ? data.fuelC() : 0.0;

            long duration = (data.duration() != null) ? data.duration() : 0L;
            totalDuration += duration;

            double avgSpeed = (data.avgSpeed() != null) ? data.avgSpeed() : 0.0;
            if (avgSpeed > 0 && duration > 0) {
                speedWeightedSum += avgSpeed * duration;
                totalSpeedDuration += duration;
            }

            if (data.startTime() != null) minStart = Math.min(minStart, data.startTime());
            if (data.endTime() != null) maxEnd = Math.max(maxEnd, data.endTime());
        }

        double finalAvgSpeed = (totalSpeedDuration > 0) ? speedWeightedSum / totalSpeedDuration : 0.0;

        double hours = totalDuration / 3600000.0;
        double productivity = (hours > 0) ? totalActualArea / hours : 0.0;;

        return new OperationTableRow(
            operationName,
            LocalDateTime.ofInstant(Instant.ofEpochMilli(minStart), TIMEZONE),
            LocalDateTime.ofInstant(Instant.ofEpochMilli(maxEnd), TIMEZONE),
            totalArea,
            totalActualArea,
            totalFuelLiters,
            Duration.ofMillis(totalDuration),
            productivity,
            finalAvgSpeed
        );

    }

}
