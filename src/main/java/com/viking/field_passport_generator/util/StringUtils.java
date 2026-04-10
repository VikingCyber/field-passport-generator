package com.viking.field_passport_generator.util;

public final class StringUtils {
    private StringUtils() { throw new UnsupportedOperationException("Utility class"); }

    public static String clean(String s) {
        if (s == null || s.isBlank() || s.equalsIgnoreCase("null")) {
            return "";
        }
        return s.trim().replaceAll("\\s+", " ");
    }

    public static String normalize(String s, String fallback) {
        String cleaned = clean(s);
        return cleaned.isEmpty() ? fallback : cleaned;
    }
}
