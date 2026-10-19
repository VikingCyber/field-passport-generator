package com.viking.generator.data.dto;

import com.viking.generator.model.common.MachineResource;

import java.util.List;

public record RawMachineResponse(
        List<MachineResource> data
) {
}
