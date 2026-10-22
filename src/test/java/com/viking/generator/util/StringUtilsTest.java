package com.viking.generator.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StringUtilsTest {

    @Test
    @DisplayName("sanitizeFileName заменяет каждый небезопасный символ подчёркиванием")
    void sanitizeFileNameReplacesUnsafeChars() {
        assertEquals("Поле__1", StringUtils.sanitizeFileName("Поле №1"),
                "Пробел и № заменяются подчёркиваниями (по одному на символ)");
        assertEquals("ТК02_02", StringUtils.sanitizeFileName("ТК02-02"),
                "Дефис тоже не буква и не цифра — заменяется подчёркиванием");
        assertEquals("___", StringUtils.sanitizeFileName("!!!"),
                "Каждый небезопасный символ становится подчёркиванием");
    }

    @Test
    @DisplayName("sanitizeFileName сохраняет буквы, цифры и уже имеющиеся подчёркивания")
    void sanitizeFileNameKeepsLettersAndUnderscores() {
        assertEquals("Поле_Абра", StringUtils.sanitizeFileName("Поле-Абра"));
        assertEquals("Field_A", StringUtils.sanitizeFileName("Field_A"),
                "Подчёркивание заменяется само на себя, т.е. сохраняется");
        assertEquals("Pole_2025", StringUtils.sanitizeFileName("Pole-2025"));
    }
}
