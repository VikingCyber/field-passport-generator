package com.viking.field_passport_generator.config;

import com.viking.field_passport_generator.data.aggregator.FieldDataAggregator;
import com.viking.field_passport_generator.data.dto.satellite.SatelliteCaptureRule;
import com.viking.field_passport_generator.data.provider.DataProvider;
import com.viking.field_passport_generator.data.provider.FileDataProvider;
import com.viking.field_passport_generator.http.InternalHttpClient;
import com.viking.field_passport_generator.http.NoteImageLoader;
import com.viking.field_passport_generator.http.SatelliteImageLoader;
import com.viking.field_passport_generator.mapper.NoteMapper;
import com.viking.field_passport_generator.mapper.OperationMapper;
import com.viking.field_passport_generator.mapper.SatelliteMapper;
import com.viking.field_passport_generator.mapper.TechJournalMapper;
import com.viking.field_passport_generator.model.SpectralIndex;
import com.viking.field_passport_generator.service.ImageCacheService;
import com.viking.field_passport_generator.service.ImageSyncService;
import com.viking.field_passport_generator.service.PassportGeneratorService;
import com.viking.field_passport_generator.service.PdfGeneratorService;
import com.viking.field_passport_generator.util.JsonDataParser;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

public class AppContainer {
    // Services ready for operation (getters provided below)
    private final DataProvider dataProvider;
    private final PassportGeneratorService passportGeneratorService;
    private final ImageSyncService syncService;

    public AppContainer(AppConfig config) {
        // --- 1. Infrastructure Setup ---
        // Initialize common utilities used across different services
        JsonDataParser jsonParser = new JsonDataParser();
        InternalHttpClient noteClient = new InternalHttpClient(10, 60_000L);
        InternalHttpClient satelliteClient = new InternalHttpClient(5, 60_000L);

        // --- 2. Image Processing Layer ---
        // Configure image loading, caching, and synchronization
        NoteImageLoader noteImageLoader = new NoteImageLoader(
                noteClient,
                config.getString("agro.api.base-url"),
                config.getString("agro.api.key"),
                config.getString("agro.api.user-agent"),
                config.getString("agro.api.endpoints.attachments-info")
        );

        SatelliteImageLoader satelliteLoader = new SatelliteImageLoader(
                satelliteClient,
                config.getString("agro.api.base-url"),
                config.getString("agro.api.key"),
                config.getString("agro.api.user-agent"),
                config.getString("agro.api.endpoints.spectral-indices")
        );

        Path cacheDir = Path.of(config.getString("app.cache-dir", "cache"));
        String fromDate = config.getString("agro.satellite.fromDate", "20240101");
        String toDate = config.getString("agro.satellite.toDate", "20261231");
        ImageCacheService noteCache = new ImageCacheService(noteImageLoader, satelliteLoader, cacheDir, fromDate, toDate);
        this.syncService = new ImageSyncService(noteCache, jsonParser);

        // --- 3. Data Processing Layer (Mappers & Aggregators) ---
        // Convert raw configuration strings into typed domain objects
        ZoneId timezone = ZoneId.of(config.getString("app.timezone", "Asia/Krasnoyarsk"));
        Duration threshold = Duration.ofHours(config.getInt("app.threshold-hours", 48));

        OperationMapper opMapper = new OperationMapper(timezone, threshold);
        NoteMapper noteMapper = new NoteMapper(timezone);
        TechJournalMapper techMapper = new TechJournalMapper(config.getString("app.default-empty-label", "—"));
        Set<SpectralIndex> requiredIndices = config.getSpectralIndex("agro.satellite.indices", Set.of(SpectralIndex.NDVI));
        List<SatelliteCaptureRule> captureRules = config.getMappingResult("agro.satellite.mapping");
        SatelliteMapper satelliteMapper = new SatelliteMapper(requiredIndices, captureRules);
        FieldDataAggregator aggregator = new FieldDataAggregator(opMapper, noteMapper, techMapper, satelliteMapper,
                timezone);

        // DataProvider handles the retrieval and aggregation of field data
        this.dataProvider = new FileDataProvider(jsonParser, aggregator);

        // --- 4. PDF Generation Layer ---
        // Prepare storage paths and safety thresholds for document generation
        Path outputDir = Path.of(config.getString("app.storage.output-dir", "output"));

        // Convert Megabytes from config to Bytes for internal safety checks
        long minSpaceBytes = (long) config.getInt("app.min-free-space-mb", 1024) * 1024L * 1024L;

        this.passportGeneratorService = new PdfGeneratorService(
                noteCache::getImageBytes, // Functional interface for decoupled image retrieval
                minSpaceBytes,
                outputDir
        );
    }

    // Getters for Main entry point
    public DataProvider getDataProvider() { return dataProvider; }
    public PassportGeneratorService getPassportGeneratorService() { return passportGeneratorService; }
    public ImageSyncService getSyncService() { return syncService; }
}