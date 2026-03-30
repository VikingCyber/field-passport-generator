package com.viking.field_passport_generator.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YearUtils {
    private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})");

    public static String extractYear(String text) {
        if (text == null || text.isBlank()) return "";
        Matcher matcher = YEAR_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }
}
