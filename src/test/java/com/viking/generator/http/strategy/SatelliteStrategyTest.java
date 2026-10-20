package com.viking.generator.http.strategy;

import com.viking.generator.config.SatelliteConfigInternal;
import com.viking.generator.data.dto.satellite.FieldSpectralResponse;
import com.viking.generator.data.dto.satellite.SatelliteScan;
import com.viking.generator.http.SatelliteImageLoader;
import com.viking.generator.util.JsonDataParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SatelliteStrategyTest {

    private SatelliteStrategy strategy;
    private SatelliteImageLoader loader;
    private JsonDataParser parser;
    private Path cachePath;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        loader = mock(SatelliteImageLoader.class);
        parser = mock(JsonDataParser.class);
        cachePath = tempDir.resolve("cache");
        try {
            Files.createDirectories(cachePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        SatelliteConfigInternal config = new SatelliteConfigInternal(
                cachePath,
                "20240101",
                "20261231",
                7,
                0.8,
                0.5,
                "png"
        );
        strategy = new SatelliteStrategy(loader, parser, config);
    }

    @Test
    @DisplayName("Устаревший history.json (старше 30 дней) перезапрашивается из API")
    void shouldRefetchStaleHistoryFromApi() throws Exception {
        Long fieldId = 495356L;
        Path historyFile = createHistoryFile(fieldId, "2026-06-01");

        // Делаем кэш «старым» — старше нашего TTL в 30 дней
        Files.setLastModifiedTime(historyFile,
                FileTime.from(Instant.now().minus(45, ChronoUnit.DAYS)));

        // Старое содержимое на диске (мок-парсер его вернёт, если запросят)
        SatelliteScan oldScan = new SatelliteScan(
                "old-id", "GoogleEarth", "/ndvi/...", "2026-06-01",
                0.0, null, null, null, null
        );
        when(parser.parse(historyFile, FieldSpectralResponse.class))
                .thenReturn(new FieldSpectralResponse(fieldId, List.of(oldScan)));

        // Свежий ответ API (октябрь 2026)
        SatelliteScan freshScan = new SatelliteScan(
                "fresh-id", "GoogleEarth", "/ndvi/...", "2026-10-19",
                0.0, null, null, null, null
        );
        FieldSpectralResponse freshData = new FieldSpectralResponse(fieldId, List.of(freshScan));
        when(loader.fetchSpectralData(anyList(), anyString(), anyString()))
                .thenReturn(Map.of(fieldId, freshData));

        Map<Long, FieldSpectralResponse> result = strategy.collectMetadata(Set.of(fieldId));

        // API был вызван, несмотря на наличие кэша
        verify(loader, atLeastOnce()).fetchSpectralData(
                argThat(ids -> ids.contains(fieldId)), anyString(), anyString());
        // В результат пошли свежие данные
        FieldSpectralResponse response = result.get(fieldId);
        assertFalse(response.data().isEmpty(), "Данные не должны быть пустыми");
        assertTrue(response.data().stream().anyMatch(s -> s.date().equals("2026-10-19")),
                "Должен присутствовать свежий скан от 2026-10-19");
    }

    @Test
    @DisplayName("Свежий history.json НЕ перезапрашивается — берём из кэша")
    void shouldReuseFreshHistoryFromDisk() throws Exception {
        Long fieldId = 495356L;
        Path historyFile = createHistoryFile(fieldId, "2026-10-01");

        SatelliteScan cachedScan = new SatelliteScan(
                "cached-id", "GoogleEarth", "/ndvi/...", "2026-10-01",
                0.0, null, null, null, null
        );
        when(parser.parse(historyFile, FieldSpectralResponse.class))
                .thenReturn(new FieldSpectralResponse(fieldId, List.of(cachedScan)));

        Map<Long, FieldSpectralResponse> result = strategy.collectMetadata(Set.of(fieldId));

        // API не вызывается — кэш свежий
        verify(loader, never()).fetchSpectralData(anyList(), anyString(), anyString());
        assertTrue(result.containsKey(fieldId));
        assertTrue(result.get(fieldId).data().stream().anyMatch(s -> s.date().equals("2026-10-01")));
    }

    @Test
    @DisplayName("Для полей без history.json данные всегда запрашиваются из API")
    void shouldFetchForFieldsWithoutCachedHistory() {
        Long newFieldId = 999999L;

        SatelliteScan scan = new SatelliteScan(
                "s1", "GoogleEarth", "/ndvi/...", "2026-07-15",
                0.0, null, null, null, null
        );
        FieldSpectralResponse data = new FieldSpectralResponse(newFieldId, List.of(scan));
        when(loader.fetchSpectralData(anyList(), anyString(), anyString()))
                .thenReturn(Map.of(newFieldId, data));

        Map<Long, FieldSpectralResponse> result = strategy.collectMetadata(Set.of(newFieldId));

        verify(loader, atLeastOnce()).fetchSpectralData(anyList(), anyString(), anyString());
        assertTrue(result.containsKey(newFieldId));
    }

    private Path createHistoryFile(Long fieldId, String scanDate) throws IOException {
        Path fieldDir = cachePath.resolve(fieldId.toString());
        Files.createDirectories(fieldDir);
        Path historyFile = fieldDir.resolve("history.json");
        Files.writeString(historyFile, "{}");
        return historyFile;
    }
}