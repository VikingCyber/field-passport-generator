package com.viking.field_passport_generator.data.dto.note;

import java.util.List;

public record AttachmentResponse(
        boolean success,
        List<Data> data
) {
}
