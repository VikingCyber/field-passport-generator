package com.viking.field_passport_generator.http;

public record DownloadTask(Long fieldId,
                    String indexName,
                    String date, String url) {}
