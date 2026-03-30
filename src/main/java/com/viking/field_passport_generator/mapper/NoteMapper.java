package com.viking.field_passport_generator.mapper;

import com.viking.field_passport_generator.data.dto.RawNote;
import com.viking.field_passport_generator.model.NoteImage;
import com.viking.field_passport_generator.model.NoteSection;
import com.viking.field_passport_generator.model.NoteTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public class NoteMapper {
    private final ZoneId timezone;
    private static final Logger log = LoggerFactory.getLogger(NoteMapper.class);

    public NoteMapper(ZoneId timezone) {
        this.timezone = Objects.requireNonNull(timezone, "Timezone must not be null");
    }

    public NoteSection map(List<RawNote> rawNotes, String passportYear) {
        if (rawNotes == null || rawNotes.isEmpty()) {
            return new NoteSection(Collections.emptyList(), Collections.emptyList());
        }

        int targetYear = Integer.parseInt(passportYear);
        List<RawNote> filteredNotes = new ArrayList<>();
        for (RawNote note : rawNotes) {
            int noteYear = note.createdAt().atZoneSameInstant(timezone).getYear();
            if (noteYear == targetYear) {
                filteredNotes.add(note);
            }
        }


        if (filteredNotes.isEmpty()) {
            return new NoteSection(Collections.emptyList(), Collections.emptyList());
        }


        filteredNotes.sort(Comparator.comparing(RawNote::createdAt)
                .thenComparing(RawNote::updatedAt).reversed());

        List<NoteTableRow> noteTableRows = new ArrayList<>();
        List<NoteImage> noteImages = new ArrayList<>();

        for (int i = 0; i < filteredNotes.size(); i++) {
            RawNote rawNote = filteredNotes.get(i);
            String noteIndex = String.valueOf(i + 1);

            LocalDateTime localUpdated = null;
            if (rawNote.updatedAt() != null) {
                localUpdated = rawNote.updatedAt().atZoneSameInstant(timezone).toLocalDateTime();
            }

            NoteTableRow noteRow = new NoteTableRow(
                    noteIndex,
                    rawNote.title(),
                    rawNote.text(),
                    rawNote.createdAt().atZoneSameInstant(timezone).toLocalDateTime(),
                    localUpdated
            );
            noteTableRows.add(noteRow);

            if (rawNote.attachments() != null) {
                for (int j = 0; j < rawNote.attachments().size(); j++) {
                    String attachmentId = rawNote.attachments().get(j);
                    noteImages.add(new NoteImage(attachmentId,noteIndex + "." + (j + 1)));
                }
            }
        }
        return new NoteSection(noteTableRows, noteImages);
    }
}
