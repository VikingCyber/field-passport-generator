package com.viking.field_passport_generator.data.dictionary;

import com.viking.field_passport_generator.model.MachineResource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MachineDictionary {
    private final Map<Long, MachineResource> dictionary;
    private static final String NOT_AVAILABLE = "Техника не определена";

    public MachineDictionary(List<MachineResource> machines) {
        this.dictionary = machines.stream()
                .map(this::normalize)
                .collect(Collectors.toMap(
                        MachineResource::id,
                        machine -> machine,
                        (existing, replacement) -> existing
                ));
    }

    public String getMachineFullTitle(Long id) {
        MachineResource machine = dictionary.get(id);
        if (machine == null) return "";

        String inv = machine.inventoryNumber();

        String cleanNum = Arrays.stream(machine.number().split(" "))
                .filter(word -> !word.equalsIgnoreCase(inv))
                .collect(Collectors.joining(" "));

        return Stream.of(machine.unitTypeName(), getBestModelName(machine), cleanNum, inv)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(" "));

    }

    private MachineResource normalize(MachineResource machine) {
        return new MachineResource(
                machine.id(),
                clean(machine.unitTypeName()),
                clean(machine.unitModelName()),
                clean(machine.model()),
                clean(machine.number()),
                clean(machine.inventoryNumber())
        );
    }

    private String getBestModelName(MachineResource machine) {
        return machine.unitModelName().isEmpty() ? machine.model() : machine.unitModelName();
    }

    public String clean(String s) {
        if (s == null || s.isBlank() || s.equalsIgnoreCase("null")) {
            return "";
        }
        return s.trim().replaceAll("\\s+", " ");
    }

    public MachineResource getById(Long id) {
        if (id == null) {
            return MachineResource.unknown(0L);
        }
        return dictionary.getOrDefault(id, MachineResource.unknown(id));
    }


}
