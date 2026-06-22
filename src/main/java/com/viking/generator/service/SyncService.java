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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SyncService {
    private final Logger log = LoggerFactory.getLogger(SyncService.class);
    private final ImageCacheService cacheService;
    private final JsonDataParser parser;

    private final AgroMetadataSyncService apiSyncService;
    private final InMemoryDataProvider cacheProvider;
    private final FileDataProvider fileLoader;
    private final LocalFilesConfig localConfig;

    public SyncService(ImageCacheService cacheService, JsonDataParser parser, AgroMetadataSyncService apiSyncService,
                       InMemoryDataProvider cacheProvider, FileDataProvider fileLoader, LocalFilesConfig localConfig) {
        this.cacheService = cacheService;
        this.parser = parser;
        this.apiSyncService = apiSyncService;
        this.cacheProvider = cacheProvider;
        this.fileLoader = fileLoader;
        this.localConfig = localConfig;
    }

    public void syncAndRefreshEcosystem() throws Exception {
        log.info("=== НАЧАЛО ГЛОБАЛЬНОЙ СИНХРОНИЗАЦИИ ЭКОСИСТЕМЫ ===");

        // 1. Скачиваем свежие JSON-ы из Агросигнала на диск
        apiSyncService.syncAllMetadata();

        // 2. Прогреваем кэш картинок на основе новых файлов
        warmUpAll(localConfig.notesPath(), localConfig.fieldDataPath());

        // 3. Перетираем старый кэш паспортов в RAM новыми данными
        cacheProvider.refreshFromFiles(fileLoader);

        log.info("=== ЭКОСИСТЕМА УСПЕШНО ОБНОВЛЕНА В РАНТАЙМЕ ===");
    }

    public void warmUpAll(Path notesDataPath, Path satelliteDataPath) {
        warmUpNotes(notesDataPath);
        warmUpSatelliteMetadata(satelliteDataPath);
    }

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

    private void warmUpNotes(Path notesJsonPath) {
        log.info("Starting notes images data synchronization from disk: {}", notesJsonPath);
        try (InputStream is = Files.newInputStream(notesJsonPath)) {
            RawNotesResponse notes = parser.parse(is, RawNotesResponse.class);

            Set<String> allIds = notes.data().stream()
                    .flatMap(note -> note.attachments().stream())
                    .collect(Collectors.toSet());

            cacheService.sync(allIds, SourceType.NOTE);
        } catch (IOException e) {
            log.error("Критическая ошибка чтения файла заметок: {}", notesJsonPath, e);
        }
    }

    private void warmUpSatelliteMetadata(Path fieldsJsonPath) {
        log.info("Starting satellite data synchronization from disk: {}", fieldsJsonPath);

        try (InputStream is = Files.newInputStream(fieldsJsonPath)) {
            RawApiResponse fieldsResponse = parser.parse(is, RawApiResponse.class);

            Set<Long> allIds = fieldsResponse.data().stream()
                    .map(RawFieldData::fieldId)
                    .collect(Collectors.toSet());

            log.debug("Found {} fields for image download", allIds.size());
            cacheService.sync(allIds, SourceType.SATELLITE);
        } catch (IOException e) {
            log.error("Критическая ошибка чтения файла спутников: {}", fieldsJsonPath, e);
        }
    }
}
