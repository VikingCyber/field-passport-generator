package com.viking.generator.http;

public record DownloadTask(Long fieldId,
                    String indexName,
                    String date, String url) {}
