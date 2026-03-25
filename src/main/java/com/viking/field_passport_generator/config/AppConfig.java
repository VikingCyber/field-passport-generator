package com.viking.field_passport_generator.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

public class AppConfig {
    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    private final JsonNode rootNode;

    public AppConfig() {
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            if (is == null) throw new RuntimeException("application.yml not found");
            this.rootNode = objectMapper.readTree(is);
        } catch (IOException e) {
            throw new RuntimeException("Error loading YAML config", e);
        }
    }

    private JsonNode findNode(String key) {
        String pointer = "/" + key.replace(".", "/");
        return rootNode.at(pointer);
    }

    public String getString(String key) {
        String envValue = System.getenv(key.replace(".", "-").toUpperCase());
        if (envValue != null) return envValue;

        JsonNode node = findNode(key);
        return (node.isMissingNode() || node.isNull() ? null : node.asText());
    }

    public String getString(String key, String defaultValue) {
        String value = getString(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    public int getInt(String key, int defaultValue) {
        JsonNode node = findNode(key);
        if (node.isNumber()) return node.asInt();

        String val = getString(key);
        try {
            return val!= null ? Integer.parseInt(val.trim()) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        JsonNode node = findNode(key);
        if (node.isBoolean()) return node.asBoolean();

        String val = getString(key);
        return val != null ? Boolean.parseBoolean(val.trim()) : defaultValue;
    }
}
