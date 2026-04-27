package com.viking.field_passport_generator.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viking.field_passport_generator.data.dto.satellite.FieldSpectralResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class SatelliteImageLoader {
    private static final Logger log = LoggerFactory.getLogger(SatelliteImageLoader.class);
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final AtomicBoolean circuitBreaker = new AtomicBoolean(false);
    private final Semaphore networkSemaphore = new Semaphore(5);

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String userAgent;
    private final double cloudThreshold;
    private final String spectralEndpoint;

    public SatelliteImageLoader(HttpClient httpClient, String baseUrl, String apiKey,
                                String userAgent, double cloudThreshold, String spectralEndpoint) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.userAgent = userAgent;
        this.cloudThreshold = cloudThreshold;
        this.spectralEndpoint = spectralEndpoint;
    }

    public Map<Long, FieldSpectralResponse> fetchSpectralData(List<Long> ids, String fromDate, String toDate) {


        URI uri = buildUri(ids, fromDate, toDate);
        log.debug("Fetch satellite data: {}", uri);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                log.error("API вернул код {}", response.statusCode());
                return Map.of();
            }

            try (InputStream is = response.body()) {
                // Читаем как дерево (JsonNode), это сработает и для { }, и для [ ]
                JsonNode node = objectMapper.readTree(is);

                List<FieldSpectralResponse> rawData;

                if (node.isArray()) {
                    // Если пришел список (как ты и ждешь)
                    rawData = objectMapper.convertValue(node, new TypeReference<>() {});
                } else if (node.isObject()) {
                    // Если пришел объект, логируем его, чтобы понять, что там внутри
                    log.warn("Вместо списка пришел объект! Возможно, это ошибка или обертка: {}", node.toString());

                    // Если данные лежат внутри поля, например "data" или "items", достаем их:
                    if (node.has("data") && node.get("data").isArray()) {
                        rawData = objectMapper.convertValue(node.get("data"), new TypeReference<>() {});
                    } else {
                        return Map.of(); // Если это просто объект ошибки
                    }
                } else {
                    return Map.of();
                }

                return rawData.stream()
                        .collect(Collectors.toMap(
                                FieldSpectralResponse::id,
                                f -> f,
                                (existing, replacement) -> existing
                        ));
            }
        } catch (InterruptedException e) {
            log.error("Запрос прерван: {}", e.getMessage());
            Thread.currentThread().interrupt();
            return Map.of();
        } catch (IOException e) {
            log.error("Ошибка ввода-вывода (возможно, Jackson или сеть): {}", e.getMessage());
            return Map.of();
        }
    }

    private String determineExtension(String url) {
        String cleanPath = url.contains("?") ? url.substring(0, url.indexOf("?")) : url;
        if (cleanPath.contains(".")) {
            return cleanPath.substring(cleanPath.lastIndexOf(".") + 1).toLowerCase();
        }
        return "png";
    }

    public byte[] downloadBytes(String url) {
        // 1. Быстрая проверка: если предохранитель выбит или ссылки нет — даже не пытаемся
        if (url == null || url.isBlank() || circuitBreaker.get()) {
            return null;
        }

        // 2. Формируем полный URI
        URI fullUri;
        try {
            if (url.startsWith("http")) {
                fullUri = URI.create(url.replace(" ", "%20"));
            } else {
                String path = url.startsWith("/") ? url.substring(1) : url;
                fullUri = URI.create(baseUrl).resolve(path.replace(" ", "%20"));
            }
        } catch (Exception e) {
            log.error("Некорректный URL снимка: {}", url);
            return null;
        }

        // 3. Заходим под защиту Семафора
        try {
            networkSemaphore.acquire(); // Ждем разрешения, если уже качается 5 файлов

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(fullUri)
                    .header("User-Agent", userAgent)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            log.debug("Загрузка снимка: {}", fullUri);

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            // 4. Обработка результата
            if (response.statusCode() == 200) {
                byte[] data = response.body();
                // Проверка на размер (заглушки обычно весят очень мало)
                if (data != null && data.length > 1024) {
                    return data;
                } else {
                    log.warn("Снимок скачан, но размер подозрительно мал: {} байт", data != null ? data.length : 0);
                    return null;
                }
            }

            // 5. Реакция на перегрузку API или ошибки сервера
            if (response.statusCode() == 429 || response.statusCode() >= 500) {
                log.error("КРИТИЧЕСКАЯ ОШИБКА API {}. Выбиваем предохранитель.", response.statusCode());
                circuitBreaker.set(true);
            } else {
                log.warn("Не удалось скачать снимок. Код: {} | URL: {}", response.statusCode(), fullUri);
            }

        } catch (InterruptedException e) {
            log.error("Поток загрузки прерван");
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            log.error("Ошибка сети при загрузке снимка {}: {}", fullUri, e.getMessage());
            // Если сеть совсем легла — тоже можно выбить предохранитель
            // circuitBreaker.set(true);
        } catch (Exception e) {
            log.error("Непредвиденная ошибка: {}", e.getMessage());
        } finally {
            // ОБЯЗАТЕЛЬНО освобождаем семафор
            networkSemaphore.release();
        }

        return null;
    }

    public boolean isCircuitBroken() {
        return circuitBreaker.get();
    }

    private URI buildUri(List<Long> ids, String fromDate, String toDate) {
        List<String> params = new ArrayList<>();
        params.add("apiKey=" + encode(apiKey));
        params.add("fromDate=" + encode(fromDate));
        params.add("toDate=" + encode(toDate));

        for (Long id : ids) {
            params.add("id=" + id);
        }
        String queryString = String.join("&", params);
        return URI.create(baseUrl + spectralEndpoint + "?" + queryString);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
