package com.viking.generator.mapper;

import com.viking.generator.data.dto.satellite.SatelliteCaptureRule;
import com.viking.generator.model.tables.OperationTableRow;
import com.viking.generator.model.media.SatelliteImage;
import com.viking.generator.model.common.SpectralIndex;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SatelliteMapper {
    private final Set<SpectralIndex> indices;
    private final List<SatelliteCaptureRule> rules;
    public SatelliteMapper(Set<SpectralIndex> indices, List<SatelliteCaptureRule> rules) {
        this.indices = indices;
        this.rules = rules;
    }

    public List<SatelliteImage> map(Long fieldId, List<OperationTableRow> rows) {
        List<SatelliteImage> result = new ArrayList<>();

        for (OperationTableRow row : rows) {
            String operationName = row.operationName().toLowerCase();
            LocalDate operationEndDate = row.end().toLocalDate();

            for (SatelliteCaptureRule rule : rules) {
                if (operationName.contains(rule.key().toLowerCase())) {
                    for (int i = 0; i < rule.offsets().size(); i++) {
                        int daysOffset = rule.offsets().get(i);
                        String description = rule.labels().get(i);
                        LocalDate targetDate = operationEndDate.plusDays(daysOffset);
                        addImages(result, fieldId, targetDate, description);
                    }
                }
            }
        }
        return result;
    }

    private void addImages(List<SatelliteImage> result, Long fieldId, LocalDate date, String description) {
        for (SpectralIndex idx : indices) {
            result.add(new SatelliteImage(String.valueOf(fieldId), date, description, idx));
        }
    }
}
