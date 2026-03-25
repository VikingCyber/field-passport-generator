package com.viking.field_passport_generator.model;

import java.time.LocalDateTime;

public record NoteTableRow(
        String index,
        String title,
        String text,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
