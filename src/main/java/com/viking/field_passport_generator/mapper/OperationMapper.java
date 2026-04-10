package com.viking.field_passport_generator.mapper;

import com.viking.field_passport_generator.data.aggregator.OperationAccumulator;
import com.viking.field_passport_generator.data.dictionary.TmcDictionary;
import com.viking.field_passport_generator.data.dto.RawOperationData;
import com.viking.field_passport_generator.model.OperationTableRow;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class OperationMapper {
    private final ZoneId timezone;
    private final long aggregationThresholdMs;

    public OperationMapper(ZoneId timezone, Duration threshold) {
        this.timezone = Objects.requireNonNull(timezone, "Timezone must not be null");
        this.aggregationThresholdMs = Objects.requireNonNull(threshold, "Threshold must not be null").toMillis();
    }

    public List<OperationTableRow> mapToTableRow(
            List<RawOperationData> rawData,
            TmcDictionary tmcDictionary) {

        List<OperationAccumulator> groups = groupByOperation(rawData);

        return groups.stream()
                .map(g -> g.toTableRow(tmcDictionary))
                .sorted(Comparator.comparing(OperationTableRow::start).reversed())
                .collect(Collectors.toList());

    }

    private List<OperationAccumulator> groupByOperation(List<RawOperationData> sortedData) {
        List<OperationAccumulator> groups = new ArrayList<>();

        if (sortedData == null || sortedData.isEmpty()) {
            return groups;
        }

        OperationAccumulator current = new OperationAccumulator(timezone);
        current.add(sortedData.getFirst());

        for (int i = 1; i < sortedData.size(); i++) {
            RawOperationData curr = sortedData.get(i);
            RawOperationData prev = sortedData.get(i - 1);

            boolean sameOp = curr.getOperation().equals(prev.getOperation());
            long timeGap = curr.getStartTime() - prev.getEndTime();
            boolean withinWindow = timeGap <= aggregationThresholdMs;

            if (sameOp && withinWindow) {
                current.add(curr);
            } else {
                groups.add(current);
                current = new OperationAccumulator(timezone);
                current.add(curr);
            }
        }
        groups.add(current);
        return groups;
    }
}
