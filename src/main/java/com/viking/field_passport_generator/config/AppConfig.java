package com.viking.field_passport_generator.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class AppConfig {
    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    private final JsonNode rootNode;

    public AppConfig() {
        this("application.yml");
    }

    public AppConfig(String fileName) {
        this.rootNode = loadConfiguration(fileName);
    }

    private JsonNode loadConfiguration(String fileName) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Path externalPath = Path.of(fileName);

        if (Files.exists(externalPath) && Files.isRegularFile(externalPath)) {
            try (InputStream is = Files.newInputStream(externalPath)) {
                log.info("Configuration loading from external config file: {}", externalPath.toAbsolutePath());
                return mapper.readTree(is);
            } catch (IOException e) {
                log.error("Failed to read external config file: {}", fileName);
                throw new RuntimeException("External config error", e);
            }
        }

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(fileName)) {
            if (is == null) {
                log.error("Configuration file '{}' not found on disk or in classpath!", fileName);
                throw new RuntimeException("Config not found: " + fileName);
            }
            log.info("Configuration loading from classpath: {}", fileName);
            return mapper.readTree(is);
        } catch (IOException e) {
            log.error("Failed to read internal config file from classpath", e);
            throw new RuntimeException("Internal config error", e);
        }
    }

    private JsonNode findNode(String key) {
        String pointer = "/" + key.replace(".", "/");
        return rootNode.at(pointer);
    }

    public String getString(String key) {
        String envValue = System.getenv(key.replace(".", "_").toUpperCase());
        if (envValue != null && !envValue.isBlank()) return envValue;

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
