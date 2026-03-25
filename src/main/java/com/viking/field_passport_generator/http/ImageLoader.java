package com.viking.field_passport_generator.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viking.field_passport_generator.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ImageLoader {
    private static final Logger log = LoggerFactory.getLogger(ImageLoader.class);

    private static final String IMAGE_LINK_PATH = "storage/files";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String userAgent;
    private final String cacheDir;

    public ImageLoader(HttpClient httpClient, AppConfig appConfig) {
        this.httpClient = httpClient;
        this.baseUrl = appConfig.getString("agro.api.base-url");
        this.apiKey = appConfig.getString("agro.api.key");
        this.userAgent = appConfig.getString("agro.api.user-agent");
        this.cacheDir = appConfig.getString("app.cache-dir");
    }

    public Map<String, String> fetchDownloadUrls(List<String> ids) {
        try {
            Map<String, List<String>> requestMap = Map.of("ids", ids);
            String jsonBody = objectMapper.writeValueAsString(requestMap);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl).resolve(IMAGE_LINK_PATH))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("User-Agent", userAgent)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseLinkResponse(response.body());
            } else {
                log.error("API вернул ошибку {}: {}", response.statusCode(), response.body());
            }
        } catch (JsonProcessingException e) {
            log.error("Json Serialization Error: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Request Executing Error: {}", e.getMessage());
        }
        return Map.of();
    }

    private Map<String, String> parseLinkResponse(String body) throws JsonProcessingException {
        AttachmentResponse response = objectMapper.readValue(body, AttachmentResponse.class);
        if (response == null || !response.success() || response.data == null) {
            return Map.of();
        }
        return response.data.stream()
                .filter(d -> d.meta() != null && d.meta().resourceUrl() != null)
                .collect(Collectors.toMap(
                        Data::id,
                        d -> d.meta().resourceUrl(),
                        (existing, replacement) -> existing
                ));
    }

    public byte[] downloadBytes(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .headers("User-Agent", userAgent)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                byte[] data = response.body();
                return (data != null && data.length > 1024) ? data : null;
            }
        } catch (Exception e) {
            log.error("Error downloading file: {}", e.getMessage());
        }
        return null;
    }

//    public void saveToDisk(String id, byte[] data) throws IOException {
//        Path cache = Path.of(cacheDir);
//        if (Files.notExists(cache)) {
//            Files.createDirectories(cache);
//        }
//        Path filePath = cache.resolve(id + ".jpg");
//        Files.write(filePath, data);
//        System.out.println("File saved: " + filePath.toAbsolutePath());
//    }
//
//    public static void main(String[] args) {
//        AppConfig appConfig = new AppConfig();
//        HttpClient httpClient = HttpClient.newBuilder()
//                .followRedirects(HttpClient.Redirect.NORMAL)
//                .connectTimeout(Duration.ofSeconds(10))
//                .build();
//        try {
//            ImageLoader imageLoader = new ImageLoader(httpClient, appConfig);
//            String testId = "683feed0401c77276b6cabdb";
//            System.out.println("----- Тест 1: Получение ссылок -----");
//            var links = imageLoader.fetchDownloadUrls(List.of(testId));
//            if (links.containsKey(testId)) {
//                String url = links.get(testId);
//                System.out.println("Успех! Ссылка: " + url);
//
//                System.out.println("----- Тест 2: Получение изображения -----");
//                byte[] image = imageLoader.downloadBytes(url);
//                System.out.println("Скачано байт: " + (image != null ? image.length : "0"));
//                imageLoader.saveToDisk(testId, image);
//
//            } else {
//                System.out.println("ID не найден в ответе. Проверь структуру DTO!");
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    private record AttachmentResponse(boolean success, List<Data> data) {}
    private record Data(String id, Meta meta) {}
    private record Meta(String resourceUrl) {}

}
