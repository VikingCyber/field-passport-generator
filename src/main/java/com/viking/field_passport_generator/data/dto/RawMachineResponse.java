package com.viking.field_passport_generator.data.dto;

import com.viking.field_passport_generator.model.MachineResource;

import java.util.List;

public record RawMachineResponse(
        List<MachineResource> data
) {
}
