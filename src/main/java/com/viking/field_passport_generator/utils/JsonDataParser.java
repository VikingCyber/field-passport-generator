package com.viking.field_passport_generator.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.Objects;

public class JsonDataParser {
    private final ObjectMapper mapper;

    public JsonDataParser() {
        this.mapper = JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
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
}
