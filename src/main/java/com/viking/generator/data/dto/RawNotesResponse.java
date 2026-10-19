package com.viking.generator.data.dto;

import java.util.List;

public record RawNotesResponse(
        List<RawNote> data,
        Boolean success,
        Integer total
) {
}
