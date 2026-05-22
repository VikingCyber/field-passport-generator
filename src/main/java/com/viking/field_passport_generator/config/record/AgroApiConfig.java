package com.viking.field_passport_generator.config.record;

public record AgroApiConfig(
        String apiKey,
        String baseUrl,
        String userAgent,
        long minDownloadSizeBytes,
        long recoveryTimeMs,
        String attachmentsEndpoint,
        String spectralIndicesEndpoint
) {}
