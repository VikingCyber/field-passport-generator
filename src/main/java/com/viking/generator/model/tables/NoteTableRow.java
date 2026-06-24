package com.viking.generator.model.tables;

import java.time.LocalDateTime;

public record NoteTableRow(
        String index,
        String title,
        String text,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
