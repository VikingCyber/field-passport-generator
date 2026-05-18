package com.viking.field_passport_generator.model.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viking.field_passport_generator.util.StringUtils;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MachineResource(
        Long id,
        String unitTypeName,
        String model,
        String unitModelName,
        String number,
        String inventoryNumber
) {

    public String modelName() {
        return unitModelName.isBlank() ? model : unitModelName;
    }

    public String stateNumber() {
        return Arrays.stream(number.split(" "))
                .filter(word -> !word.isEmpty() && !word.equalsIgnoreCase(inventoryNumber))
                .distinct()
                .collect(Collectors.joining(" "));
    }

    public static MachineResource unknown(Long id) {
        return new MachineResource(id, "Неизвестная техника", "", "", "", "");
    }

    public String getFullTitle() {
        return Stream.of(unitTypeName, modelName(), stateNumber(), inventoryNumber)
                .map(StringUtils::clean)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "));
    }
}

