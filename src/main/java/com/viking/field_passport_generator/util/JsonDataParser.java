package com.viking.field_passport_generator.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class JsonDataParser {
    private final ObjectMapper mapper;

    public JsonDataParser() {
        this.mapper = JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false)
                .addModule(new JavaTimeModule())
                .build();
    }

    public <T> T parse(InputStream is, Class<T> clazz) {
        Objects.requireNonNull(is, "InputStream cannot be null");
        try {
            return mapper.readValue(is, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing JSON into " + clazz.getSimpleName(), e);
        }
    }

    public <T> T parse(Path path, Class<T> clazz) {
        try {
            return mapper.readValue(path.toFile(), clazz);
        } catch (Exception e) {
            throw new RuntimeException("Error reading JSON file: " + path, e);
        }
    }

    public void write(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            mapper.writeValue(path.toFile(), value);
        } catch (Exception e) {
            throw new RuntimeException("Error writing JSON file: " + path, e);
        }
    }
}
