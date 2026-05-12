package com.viking.field_passport_generator.config;

import com.viking.field_passport_generator.data.aggregator.FieldDataAggregator;
import com.viking.field_passport_generator.data.dto.satellite.SatelliteCaptureRule;
import com.viking.field_passport_generator.data.provider.DataProvider;
import com.viking.field_passport_generator.data.provider.FileDataProvider;
import com.viking.field_passport_generator.http.InternalHttpClient;
import com.viking.field_passport_generator.http.NoteImageLoader;
import com.viking.field_passport_generator.http.SatelliteImageLoader;
import com.viking.field_passport_generator.http.strategy.ChartStrategy;
import com.viking.field_passport_generator.http.strategy.NoteStrategy;
import com.viking.field_passport_generator.http.strategy.SatelliteStrategy;
import com.viking.field_passport_generator.mapper.*;
import com.viking.field_passport_generator.model.SpectralIndex;
import com.viking.field_passport_generator.service.*;
import com.viking.field_passport_generator.service.orchestration.PassportOrchestrator;
import com.viking.field_passport_generator.util.JsonDataParser;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

public class AppContainer {
    private final DataProvider dataProvider;
    private final ImageSyncService syncService;
    private final PassportOrchestrator orchestrator;

    public AppContainer(AppConfig config) {
        // ===== 1. Utilities =====
        JsonDataParser jsonParser = new JsonDataParser();
        Path cacheDir = Path.of(config.getString("app.cache-dir", "cache/images"));

        // ===== 2. HTTP Clients =====
        InternalHttpClient noteClient = createNoteHttpClient(config);
        InternalHttpClient satelliteClient = createSatelliteHttpClient(config);

        // ===== 3. Image Loading Strategies =====
        NoteStrategy noteStrategy = createNoteStrategy(config, noteClient);
        SatelliteStrategy satelliteStrategy = createSatelliteStrategy(config, satelliteClient, jsonParser);
        ChartStrategy chartStrategy = createChartStrategy(config, jsonParser);

        // ===== 4. Cache & Sync =====
        ImageCacheService imageCache = new ImageCacheService(cacheDir, List.of(noteStrategy, satelliteStrategy, chartStrategy));
        this.syncService = new ImageSyncService(imageCache, jsonParser);

        // ===== 5. Data Aggregation =====
        FieldDataAggregator aggregator = createAggregator(config);
        this.dataProvider = new FileDataProvider(jsonParser, aggregator);

        // ===== 6. PDF Generation =====
        PassportGeneratorService passportGeneratorService = createPdfService(config);

        // ===== 7. Orchestration =====
        int maxConcurrent = config.getInt("app.max-concurrent-tasks", 10);
        this.orchestrator = new PassportOrchestrator(
                this.syncService,
                passportGeneratorService,
                maxConcurrent
        );

    }

    // ========== Factory Methods ==========

    private InternalHttpClient createNoteHttpClient(AppConfig config) {
        return new InternalHttpClient(
                config.getInt("agro.api.notes.max-concurrent-request", 10),
                config.getLong("agro.api.recovery-time-ms", 60_000L),
                config.getLong("agro.api.min-download-size-bytes", 1024L)
        );
    }

    private InternalHttpClient createSatelliteHttpClient(AppConfig config) {
        return new InternalHttpClient(
                config.getInt("agro.api.satellite.max-concurrent-request", 5),
                config.getLong("agro.api.recovery-time-ms", 60_000L),
                config.getLong("agro.api.min-download-size-bytes", 1024L)
        );
    }

    private NoteStrategy createNoteStrategy(AppConfig config, InternalHttpClient client) {
        NoteConfig noteConfig = config.getNoteConfig();
        NoteImageLoader loader = new NoteImageLoader(
                client,
                config.getString("agro.api.base-url"),
                config.getString("agro.api.key"),
                config.getString("agro.api.user-agent"),
                config.getString("agro.api.endpoints.attachments-info")
        );
        return new NoteStrategy(loader, noteConfig);
    }

    private SatelliteStrategy createSatelliteStrategy(AppConfig config, InternalHttpClient client,
                                                      JsonDataParser jsonParser) {
        SatelliteConfig satelliteConfig = config.getSatelliteConfig();
        SatelliteImageLoader loader = new SatelliteImageLoader(
                client,
                config.getString("agro.api.base-url"),
                config.getString("agro.api.key"),
                config.getString("agro.api.user-agent"),
                config.getString("agro.api.endpoints.spectral-indices")
        );
        return new SatelliteStrategy(loader, jsonParser, satelliteConfig);
    }

    private ChartStrategy createChartStrategy(AppConfig config, JsonDataParser jsonParser) {
        ChartConfig chartConfig = config.getChartConfig();
        ChartGenerator chartGenerator = new XChartGeneratorImpl(chartConfig);
        double cloudThreshold = chartConfig.cloudThreshold();
        ChartMapper chartMapper = new ChartMapper(cloudThreshold);
        return new ChartStrategy(chartGenerator, chartConfig, jsonParser, chartMapper);
    }

    private FieldDataAggregator createAggregator(AppConfig config) {
        ZoneId timezone = ZoneId.of(config.getString("app.timezone", "Asia/Krasnoyarsk"));
        Duration threshold = Duration.ofHours(config.getInt("app.threshold-hours", 48));

        OperationMapper opMapper = new OperationMapper(timezone, threshold);
        NoteMapper noteMapper = new NoteMapper(timezone);
        TechJournalMapper techMapper = new TechJournalMapper(config.getString("app.default-empty-label", "—"));

        Set<SpectralIndex> requiredIndices = config.getSpectralIndex("agro.satellite.indices", Set.of(SpectralIndex.NDVI));
        List<SatelliteCaptureRule> captureRules = config.getMappingResult("agro.satellite.mapping");
        SatelliteMapper satelliteMapper = new SatelliteMapper(requiredIndices, captureRules);

        return new FieldDataAggregator(opMapper, noteMapper, techMapper, satelliteMapper, timezone);
    }

    private PassportGeneratorService createPdfService(AppConfig config) {
        Path outputDir = Path.of(config.getString("app.storage.output-dir", "output"));
        long minSpaceBytes = (long) config.getInt("app.min-free-space-mb", 1024) * 1024L * 1024L;
        return new PdfGeneratorService(minSpaceBytes, outputDir);
    }

    // ========== Getters ==========
    public DataProvider getDataProvider() { return dataProvider; }
    public ImageSyncService getSyncService() { return syncService; }
    public PassportOrchestrator getOrchestrator() { return orchestrator; }
}