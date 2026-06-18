package com.viking.field_passport_generator.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.viking.field_passport_generator.config.record.*;
import com.viking.field_passport_generator.data.dto.satellite.SatelliteCaptureRule;
import com.viking.field_passport_generator.model.common.SpectralIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppConfig {
    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    private final JsonNode rootNode;

    private AppRuntimeConfig cachedAppRuntime;
    private StoragePathsConfig cachedStoragePaths;
    private LocalFilesConfig cachedLocalFiles;
    private AgroApiConfig cachedAgroApi;
    private AgroPerformanceConfig cachedAgroPerf;
    private SatelliteConfig cachedSatellite;
    private AgroSyncConfig cachedAgroSync;

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

    public AppRuntimeConfig getAppRuntimeConfig() {
        if (cachedAppRuntime == null) {
            cachedAppRuntime = new AppRuntimeConfig(
              getString(ConfigKeys.App.MODE, "web"),
              getInt(ConfigKeys.App.Server.PORT, 8080),
              getString(ConfigKeys.App.Server.HOST, "0.0.0.0"),
              getTimezone(ConfigKeys.App.Locale.TIMEZONE, ZoneId.of("Asia/Krasnoyarsk")),
              getString(ConfigKeys.App.Locale.DEFAULT_EMPTY_LABEL, "-"),
              getString(ConfigKeys.App.Locale.EQUIPMENT_SEPARATOR, "-"),
              getInt(ConfigKeys.App.Performance.MAX_CONCURRENT_TASKS, 10),
                Duration.ofHours(getInt(ConfigKeys.App.Performance.AGGREGATION_THRESHOLD_HOURS, 48))
            );
        }
        return cachedAppRuntime;
    }

    public StoragePathsConfig getStoragePathsConfig() {
        if (cachedStoragePaths == null) {
            Path cacheBase = getPath(ConfigKeys.App.Storage.CACHE_BASE_DIR, "cache/images");

            long minFreeSpaceBytes = (long) getInt(ConfigKeys.App.Storage.MIN_FREE_SPACE_MB, 1024) * 1024L * 1024L;

            cachedStoragePaths = new StoragePathsConfig(
                    getPath(ConfigKeys.App.Storage.OUTPUT_DIR, "output"),
                    cacheBase,
                    getPath(ConfigKeys.App.Storage.Notes.DIR, cacheBase.resolve("notes").toString()),
                    getPath(ConfigKeys.App.Storage.Charts.DIR, cacheBase.resolve("charts").toString()),
                    minFreeSpaceBytes,
                    getString(ConfigKeys.App.Storage.PASSPORT_EXTENSION, "pdf"),
                    getString(ConfigKeys.App.Storage.Notes.EXTENSION, "jpg"),
                    getString(ConfigKeys.App.Storage.Satellite.EXTENSION, "png"),
                    getString(ConfigKeys.App.Storage.Charts.EXTENSION, "png"),
                    getString(ConfigKeys.App.Storage.Charts.PREFIX, "chart_"),
                    getInt(ConfigKeys.App.Storage.Charts.WIDTH, 2000),
                    getInt(ConfigKeys.App.Storage.Charts.HEIGHT, 1200),
                    getPath(ConfigKeys.App.Storage.Charts.FONT_PATH, "fonts/NotoSans-Regular.ttf")
            );
        }
        return cachedStoragePaths;
    }

    public LocalFilesConfig getLocalFilesConfig() {
        if (cachedLocalFiles == null) {
            cachedLocalFiles = new LocalFilesConfig(
                    getPath(ConfigKeys.App.LocalFiles.FIELD_DATA, "data/fieldData.json"),
                    getPath(ConfigKeys.App.LocalFiles.OPERATIONS, "data/operationsData.json"),
                    getPath(ConfigKeys.App.LocalFiles.TMC, "data/tmc.json"),
                    getPath(ConfigKeys.App.LocalFiles.NOTES, "data/notesData.json"),
                    getPath(ConfigKeys.App.LocalFiles.UNITS, "data/units.json")
            );
        }
        return cachedLocalFiles;
    }

    public AgroApiConfig getAgroApiConfig() {
        if (cachedAgroApi == null) {
            cachedAgroApi = new AgroApiConfig(
                    getString(ConfigKeys.Agro.Api.KEY, ""),
                    getString(ConfigKeys.Agro.Api.BASE_URL, "https://mir.agrosignal.com/"),
                    getString(ConfigKeys.Agro.Api.USER_AGENT, "Mozilla/5.0"),
                    getLong(ConfigKeys.Agro.Api.MIN_DOWNLOAD_SIZE_BYTES, 1024),
                    getLong(ConfigKeys.Agro.Api.RECOVERY_TIME_MS, 60000),
                    getString(ConfigKeys.Agro.Api.Endpoints.ATTACHMENTS_INFO, "storage/files"),
                    getString(ConfigKeys.Agro.Api.Endpoints.SPECTRAL_INDICES, "spectralIndices"),
                    getString(ConfigKeys.Agro.Api.Endpoints.FIELD_REPORT, "data/reportData"),
                    getString(ConfigKeys.Agro.Api.Endpoints.TMC, "goods"),
                    getString(ConfigKeys.Agro.Api.Endpoints.UNITS, "units"),
                    getString(ConfigKeys.Agro.Api.Endpoints.NOTES, "notes")
            );
        }
        return cachedAgroApi;
    }

    public AgroPerformanceConfig getAgroPerformanceConfig() {
        if (cachedAgroPerf == null) {
            cachedAgroPerf = new AgroPerformanceConfig(
                    getInt(ConfigKeys.Agro.Notes.MAX_CONCURRENT_REQUESTS, 10),
                    getInt(ConfigKeys.Agro.Satellite.MAX_CONCURRENT_REQUESTS, 5)
            );
        }
        return cachedAgroPerf;
    }

    public SatelliteConfig getSatelliteConfig() {
        if (cachedSatellite == null) {
            cachedSatellite = new SatelliteConfig(
                    getDouble(ConfigKeys.Agro.Satellite.CLOUD_THRESHOLD, 0.8),
                    getDouble(ConfigKeys.Agro.Satellite.CLOUD_WEIGHT_FACTOR, 0.5),
                    getInt(ConfigKeys.Agro.Satellite.SCAN_WINDOW_DAYS, 7),
                    getString(ConfigKeys.Agro.Satellite.FROM_DATE, "20240101"),
                    getString(ConfigKeys.Agro.Satellite.TO_DATE, "20261231"),
                    getSpectralIndex(ConfigKeys.Agro.Satellite.INDICES, Set.of(SpectralIndex.NDVI)),
                    getMappingResult(ConfigKeys.Agro.Satellite.MAPPING)
            );
        }
        return cachedSatellite;
    }

    public AgroSyncConfig getAgroSyncConfig() {
        if (cachedAgroSync == null) {
            cachedAgroSync = new AgroSyncConfig(
                    getString(ConfigKeys.Agro.Sync.FROM_DATE, "2023-01-01T00:00:00Z"),
                    getString(ConfigKeys.Agro.Sync.TO_DATE, "2027-01-01T00:00:00Z"),
                    getLong(ConfigKeys.Agro.Sync.COMPANY_ID, 390557L)
            );
        }
        return cachedAgroSync;
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
        Path path = Path.of(val);
        log.info("Загрузка пути для ключа '{}': значение '{}' (существует: {})",
                key, val, Files.exists(path));
        return path;
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
}
