package com.viking.generator.data.dto;

import java.util.List;

public record RawApiResponse(
    List<RawFieldData> data
) {}
