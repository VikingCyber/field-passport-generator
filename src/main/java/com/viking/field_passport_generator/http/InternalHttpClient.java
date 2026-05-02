package com.viking.field_passport_generator.http;

import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class InternalHttpClient {
    private static final Logger log = LoggerFactory.getLogger(InternalHttpClient.class);
    private final HttpClient httpClient;
    private final Semaphore networkSemaphore;
    private final AtomicBoolean circuitBreaker = new AtomicBoolean(false);
    private final AtomicLong lastErrorTime = new AtomicLong(0);
    private final long recoveryTimeMs;

    public InternalHttpClient(int maxConcurrentRequests, Long recoveryTimeMs) {
        this.networkSemaphore = new Semaphore(maxConcurrentRequests);
        this.recoveryTimeMs = recoveryTimeMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }


    public <T> HttpResponse<T> sendRequest(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        if (isCircuitBreakerOpen()) {
            log.warn("Circuit breaker is OPEN. Skipping request to: {}", request.uri());
            return null;
        }

        try {
            networkSemaphore.acquire();
            return executeWithRetries(request, handler);
        } catch (InterruptedException e) {
            log.error("Request interrupted: {}", request.uri());
            Thread.currentThread().interrupt();
            return null;
        } finally {
            networkSemaphore.release();
        }
    }

    public Optional<URI> buildSafeUri(String baseUrl, String path) {
        if (path == null || path.isBlank()) return Optional.empty();
        HttpUrl url;
        if (path.startsWith("http")) {
            url = HttpUrl.parse(path);
        } else {
            HttpUrl base = HttpUrl.parse(baseUrl);
            url = (base != null) ? base.resolve(path) : null;
        }
        return Optional.ofNullable(url).map(HttpUrl::uri);
    }

    public byte[] downloadBytes(String baseUrl, String path, String userAgent) {
        return buildSafeUri(baseUrl, path).map(uri -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", userAgent)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = sendRequest(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response != null && response.body() != null && response.body().length > 1024) {
                return response.body();
            } else {
                log.warn("File downloaded but is suspiciously small or null for URI: {}", uri);
                return null;
            }
        }).orElse(null);
    }

    private boolean isCircuitBreakerOpen() {
        if (!circuitBreaker.get()) return false;
        long elapsed = System.currentTimeMillis() - lastErrorTime.get();
        if (elapsed > recoveryTimeMs) {
            log.info("Circuit breaker is HALF-OPEN. Testing API with a probe request...");
        } else {
            return false;
        }
        return true;
    }

    private <T> HttpResponse<T> executeWithRetries(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        int maxAttempts = 3;
        long waitTime = 2000;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<T> response = httpClient.send(request, handler);
                int status = response.statusCode();

                if (status >= 200 && status < 300) {
                    if (circuitBreaker.get()) {
                        log.info("API recovered! Circuit breaker is now CLOSED");
                        circuitBreaker.set(false);
                    }
                    return response;
                }

                if (status == 429) {
                    tripCircuitBreaker("API 429 (Too many Requests)");
                    return null;
                }

                if (status >= 500) {
                    log.warn("Attempt {}/{} | Status {} | Retrying in {}ms", attempt, maxAttempts, status, waitTime);
                    if (attempt < maxAttempts) {
                        Thread.sleep(waitTime);
                        waitTime *= 2;
                        continue;
                    }
                    tripCircuitBreaker("Max attempts reached for 5xx errors");
                } else {
                    log.warn("Non-retryable error: {} for URI: {}", status, request.uri());
                    break;
                }
            } catch (InterruptedException e) {
                log.error("Download thread interrupted. URI: {}, Reason: {}", request.uri(), e.getMessage());
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                log.error("Network I/O error {}: {}", request.uri(), e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error: {}", e.getMessage());
            }
        }
        return null;
    }

    private void tripCircuitBreaker(String reason) {
        log.error("{}. Tripping circuit breaker", reason);
        circuitBreaker.set(true);
        lastErrorTime.set(System.currentTimeMillis());
    }
}
