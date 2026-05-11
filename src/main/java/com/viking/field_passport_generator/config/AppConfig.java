package com.viking.field_passport_generator.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.viking.field_passport_generator.data.dto.satellite.SatelliteCaptureRule;
import com.viking.field_passport_generator.model.SpectralIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.*;

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
        return node.asText();
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
            return val != null ? Integer.parseInt(val.trim()) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public long getLong(String key, long defaultValue) {
        JsonNode node = findNode(key);
        if (node.isNumber()) return node.asLong();

        String val = getString(key);
        try {
            return val != null ? Long.parseLong(val.trim()) : defaultValue;
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

    public Path getPath(String key, String defaultValue) {
        String val = getString(key, defaultValue);
        return Path.of(val);
    }

    public Set<SpectralIndex> getSpectralIndex(String key, Set<SpectralIndex> defaultValue) {
        JsonNode node = findNode(key);

        if (node.isMissingNode() || !node.isArray()) {
            return defaultValue;
        }

        Set<SpectralIndex> result = new HashSet<>();
        for (JsonNode item : node) {
            try {
                result.add(SpectralIndex.valueOf(item.asText().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown spectral index in config: {}", item.asText());
            }
        }
        return result.isEmpty() ? defaultValue : result;
    }

    public List<SatelliteCaptureRule> getMappingResult(String key) {
        JsonNode node = findNode(key);
        if (node.isMissingNode() || !node.isArray()) {
            return Collections.emptyList();
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readerForListOf(SatelliteCaptureRule.class).readValue(node);
        } catch (IOException e) {
            log.error("Failed to parse mapping rules from config at key : {}", key);
            return Collections.emptyList();
        }
    }

    public double getDouble(String key, double defaultValue) {
        JsonNode node = findNode(key);
        return node.isNumber() ? node.asDouble() : defaultValue;
    }

    public ZoneId getTimezone(String key, ZoneId defaultValue) {
        JsonNode node = findNode(key);
        if (!node.isMissingNode() && node.isTextual() && !node.asText().isBlank()) {
            try {
                return ZoneId.of(node.asText());
            } catch (DateTimeException e) {
                log.warn("Invalid timezone in config {}: {}", key, node.asText());
            }
        }
        return defaultValue;
    }

    public SatelliteConfig getSatelliteConfig() {
        return new SatelliteConfig(
                getPath("agro.cache-dir", "cache/images"),
                getString("agro.satellite.fromDate", "20240101"),
                getString("agro.satellite.toDate", "20260101"),
                getInt("agro.satellite.scan-window-days", 7),
                getDouble("agro.satellite.cloud-threshold", 0.8),
                getDouble("agro.satellite.cloud-weight-factor", 5.0),
                getString("agro.satellite-extension", "png")
        );
    }

    public NoteConfig getNoteConfig() {
        return new NoteConfig(
            getString("app.notes-dir", "notes"),
            getString("app.notes-extension", "jpg"),
            getPath("app.cache-dir", "cache/images")
        );
    }

    public ChartConfig getChartConfig() {
        return new ChartConfig(
                getString("app.cache.charts.dir", "charts"),
                getString("app.cache.charts.default-extension", "png"),
                getString("app.cache.charts.file-prefix", "chart_"),
                getInt("app.cache.charts.width", 800),
                getInt("app.cache.charts.height", 400),
                getPath("app.cache.path", "cache/images"),
                getString("app.cache.charts.font-path", "fonts/NotoSans-Regular.ttf"),
                getTimezone("app.timezone", ZoneId.of("Asia/Krasnoyarsk"))
        );
    }
}
