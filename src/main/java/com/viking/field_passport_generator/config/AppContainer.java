package com.viking.field_passport_generator.config;

import com.viking.field_passport_generator.config.record.*;
import com.viking.field_passport_generator.data.aggregator.FieldDataAggregator;
import com.viking.field_passport_generator.data.provider.DataProvider;
import com.viking.field_passport_generator.data.provider.FileDataProvider;
import com.viking.field_passport_generator.data.provider.InMemoryDataProvider;
import com.viking.field_passport_generator.data.provider.WebDataProvider;
import com.viking.field_passport_generator.http.InternalHttpClient;
import com.viking.field_passport_generator.http.NoteImageLoader;
import com.viking.field_passport_generator.http.SatelliteImageLoader;
import com.viking.field_passport_generator.http.strategy.ChartStrategy;
import com.viking.field_passport_generator.http.strategy.NoteStrategy;
import com.viking.field_passport_generator.http.strategy.SatelliteStrategy;
import com.viking.field_passport_generator.mapper.*;
import com.viking.field_passport_generator.service.*;
import com.viking.field_passport_generator.service.orchestration.PassportOrchestrator;
import com.viking.field_passport_generator.util.JsonDataParser;
import java.util.List;

public class AppContainer {
    private final DataProvider dataProvider;
    private final ImageSyncService syncService;
    private final PassportOrchestrator orchestrator;
    private final PassportGeneratorService pdfService;

    public AppContainer(AppConfig config) {
        // ===== 1. Разворачиваем типизированные рекорды =====
        AppRuntimeConfig runtime = config.getAppRuntimeConfig();
        StoragePathsConfig paths = config.getStoragePathsConfig();
        AgroApiConfig agro = config.getAgroApiConfig();
        AgroPerformanceConfig perf = config.getAgroPerformanceConfig();
        SatelliteConfig satellite = config.getSatelliteConfig();

        JsonDataParser jsonParser = new JsonDataParser();

        // ===== 2. HTTP Clients =====
        InternalHttpClient noteClient = new InternalHttpClient(
                perf.notesMaxConcurrent(),
                agro.recoveryTimeMs(),
                agro.minDownloadSizeBytes()
        );
        InternalHttpClient satelliteClient = new InternalHttpClient(
                perf.satelliteMaxConcurrent(),
                agro.recoveryTimeMs(),
                agro.minDownloadSizeBytes()
        );

        // ===== 3. Image Loading Strategies =====
        NoteStrategy noteStrategy = createNoteStrategy(paths, agro, noteClient);
        SatelliteStrategy satelliteStrategy = createSatelliteStrategy(paths, agro, satellite, satelliteClient, jsonParser);
        ChartStrategy chartStrategy = createChartStrategy(paths, satellite, jsonParser, runtime);

        // ===== 4. Cache & Sync =====
        ImageCacheService imageCache = new ImageCacheService(
                paths.cacheBaseDir(),
                List.of(noteStrategy, satelliteStrategy, chartStrategy)
        );
        this.syncService = new ImageSyncService(imageCache, jsonParser);

        // ===== 5. Data Aggregation & Provider =====
        FieldDataAggregator aggregator = createAggregator(runtime, satellite);

        FileDataProvider fileDataProvider = new FileDataProvider(jsonParser, aggregator);
        InMemoryDataProvider cacheProvider = new InMemoryDataProvider(
                paths.outputDir().toString(),
                paths.passportExtension()
        );
        cacheProvider.refreshFromFiles(fileDataProvider);
        this.dataProvider = cacheProvider;

        // ===== 6. PDF Generation =====
        this.pdfService = new PdfGeneratorService(paths.minFreeSpaceBytes(), paths.outputDir());

        // ===== 7. Orchestration =====
        this.orchestrator = new PassportOrchestrator(
                this.syncService,
                this.pdfService,
                runtime.maxConcurrentTasks()
        );
    }

    // ========== Приватные методы сборки стратегий ==========

    private NoteStrategy createNoteStrategy(StoragePathsConfig paths, AgroApiConfig agro, InternalHttpClient client) {
        NoteImageLoader loader = new NoteImageLoader(
                client,
                agro.baseUrl(),
                agro.apiKey(),
                agro.userAgent(),
                agro.attachmentsEndpoint()
        );
        // Собираем внутренний конфиг стратегии заметок
        NoteConfig noteConfig = new NoteConfig(
                paths.notesDir().toString(),
                paths.notesExtension(),
                paths.cacheBaseDir()
        );
        return new NoteStrategy(loader, noteConfig);
    }

    private SatelliteStrategy createSatelliteStrategy(StoragePathsConfig paths, AgroApiConfig agro,
                                                      SatelliteConfig satellite, InternalHttpClient client,
                                                      JsonDataParser jsonParser) {
        SatelliteImageLoader loader = new SatelliteImageLoader(
                client,
                agro.baseUrl(),
                agro.apiKey(),
                agro.userAgent(),
                agro.spectralIndicesEndpoint()
        );
        // Собираем внутренний конфиг для спутниковой стратегии
        SatelliteConfigInternal satConfig = new SatelliteConfigInternal(
                paths.cacheBaseDir(),
                satellite.fromDate(),
                satellite.toDate(),
                satellite.scanWindowDays(),
                satellite.cloudThreshold(),
                satellite.cloudWeightFactor(),
                paths.satelliteExtension()
        );
        return new SatelliteStrategy(loader, jsonParser, satConfig);
    }

    private ChartStrategy createChartStrategy(StoragePathsConfig paths, SatelliteConfig satellite,
                                              JsonDataParser jsonParser, AppRuntimeConfig runtime) {
        ChartConfig chartConfig = new ChartConfig(
                paths.chartsDir().toString(),
                paths.chartExtension(),
                paths.chartPrefix(),
                paths.chartWidth(),
                paths.chartHeight(),
                paths.cacheBaseDir(),
                paths.chartFontPath().toString(),
                runtime.timezone(),
                satellite.cloudThreshold()
        );
        ChartGenerator chartGenerator = new XChartGeneratorImpl(chartConfig);
        ChartMapper chartMapper = new ChartMapper(satellite.cloudThreshold());
        return new ChartStrategy(chartGenerator, chartConfig, jsonParser, chartMapper);
    }

    private FieldDataAggregator createAggregator(AppRuntimeConfig runtime, SatelliteConfig satellite) {
        OperationMapper opMapper = new OperationMapper(runtime.timezone(), runtime.aggregationThreshold());
        NoteMapper noteMapper = new NoteMapper(runtime.timezone());
        TechJournalMapper techMapper = new TechJournalMapper(runtime.defaultEmptyLabel());
        SatelliteMapper satelliteMapper = new SatelliteMapper(satellite.indices(), satellite.mappingRules());

        return new FieldDataAggregator(opMapper, noteMapper, techMapper, satelliteMapper, runtime.timezone());
    }

    // ========== Getters ==========
    public WebDataProvider getWebDataProvider() { return (WebDataProvider) dataProvider; }
    public DataProvider getDataProvider() { return dataProvider; }
    public ImageSyncService getSyncService() { return syncService; }
    public PassportOrchestrator getOrchestrator() { return orchestrator; }
    public PassportGeneratorService getPdfService() { return pdfService; }
}