package com.viking.field_passport_generator.config;

import com.viking.field_passport_generator.data.aggregator.FieldDataAggregator;
import com.viking.field_passport_generator.data.provider.DataProvider;
import com.viking.field_passport_generator.data.provider.FileDataProvider;
import com.viking.field_passport_generator.http.ImageLoader;
import com.viking.field_passport_generator.mapper.NoteMapper;
import com.viking.field_passport_generator.mapper.OperationMapper;
import com.viking.field_passport_generator.mapper.TechJournalMapper;
import com.viking.field_passport_generator.service.ImageCacheService;
import com.viking.field_passport_generator.service.ImageSyncService;
import com.viking.field_passport_generator.service.PassportGeneratorService;
import com.viking.field_passport_generator.service.PdfGeneratorService;
import com.viking.field_passport_generator.util.JsonDataParser;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;

public class AppContainer {
    // Services ready for operation (getters provided below)
    private final DataProvider dataProvider;
    private final PassportGeneratorService passportGeneratorService;
    private final ImageSyncService syncService;

    public AppContainer(AppConfig config) {
        // --- 1. Infrastructure Setup ---
        // Initialize common utilities used across different services
        JsonDataParser jsonParser = new JsonDataParser();
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // --- 2. Image Processing Layer ---
        // Configure image loading, caching, and synchronization
        ImageLoader imageLoader = new ImageLoader(
                httpClient,
                config.getString("agro.api.base-url"),
                config.getString("agro.api.key"),
                config.getString("agro.api.user-agent"),
                config.getString("agro.api.endpoints.attachments-info")
        );

        Path cacheDir = Path.of(config.getString("app.cache-dir", "cache"));
        ImageCacheService cacheService = new ImageCacheService(imageLoader, cacheDir);
        this.syncService = new ImageSyncService(cacheService, jsonParser);

        // --- 3. Data Processing Layer (Mappers & Aggregators) ---
        // Convert raw configuration strings into typed domain objects
        ZoneId timezone = ZoneId.of(config.getString("app.timezone", "Asia/Krasnoyarsk"));
        Duration threshold = Duration.ofHours(config.getInt("app.threshold-hours", 48));

        OperationMapper opMapper = new OperationMapper(timezone, threshold);
        NoteMapper noteMapper = new NoteMapper(timezone);
        TechJournalMapper techMapper = new TechJournalMapper(config.getString("app.default-empty-label", "—"));
        FieldDataAggregator aggregator = new FieldDataAggregator(opMapper, noteMapper, techMapper, timezone);

        // DataProvider handles the retrieval and aggregation of field data
        this.dataProvider = new FileDataProvider(jsonParser, aggregator);

        // --- 4. PDF Generation Layer ---
        // Prepare storage paths and safety thresholds for document generation
        Path outputDir = Path.of(config.getString("app.storage.output-dir", "output"));

        // Convert Megabytes from config to Bytes for internal safety checks
        long minSpaceBytes = (long) config.getInt("app.min-free-space-mb", 1024) * 1024 * 1024;

        this.passportGeneratorService = new PdfGeneratorService(
                cacheService::getImageBytes, // Functional interface for decoupled image retrieval
                minSpaceBytes,
                outputDir
        );
    }

    // Getters for Main entry point
    public DataProvider getDataProvider() { return dataProvider; }
    public PassportGeneratorService getPassportGeneratorService() { return passportGeneratorService; }
    public ImageSyncService getSyncService() { return syncService; }
}