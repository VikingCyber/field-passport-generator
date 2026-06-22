package com.viking.generator.http;

import com.viking.generator.data.dto.satellite.FieldSpectralResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SatelliteImageLoaderTest {

    private SatelliteImageLoader loader;
    private InternalHttpClient internalClient;

    @BeforeEach
    void setUp() {
        internalClient = mock(InternalHttpClient.class);

        loader = new SatelliteImageLoader(
                internalClient,
                "https://api.test.com",
                "test-api-key",
                "TestAgent/1.0",
                "/spectral"
        );
    }

    @Test
    @DisplayName("Should build correct URL with dense dates and IDs")
    void shouldBuildCorrectUrl() {
        // 1. Подготовка
        List<Long> ids = List.of(391333L);
        String from = "20240101";
        String to = "20240501";

        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> mockResponse = (HttpResponse<InputStream>) mock(HttpResponse.class);

        when(mockResponse.body()).thenReturn(new ByteArrayInputStream("[]".getBytes()));

        doReturn(mockResponse).when(internalClient).sendRequest(any(HttpRequest.class), any());

        loader.fetchSpectralData(ids, from, to);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        verify(internalClient).sendRequest(requestCaptor.capture(), any());

        URI sentUri = requestCaptor.getValue().uri();
        String queryString = sentUri.getQuery();

        assertTrue(queryString.contains("fromDate=20240101"), "Missing fromDate");
        assertTrue(queryString.contains("toDate=20240501"), "Missing toDate");
        assertTrue(queryString.contains("id=391333"), "Missing field ID");
        assertTrue(queryString.contains("apiKey=test-api-key"), "Missing API Key");
    }

    @Test
    @DisplayName("Full integration: Should parse real API JSON structure with ISO dates")
    void shouldParseRealApiJsonStructure() {
        String realJsonResponse = """
    [
      {
        "id": 391333,
        "data": [
          {
            "id": "67d219b4b186af140df9ef8e",
            "objectType": "GoogleEarth",
            "date": "2024-01-02",
            "cloud": 1.0,
            "ndvi": { "mean": 0.0 }
          }
        ]
      }
    ]
    """;

        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> mockResponse = (HttpResponse<InputStream>) mock(HttpResponse.class);
        when(mockResponse.body()).thenReturn(new ByteArrayInputStream(realJsonResponse.getBytes()));

        doReturn(mockResponse).when(internalClient).sendRequest(any(), any());

        Map<Long, FieldSpectralResponse> result = loader.fetchSpectralData(List.of(391333L), "20240101", "20240501");

        FieldSpectralResponse fieldResponse = result.get(391333L);
        String parsedDate = fieldResponse.data().getFirst().date();

        assertEquals("2024-01-02", parsedDate, "Дата должна быть распарсена с дефисами, как в JSON");

    }
}