package com.viking.field_passport_generator.service;

import com.viking.field_passport_generator.data.dto.RawApiResponse;
import com.viking.field_passport_generator.data.dto.RawFieldData;
import com.viking.field_passport_generator.data.dto.RawNotesResponse;
import com.viking.field_passport_generator.model.FieldPassport;
import com.viking.field_passport_generator.model.SourceType;
import com.viking.field_passport_generator.util.JsonDataParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ImageSyncService {
    private final ImageCacheService cacheService;
    private final JsonDataParser parser;
    private final Logger log = LoggerFactory.getLogger(ImageSyncService.class);

    public ImageSyncService(ImageCacheService cacheService, JsonDataParser parser) {
        this.cacheService = cacheService;
        this.parser = parser;
    }

    public void warmUpAll(String notesDataPath, String satelliteDataPath) {
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

    private void warmUpNotes(String notesJsonPath) {
        log.info("Starting notes images data synchronization from: {}", notesJsonPath);
        InputStream is = getClass().getClassLoader().getResourceAsStream(notesJsonPath);
        RawNotesResponse notes = parser.parse(is, RawNotesResponse.class);

        Set<String> allIds = notes.data().stream()
                .flatMap(note -> note.attachments().stream())
                .collect(Collectors.toSet());

        cacheService.sync(allIds, SourceType.NOTE);
    }

    private void warmUpSatelliteMetadata(String fieldsJsonPath) {
        log.info("Starting satellite data synchronization from {}", fieldsJsonPath);

        // 1. Читаем файл через classloader (как и заметки)
        InputStream is = getClass().getClassLoader().getResourceAsStream(fieldsJsonPath);
        if (is == null) {
            log.error("Файл не найден: {}", fieldsJsonPath);
            return;
        }

        // 2. Парсим список полей
        RawApiResponse fieldsResponse = parser.parse(is, RawApiResponse.class);

        // 3. Собираем ID
        Set<Long> allIds = fieldsResponse.data().stream()
                .map(RawFieldData::fieldId)
                .collect(Collectors.toSet());

        log.debug("Found {} fields for image download", allIds.size());

        // 4. Отправляем в кэш
        cacheService.sync(allIds, SourceType.SATELLITE);
    }
}
