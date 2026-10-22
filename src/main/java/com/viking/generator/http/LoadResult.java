package com.viking.generator.http;

import com.viking.generator.data.dto.satellite.FieldSpectralResponse;

import java.util.Map;
import java.util.Set;

public record LoadResult(
        Map<String, LoadedResource> images,
        int requestedCount,
        int linksFound,
        int downloadErrors,
        Map<Long, FieldSpectralResponse> metadata,
        Set<String> confirmedAbsentIds
) {
    public LoadResult(Map<String, LoadedResource> images, int req, int links, int err) {
        this(images, req, links, err, Map.of(), Set.of());
    }

    public LoadResult(Map<String, LoadedResource> images, int req, int links, int err,
                      Map<Long, FieldSpectralResponse> metadata) {
        this(images, req, links, err, metadata, Set.of());
    }
}
