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
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class SatelliteImageLoader {
    private static final Logger log = LoggerFactory.getLogger(SatelliteImageLoader.class);
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final AtomicBoolean circuitBreaker = new AtomicBoolean(false);
    private final AtomicLong lastErrorTime = new AtomicLong(0);
    private static final long RECOVERY_TIME_MS = 60_000;
    private final Semaphore networkSemaphore = new Semaphore(5);

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String userAgent;
    private final String spectralEndpoint;

    public SatelliteImageLoader(HttpClient httpClient, String baseUrl, String apiKey,
                                String userAgent, String spectralEndpoint) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.userAgent = userAgent;
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
                    log.warn("Вместо списка пришел объект! Возможно, это ошибка или обертка: {}", node);

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

    public byte[] downloadBytes(String path) {
        // 1. Быстрая проверка: если предохранитель выбит или ссылки нет — даже не пытаемся
        if (path == null || path.isBlank()) {
            return null;
        }

        if (circuitBreaker.get()) {
            long elapsed = System.currentTimeMillis() - lastErrorTime.get();
            if (elapsed > RECOVERY_TIME_MS) {
                log.info("Circuit breaker is HALF-OPEN. Testing API with a probe request...");
            } else {
                return null;
            }
        }
        URI fullUri;
        try {
            fullUri = URI.create(baseUrl).resolve(path);
        } catch (IllegalArgumentException e) {
            log.error("Invalid path: {}", path);
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

            // 5. Реакция на перегрузку API или ошибки сервера
            int maxAttempts = 3;
            long waitTime = 2000;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                if (circuitBreaker.get() && (System.currentTimeMillis() - lastErrorTime.get() < RECOVERY_TIME_MS)) {
                    return null;
                }

                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                // 4. Обработка результата
                if (status == 200) {
                    if (circuitBreaker.get()) {
                        log.info("Api recovered! Circuit breaker is now CLOSED");
                        circuitBreaker.set(false);
                        lastErrorTime.set(0);
                    }
                    byte[] data = response.body();
                    // Проверка на размер (заглушки обычно весят очень мало)
                    if (data != null && data.length > 1024) {
                        return data;
                    } else {
                        log.warn("Снимок скачан, но размер подозрительно мал: {} байт", data != null ? data.length : 0);
                        return null;
                    }
                }
                if (status == 429) {
                    log.error("API 429 (Too Many Requests). Tripping circuit breaker.");
                    circuitBreaker.set(true);
                    lastErrorTime.set(System.currentTimeMillis());
                    return null;
                }

                if (status >= 500) {
                    log.warn("Attempt {}/{} | Status {} | Retrying in {}ms", attempt, maxAttempts, status, waitTime);
                    if (attempt < maxAttempts) {
                        Thread.sleep(waitTime);
                        waitTime *= 2;
                    } else {
                        log.error("Max attempts reached for 5xx. Tripping circuit breaker.");
                        circuitBreaker.set(true);
                        lastErrorTime.set(System.currentTimeMillis());
                    }
                } else {
                    log.warn("Non-retryable error: {}. Skipping: {}", status, fullUri);
                    break;
                }
            }
        } catch (InterruptedException e) {
            log.error("Поток загрузки прерван");
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            log.error("Network I/O error {}: {}", fullUri, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage());
        } finally {
            networkSemaphore.release();
        }
        return null;
    }

    public boolean isCircuitBroken() {
        return circuitBreaker.get();
    }

    private URI buildUri(List<Long> ids, String fromDate, String toDate) {
        try {
            List<String> params = new ArrayList<>();
            params.add("apiKey=" + apiKey);
            params.add("fromDate=" + fromDate);
            params.add("toDate=" + toDate);

            for (Long id : ids) {
                params.add("id=" + id);
            }

            String query = String.join("&", params);

            URI base = URI.create(baseUrl);
            return new URI(
                    base.getScheme(),
                    base.getAuthority(),
                    base.getPath() + spectralEndpoint,
                    query,
                    null
            );
        } catch (URISyntaxException e) {
            log.error("Failed to construct spectral data URI for ids: {}. Reason: {}", ids, e.getMessage());
        }
        return null;
    }

}
