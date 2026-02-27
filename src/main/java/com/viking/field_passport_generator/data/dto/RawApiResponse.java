package com.viking.field_passport_generator.data.dto;

import java.util.List;

public record RawApiResponse(
    List<RawFieldData> data
) {}
