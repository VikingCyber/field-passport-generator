package com.viking.field_passport_generator.data.dictionary;

import com.viking.field_passport_generator.model.common.MachineResource;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.viking.field_passport_generator.util.StringUtils.clean;

public class MachineDictionary {
    private final Map<Long, MachineResource> dictionary;

    public MachineDictionary(List<MachineResource> machines) {
        this.dictionary = machines.stream()
                .map(this::normalize)
                .collect(Collectors.toMap(
                        MachineResource::id,
                        machine -> machine,
                        (existing, replacement) -> existing
                ));
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

    public MachineResource getById(Long id) {
        if (id == null) {
            return MachineResource.unknown(0L);
        }
        return dictionary.getOrDefault(id, MachineResource.unknown(id));
    }
}
