package com.viking.generator.service;

import com.viking.generator.config.record.LocalFilesConfig;
import com.viking.generator.data.dto.RawApiResponse;
import com.viking.generator.data.dto.RawFieldData;
import com.viking.generator.data.dto.RawNotesResponse;
import com.viking.generator.data.provider.FileDataProvider;
import com.viking.generator.data.provider.InMemoryDataProvider;
import com.viking.generator.model.FieldPassport;
import com.viking.generator.model.common.SourceType;
import com.viking.generator.util.JsonDataParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates the complete data synchronization pipeline.
 *
 * <p>This service coordinates three layers of data refresh:</p>
 * <ol>
 *     <li>Downloads fresh JSON metadata from the AgroSignal API to disk.</li>
 *     <li>Scans downloaded files to warm up the image cache</li>
 *     <li>Reloads the in-memory passport cache from the updated files.</li>
 * </ol>
 *
 * <p>After {@link #syncAndRefreshEcosystem()} completes, the entire application runtime
 * reflects the latest available data without requiring a restart.</p>
 */
public class SyncService {

    private final Logger log = LoggerFactory.getLogger(SyncService.class);
    private final ImageCacheService cacheService;
    private final JsonDataParser parser;

    private final AgroMetadataSyncService apiSyncService;
    private final InMemoryDataProvider cacheProvider;
    private final FileDataProvider fileLoader;
    private final LocalFilesConfig localConfig;

    /**
     * Constructs a new SyncService with all required dependencies.
     *
     * @param cacheService manages the local image cache.
     * @param parser parses JSON response into typed records.
     * @param apiSyncService downloads metadata from the external API.
     * @param cacheProvider holds the runtime passport cache.
     * @param fileLoader loads persisted passport from disk.
     * @param localConfig paths to local data files
     */
    public SyncService(ImageCacheService cacheService, JsonDataParser parser,
            AgroMetadataSyncService apiSyncService, InMemoryDataProvider cacheProvider,
            FileDataProvider fileLoader, LocalFilesConfig localConfig) {
        this.cacheService = cacheService;
        this.parser = parser;
        this.apiSyncService = apiSyncService;
        this.cacheProvider = cacheProvider;
        this.fileLoader = fileLoader;
        this.localConfig = localConfig;
    }

    /**
     * Runs the full system refresh: download, cache warm-up and runtime reloads.
     *
     * <p>This is the top-level entry point for manually triggered synchronization. Any failure
     * in the underlying steps will propagate as an exception</p>
     *
     * @throws Exception if any synchronization step fails.
     */
    public synchronized void syncAndRefreshEcosystem() throws Exception {
        log.info("=== НАЧАЛО ГЛОБАЛЬНОЙ СИНХРОНИЗАЦИИ ЭКОСИСТЕМЫ ===");

        // 1. Скачиваем свежие JSON-ы из Агросигнала на диск
        apiSyncService.syncAllMetadata();

        // 2. Прогреваем кэш картинок на основе новых файлов
        warmUpAll(localConfig.notesPath(), localConfig.fieldDataPath());

        // 3. Перетираем старый кэш паспортов в RAM новыми данными
        cacheProvider.refreshFromFiles(fileLoader);

        log.info("=== ЭКОСИСТЕМА УСПЕШНО ОБНОВЛЕНА В РАНТАЙМЕ ===");
    }

    /**
     * Warms up the image cache for both notes and satellite data.
     *
     * @param notesDataPath path to the downloaded notes JSON file
     * @param satelliteDataPath path to the downloaded satellite JSON file
     */
    public void warmUpAll(Path notesDataPath, Path satelliteDataPath) {
        warmUpNotes(notesDataPath);
        warmUpSatelliteMetadata(satelliteDataPath);
    }

    /**
     * Ensures all images referenced by a single passport are present in the cache.
     *
     * <p>This method is called on-the-fly when a passport is requested, so that missing
     * images are fetched without blocking the entire refresh cycle</p>
     *
     * @param passport the passport whose images should be cached.
     */
    public void prepareSinglePassport(FieldPassport passport) {
        log.debug("Sync images for field: {}, year: {}",
                passport.generalInfo().fieldName(),
                passport.generalInfo().year());

        if (passport.satelliteImages() != null) {
            cacheService.fillImages(passport.satelliteImages());
        }

        if (passport.notesSection() != null && passport.notesSection().images() != null) {
            cacheService.fillImages(passport.notesSection().images());
        }

        if (passport.indexChart() != null) {
            cacheService.fillImages(List.of(passport.indexChart()));
        }

    }

    /**
     * Reads the downloaded notes JSON and synchronizes the referenced attachment IDs with the image
     * cache.
     *
     * @param notesJsonPath path to the {@code notesData} JSON file.
     */
    private void warmUpNotes(Path notesJsonPath) {
        log.info("Starting notes images data synchronization from disk: {}", notesJsonPath);
        try (InputStream is = Files.newInputStream(notesJsonPath)) {
            RawNotesResponse notes = parser.parse(is, RawNotesResponse.class);
            if (notes != null && notes.data() != null) {
                Set<String> allIds = notes.data().stream()
                        .filter(note -> note != null && note.attachments() != null)
                        .flatMap(note -> note.attachments().stream())
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                cacheService.sync(allIds, SourceType.NOTE);

            }
        } catch (IOException e) {
            log.error("Критическая ошибка чтения файла заметок: {}", notesJsonPath, e);
        }
    }

    /**
     * Reads the downloaded file-data JSON and synchronizes the referenced field IDs with the
     * satellite image cache.
     *
     * @param fieldsJsonPath path to the {@code fieldData} JSON file.
     */
    private void warmUpSatelliteMetadata(Path fieldsJsonPath) {
        log.info("Starting satellite data synchronization from disk: {}", fieldsJsonPath);

        try (InputStream is = Files.newInputStream(fieldsJsonPath)) {
            RawApiResponse fieldsResponse = parser.parse(is, RawApiResponse.class);
            if (fieldsResponse != null && fieldsResponse.data() != null) {
                Set<Long> allIds = fieldsResponse.data().stream()
                        .filter(field -> field != null && field.fieldId() != null)
                        .map(RawFieldData::fieldId)
                        .collect(Collectors.toSet());
                cacheService.sync(allIds, SourceType.SATELLITE);
            }
        } catch (IOException e) {
            log.error("Критическая ошибка чтения файла спутников: {}", fieldsJsonPath, e);
        }
    }
}
