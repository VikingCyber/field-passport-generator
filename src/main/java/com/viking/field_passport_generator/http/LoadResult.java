package com.viking.field_passport_generator.http;

import java.util.Map;

public record LoadResult(
        Map<String, LoadedResource> images,
        int requestedCount,
        int linksFound,
        int downloadErrors
) {
}
