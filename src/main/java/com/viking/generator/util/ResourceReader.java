package com.viking.generator.util;

import java.io.InputStream;
import java.util.Objects;


public final class ResourceReader {

    private ResourceReader() { throw new UnsupportedOperationException("Utility class"); }

    @SuppressWarnings("resource")
    public static InputStream readAsStream(String path) {
        InputStream is = ResourceReader.class.getClassLoader().getResourceAsStream(path);
        return Objects.requireNonNull(is, () -> "Required resource not found: " + path);
    }
}
