package com.viking.generator.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YearUtils {
    private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})");
    private static final String UNKNOWN_STRING = "Unknown";

    public static int extractYear(String text) {
        if (text == null || text.isBlank()) return -1;
        Matcher matcher = YEAR_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }
}
