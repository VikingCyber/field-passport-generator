package com.viking.field_passport_generator.model.tables;

import com.viking.field_passport_generator.model.common.MachineResource;

import java.util.Objects;

public record TechJournalTableRow(
        MachineResource resource,
        String fieldTools,
        String driver,
        String period
) {

    public TechJournalTableRow {
        Objects.requireNonNull(resource, "Resource from dictionary cannot be null");

        fieldTools = Objects.requireNonNullElse(fieldTools, "").trim();
        driver = Objects.requireNonNullElse(driver, "").trim();
        period = Objects.requireNonNullElse(period, "").trim();
    }

    public String getFullEquipmentName() {
        return fieldTools.isEmpty()
                ? resource.getFullTitle()
                : resource.getFullTitle() + " — " + fieldTools;
    }
}
