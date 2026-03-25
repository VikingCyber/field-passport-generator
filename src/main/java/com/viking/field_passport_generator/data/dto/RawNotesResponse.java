package com.viking.field_passport_generator.data.dto;

import java.util.List;

public record RawNotesResponse(
        List<RawNote> data,
        Boolean success,
        Integer total
) {
}
