package com.viking.generator.config;

import com.viking.generator.config.record.*;
import com.viking.generator.data.aggregator.FieldDataAggregator;
import com.viking.generator.data.provider.DataProvider;
import com.viking.generator.data.provider.FileDataProvider;
import com.viking.generator.data.provider.InMemoryDataProvider;
import com.viking.generator.http.InternalHttpClient;
import com.viking.generator.http.NoteImageLoader;
import com.viking.generator.http.SatelliteImageLoader;
import com.viking.generator.http.strategy.ChartStrategy;
import com.viking.generator.http.strategy.NoteStrategy;
import com.viking.generator.http.strategy.SatelliteStrategy;
import com.viking.generator.mapper.*;
import com.viking.generator.service.*;
import com.viking.generator.service.orchestration.PassportOrchestrator;
import com.viking.generator.util.JsonDataParser;
import java.util.List;

public class AppContainer {
    private final DataProvider dataProvider;
    private final SyncService syncService;
    private final PassportOrchestrator orchestrator;

    public AppContainer(AppConfig config) {
        // ===== 1. Разворачиваем типизированные рекорды =====
        LocalFilesConfig filesConfig = config.getLocalFilesConfig();
        AppRuntimeConfig runtime = config.getAppRuntimeConfig();
        StoragePathsConfig paths = config.getStoragePathsConfig();
        AgroApiConfig agroApiConfig = config.getAgroApiConfig();
        AgroPerformanceConfig perf = config.getAgroPerformanceConfig();
        SatelliteConfig satellite = config.getSatelliteConfig();
        AgroSyncConfig syncConfig = config.getAgroSyncConfig();

        JsonDataParser jsonParser = new JsonDataParser();

        // ===== 2. HTTP Clients =====
        InternalHttpClient noteClient = new InternalHttpClient(
                perf.notesMaxConcurrent(),
                agroApiConfig.recoveryTimeMs(),
                agroApiConfig.minDownloadSizeBytes()
        );
        InternalHttpClient satelliteClient = new InternalHttpClient(
                perf.satelliteMaxConcurrent(),
                agroApiConfig.recoveryTimeMs(),
                agroApiConfig.minDownloadSizeBytes()
        );

        InternalHttpClient metaClient = new InternalHttpClient(
                perf.satelliteMaxConcurrent(),
                agroApiConfig.recoveryTimeMs(),
                agroApiConfig.minDownloadSizeBytes()
        );

        // ===== 3. Image Loading Strategies =====
        NoteStrategy noteStrategy = createNoteStrategy(paths, agroApiConfig, noteClient);
        SatelliteStrategy satelliteStrategy = createSatelliteStrategy(paths, agroApiConfig, satellite, satelliteClient, jsonParser);
        ChartStrategy chartStrategy = createChartStrategy(paths, satellite, jsonParser, runtime);



        // ===== 5. Data Aggregation & Provider =====
        FieldDataAggregator aggregator = createAggregator(runtime, satellite);

        FileDataProvider fileDataProvider = new FileDataProvider(jsonParser, aggregator, filesConfig);
        InMemoryDataProvider cacheProvider = new InMemoryDataProvider(
                paths.outputDir().toString(),
                paths.passportExtension()
        );
        cacheProvider.refreshFromFiles(fileDataProvider);
        this.dataProvider = cacheProvider;

        // ===== 6. PDF Generation =====
        PassportGeneratorService pdfService = new PdfGeneratorService(paths.minFreeSpaceBytes(), paths.outputDir());

        GenerationTracker generationTracker = new GenerationTracker();

        AgroMetadataSyncService apiSyncService = new AgroMetadataSyncService(
                metaClient,
                agroApiConfig,
                syncConfig,
                filesConfig
        );

        ImageCacheService imageCache = new ImageCacheService(
                paths.cacheBaseDir(),
                List.of(noteStrategy, satelliteStrategy, chartStrategy)
        );

        this.syncService = new SyncService(
                imageCache,
                jsonParser,
                apiSyncService,
                cacheProvider,
                fileDataProvider,
                filesConfig
        );

        // ===== 7. Orchestration =====
        this.orchestrator = new PassportOrchestrator(
                this.syncService,
                pdfService,
                cacheProvider,
                runtime.maxConcurrentTasks(),
                cacheProvider::registerNewPassport,
                generationTracker
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
    public DataProvider getDataProvider() { return dataProvider; }
    public SyncService getSyncService() { return syncService; }
    public PassportOrchestrator getOrchestrator() { return orchestrator; }
}