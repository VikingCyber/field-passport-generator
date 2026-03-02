package com.viking.field_passport_generator.utils;

import java.io.InputStream;
import java.util.Objects;


@SuppressWarnings("resource")
public class ResourceReader {
    public InputStream readAsStream(String path) {
        InputStream is = getClass().getClassLoader().getResourceAsStream(path);
        return Objects.requireNonNull(is, () -> "Required resource file not found: " + path);
    }
}
