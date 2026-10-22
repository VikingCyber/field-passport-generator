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
    @DisplayName("Заметки сортируются по убыванию даты создания, при равенстве — по убыванию даты обновления")
    void shouldSortNewestFirstThenByUpdatedAtDesc() {
        // --- ARRANGE ---
        OffsetDateTime base = OffsetDateTime.of(2025, 6, 1, 12, 0, 0, 0, ZoneOffset.ofHours(3));
        RawNote newest = new RawNote("Осмотр 21 июня", "позже всех", List.of(), List.of("1"),
                base.plusDays(20), base.plusDays(21));
        RawNote sameDayUpdatedNewer = new RawNote("Осмотр 1 июня, обновлён", "обновлён позже",
                List.of(), List.of("2"), base, base.plusHours(5));
        RawNote sameDayUpdatedOlder = new RawNote("Осмотр 1 июня", "без позднего обновления",
                List.of(), List.of("3"), base, base.plusHours(1));
        RawNote nullUpdated = new RawNote("Осмотр 27 мая", "без даты обновления",
                List.of(), List.of("4"), base.minusDays(5), null);
        NoteMapper mapper = new NoteMapper(TIMEZONE);

        // --- ACT ---
        NoteSection section = mapper.map(List.of(nullUpdated, sameDayUpdatedOlder, newest, sameDayUpdatedNewer), "2025");

        // --- ASSERT ---
        List<String> titles = section.notes().stream().map(NoteTableRow::title).toList();
        assertEquals(
                List.of("Осмотр 21 июня", "Осмотр 1 июня, обновлён", "Осмотр 1 июня", "Осмотр 27 мая"),
                titles,
                "Порядок: сначала свежие createdAt; при равном createdAt — недавно изменённый сверху; null updatedAt в конец");
    }

    @Test
    @DisplayName("Заметка с null в updatedAt не должна вызывать NPE")
    void shouldNotThrowOnNullUpdatedAt() {
        // --- ARRANGE ---
        OffsetDateTime createdAt = OffsetDateTime.of(2025, 6, 1, 12, 0, 0, 0, ZoneOffset.ofHours(3));
        RawNote nullUpdated = new RawNote("Заголовок", "текст", List.of(), List.of("42"), createdAt, null);
        NoteMapper mapper = new NoteMapper(TIMEZONE);

        // --- ACT / ASSERT ---
        NoteSection section = assertDoesNotThrow(() -> mapper.map(List.of(nullUpdated), "2025"));
        assertEquals(1, section.notes().size());
        assertNull(section.notes().get(0).updatedAt(), "updatedAt остаётся null, паспорт собирается без NPE");
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