package com.viking.field_passport_generator.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viking.field_passport_generator.data.dto.DownloadInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ImageLoader {
    private static final Logger log = LoggerFactory.getLogger(ImageLoader.class);

    private final String attachmentsEndpoint;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String userAgent;

    public ImageLoader(HttpClient httpClient, String baseUrl, String apiKey,
                       String userAgent, String attachmentsEndpoint) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.userAgent = userAgent;
        this.attachmentsEndpoint = attachmentsEndpoint;
    }

    public Map<String, DownloadInfo> fetchDownloadUrls(List<String> ids) {
        try {
            Map<String, List<String>> requestMap = Map.of("ids", ids);
            String jsonBody = objectMapper.writeValueAsString(requestMap);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl).resolve(attachmentsEndpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("User-Agent", userAgent)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, DownloadInfo> links = parseLinkResponse(response.body());

                if (links.isEmpty()) {
                    log.warn("API found NO images for this batch of {} IDs. Moving on...", ids.size());
                } else if (links.size() < ids.size()) {
                    log.info("API partially matched: {}/{} images found.", links.size(), ids.size());
                } else {
                    log.info("API match: All {} images found!", ids.size());
                }
                return links;
            }
        } catch (JsonProcessingException e) {
            log.error("Json Serialization Error: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Request Executing Error: {}", e.getMessage());
        }
        return Map.of();
    }

    private Map<String, DownloadInfo> parseLinkResponse(String body) throws JsonProcessingException {
        AttachmentResponse response = objectMapper.readValue(body, AttachmentResponse.class);
        if (response == null || !response.success() || response.data == null) {
            return Map.of();
        }
        return response.data.stream()
                .filter(d -> d.meta() != null && d.meta().resourceUrl() != null)
                .collect(Collectors.toMap(
                        Data::id,
                        d -> new DownloadInfo(
                                d.meta().resourceUrl(),
                                determineExtension(d)
                                ),
                                (existing, replacement) -> existing
                ));
    }

    public byte[] downloadBytes(String url) {
        log.debug("Скачивание файла по ссылке: {}", url);
        String encodedUrl = url.replace(" ", "%20");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(encodedUrl))
                .headers("User-Agent", userAgent)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                byte[] data = response.body();
                return (data != null && data.length > 1024) ? data : null;
            } else {
                log.warn("Server response code: {} for file: {}", response.statusCode(), encodedUrl);
            }
        } catch (Exception e) {
            log.error("Error downloading file: {}", e.getMessage());
        }
        return null;
    }

    private String determineExtension(Data data) {
        if (data == null) {
            return "jpg";
        }
        String mimeType = (data.meta() != null) ? data.meta().mimeType() : null;
        String fileName = data.name();

        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        }

        if (mimeType != null && mimeType.contains("/")) {
            String extension = mimeType.substring(mimeType.lastIndexOf("/") + 1).toLowerCase();
            return "jpeg".equals(extension) ? "jpg" : extension;
        }

        return "jpg";
    }

    private record AttachmentResponse(boolean success, List<Data> data) {}
    private record Data(String id, String name, Meta meta) {}
    private record Meta(String resourceUrl, String mimeType) {}
}
