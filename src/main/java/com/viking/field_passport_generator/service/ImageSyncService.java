package com.viking.field_passport_generator.service;

import com.viking.field_passport_generator.data.dto.RawNotesResponse;
import com.viking.field_passport_generator.util.JsonDataParser;

import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

public class ImageSyncService {
    private final ImageCacheService cacheService;
    private final JsonDataParser parser;

    public ImageSyncService(ImageCacheService cacheService, JsonDataParser parser) {
        this.cacheService = cacheService;
        this.parser = parser;
    }

    public void warmUp(String notesJsonPath) {
        InputStream is = getClass().getClassLoader().getResourceAsStream(notesJsonPath);
        RawNotesResponse notes = parser.parse(is, RawNotesResponse.class);

        Set<String> allIds = notes.data().stream()
                .flatMap(note -> note.attachments().stream())
                .collect(Collectors.toSet());

        cacheService.preloadImages(allIds);
    }
}
