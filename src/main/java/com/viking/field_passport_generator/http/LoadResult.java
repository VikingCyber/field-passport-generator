package com.viking.field_passport_generator.http;

import com.viking.field_passport_generator.data.dto.satellite.FieldSpectralResponse;

import java.util.Map;

public record LoadResult(
        Map<String, LoadedResource> images,
        int requestedCount,
        int linksFound,
        int downloadErrors,
        Map<Long, FieldSpectralResponse> metadata
) {
    public LoadResult(Map<String, LoadedResource> images, int req, int links, int err) {
        this(images, req, links, err, Map.of());
    }
}
