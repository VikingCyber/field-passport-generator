package com.viking.generator.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viking.generator.data.dto.satellite.FieldSpectralResponse;
import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class SatelliteImageLoader {
    private static final Logger log = LoggerFactory.getLogger(SatelliteImageLoader.class);
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final InternalHttpClient internalClient;
    private final String baseUrl;
    private final String apiKey;
    private final String userAgent;
    private final String spectralEndpoint;

    public SatelliteImageLoader(InternalHttpClient internalClient, String baseUrl, String apiKey,
                                String userAgent, String spectralEndpoint) {
        this.internalClient = internalClient;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.userAgent = userAgent;
        this.spectralEndpoint = spectralEndpoint;
    }

    public Map<Long, FieldSpectralResponse> fetchSpectralData(List<Long> ids, String fromDate, String toDate) {
        HttpUrl base = HttpUrl.parse(baseUrl);
        if (base == null) {
            log.error("API Error: base URL: {} is incorrect. Check configuration", baseUrl);
            return Map.of();
        }
        HttpUrl fullUrl = base.resolve(spectralEndpoint);
        if (fullUrl == null) {
            log.error("API Error: unsuccessful attempt to build path from endpoint: {}", spectralEndpoint);
            return Map.of();
        }
        HttpUrl.Builder urlBuilder = fullUrl.newBuilder();
        urlBuilder.addQueryParameter("apiKey", apiKey);
        urlBuilder.addQueryParameter("fromDate", fromDate);
        urlBuilder.addQueryParameter("toDate", toDate);
        ids.forEach(id -> urlBuilder.addQueryParameter("id", String.valueOf(id)));

        URI uri = urlBuilder.build().uri();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("User-Agent", userAgent)
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        HttpResponse<InputStream> response = internalClient.sendRequest(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response == null) return Map.of();
        try (InputStream is = response.body()) {
            JsonNode node = objectMapper.readTree(is);
            if (node.isArray()) {
                List<FieldSpectralResponse> rawData = objectMapper.convertValue(node, new TypeReference<>() {});
                return rawData.stream().collect(Collectors.toMap(FieldSpectralResponse::id, f -> f));
            }
        } catch (IOException e) {
            log.error("Jackson parsing error: {}", e.getMessage());
        }
        return Map.of();
    }

    public byte[] downloadBytes(String path) {
        return internalClient.downloadBytes(this.baseUrl, path, this.userAgent);
    }

}
