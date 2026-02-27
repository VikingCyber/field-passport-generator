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
import java.util.stream.Collectors;


import com.viking.field_passport_generator.data.aggregator.GroupedOperationData;
import com.viking.field_passport_generator.data.dictionary.TmcDictionary;
import com.viking.field_passport_generator.data.dto.RawOperationData;
import com.viking.field_passport_generator.models.OperationTableRow;

public class OperationDataMapper {
    private static final ZoneId TIMEZONE = ZoneId.of("Asia/Krasnoyarsk");
    private static final long AGGREGATION_THRESHOLD_TIME = 48L * 60 * 60 * 1000;

    public List<OperationTableRow> mapToTableRow(
            List<RawOperationData> rawData,
            String targetFieldName,
            String passportYear,
            TmcDictionary tmcDictionary) {

        int targetYear = Integer.parseInt(passportYear);
        List<RawOperationData> filtered = rawData.stream()
                .filter(RawOperationData::isValid)
                .filter(r -> r.getArea() != null && r.getArea() > 0)
                .filter(r -> targetFieldName.equals(r.getGeoZone()))
                .filter(r -> {ZonedDateTime zdt = ZonedDateTime.ofInstant(
                        Instant.ofEpochMilli(r.getStartTime()), TIMEZONE);
                    return zdt.getYear() == targetYear;
                })
                .sorted(Comparator.comparing(RawOperationData::getOperation)
                        .thenComparing(RawOperationData::getStartTime))
                .toList();

        if (filtered.isEmpty()) return Collections.emptyList();

        List<GroupedOperationData> groups = groupByOperation(filtered);

        return groups.stream()
                .map(g -> g.toTableRow(tmcDictionary))
                .sorted(Comparator.comparing(OperationTableRow::start).reversed())
                .collect(Collectors.toList());

    }

    private List<GroupedOperationData> groupByOperation(List<RawOperationData> sortedData) {
        List<GroupedOperationData> groups = new ArrayList<>();
        GroupedOperationData current = new GroupedOperationData();
        current.add(sortedData.getFirst());

        for (int i = 1; i < sortedData.size(); i++) {
            RawOperationData curr = sortedData.get(i);
            RawOperationData prev = sortedData.get(i - 1);

            boolean sameOp = curr.getOperation().equals(prev.getOperation());
            long timeGap = curr.getStartTime() - prev.getEndTime();
            boolean withinWindow = timeGap <= AGGREGATION_THRESHOLD_TIME;

            if (sameOp && withinWindow) {
                current.add(curr);
            } else {
                groups.add(current);
                current = new GroupedOperationData();
                current.add(curr);
            }
        }
        groups.add(current);
        return groups;
    }
}
