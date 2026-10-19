package com.viking.generator.data.dto.note;

import java.util.List;

public record AttachmentResponse(
        boolean success,
        List<Data> data
) {
}
