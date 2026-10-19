package com.viking.generator.data.dto;

import java.util.List;

public record RawOperationsResponse(
    List<RawOperationData> data
) {}
