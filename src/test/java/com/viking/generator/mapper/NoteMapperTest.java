package com.viking.generator.mapper;

import com.viking.generator.data.dto.RawNote;
import com.viking.generator.model.sections.NoteSection;
import com.viking.generator.model.tables.NoteTableRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NoteMapperTest {

    private static final ZoneId TIMEZONE = ZoneId.of("Europe/Moscow");

    @Test
    @DisplayName("HTML-теги из title и text заметки должны удаляться при маппинге")
    void shouldStripHtmlFromTitleAndText() {
        // --- ARRANGE ---
        OffsetDateTime createdAt = OffsetDateTime.of(2025, 6, 1, 12, 0, 0, 0, ZoneOffset.ofHours(3));
        RawNote rawNote = new RawNote(
                "<b>Важно:</b> осмотр",
                "Поле в порядке.<div><br></div><b>Доза:</b> 5 л/га &nbsp; удобрения",
                List.of("img1"),
                List.of("42"),
                createdAt,
                createdAt.plusHours(1)
        );
        NoteMapper mapper = new NoteMapper(TIMEZONE);

        // --- ACT ---
        NoteSection section = mapper.map(List.of(rawNote), "2025");
        NoteTableRow row = section.notes().getFirst();

        // --- ASSERT ---
        assertEquals("Важно: осмотр", row.title(), "Теги <b> должны быть удалены");
        assertEquals("Поле в порядке. Доза: 5 л/га удобрения", row.text(),
                "Теги <b>/<div>/<br> убираются, &nbsp; превращается в обычный пробел");
    }

    @Test
    @DisplayName("Текст без HTML не должен меняться")
    void shouldKeepPlainTextUnchanged() {
        // --- ARRANGE ---
        OffsetDateTime createdAt = OffsetDateTime.of(2025, 6, 1, 12, 0, 0, 0, ZoneOffset.ofHours(3));
        RawNote rawNote = new RawNote("Проверка всходов", "Все взошло отлично", List.of(), List.of("42"),
                createdAt, createdAt);
        NoteMapper mapper = new NoteMapper(TIMEZONE);

        // --- ACT ---
        NoteSection section = mapper.map(List.of(rawNote), "2025");
        NoteTableRow row = section.notes().getFirst();

        // --- ASSERT ---
        assertEquals("Проверка всходов", row.title());
        assertEquals("Все взошло отлично", row.text());
    }

    @Test
    @DisplayName("Заметки с пустым или отсутствующим текстом не ломаются")
    void shouldHandleNullAndBlankText() {
        // --- ARRANGE ---
        OffsetDateTime createdAt = OffsetDateTime.of(2025, 6, 1, 12, 0, 0, 0, ZoneOffset.ofHours(3));
        RawNote nullText = new RawNote("Заголовок", null, List.of(), List.of("42"), createdAt, createdAt);
        RawNote blankTitle = new RawNote("", "<p>   </p>", List.of(), List.of("42"), createdAt, createdAt);
        NoteMapper mapper = new NoteMapper(TIMEZONE);

        // --- ACT ---
        NoteSection section = mapper.map(List.of(nullText, blankTitle), "2025");

        // --- ASSERT ---
        assertEquals(2, section.notes().size());
        assertEquals("Заголовок", section.notes().get(0).title());
        assertEquals("", section.notes().get(0).text(), "null-текст превращается в пустую строку");
        assertEquals("", section.notes().get(1).text(), "Текст из одних пробелов/тегов тоже пустой");
    }
}