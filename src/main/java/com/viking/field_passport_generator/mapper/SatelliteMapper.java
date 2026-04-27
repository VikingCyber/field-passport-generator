package com.viking.field_passport_generator.mapper;

import com.viking.field_passport_generator.model.OperationTableRow;
import com.viking.field_passport_generator.model.SatelliteImage;
import com.viking.field_passport_generator.model.SpectralIndex;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SatelliteMapper {
    private final Set<SpectralIndex> indices;
    public SatelliteMapper(Set<SpectralIndex> indices) {
        this.indices = indices;
    }

    public List<SatelliteImage> map(Long fieldId, List<OperationTableRow> rows) {
        List<SatelliteImage> result = new ArrayList<>();

        for (OperationTableRow row : rows) {
            String name = row.operationName().toLowerCase();
            LocalDate date = row.end().toLocalDate();

            if (name.contains("дискование"))  {
                addImages(result, fieldId, date, "Дискование первичное");
            }

            if (name.contains("посев")) {
                addImages(result, fieldId, date.plusDays(30), "30 дней после посева");
            }

            if (name.contains("сзр")) {
                addImages(result, fieldId, date.plusDays(3), "3 дня после обработки СЗР");
                addImages(result, fieldId, date.plusDays(14), "14 дней после обработки СЗР");
                addImages(result, fieldId, date.plusDays(45), "45 дней после обработки СЗР");
            }
        }
        return result;
    }

    private void addImages(List<SatelliteImage> result, Long fieldId, LocalDate date, String description) {
        for (SpectralIndex idx : indices) {
            result.add(new SatelliteImage(fieldId, date, description, idx));
        }
    }
}
