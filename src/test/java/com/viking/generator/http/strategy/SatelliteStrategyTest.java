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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
    @DisplayName("Должен перезапрашивать history.json из API, даже если файл уже есть на диске")
    void shouldRefetchHistoryEvenWhenCachedFileExists() throws Exception {
        // 1. Подготовка: создаём "старый" history.json на диске
        Long fieldId = 495356L;
        Path fieldDir = cachePath.resolve(fieldId.toString());
        Files.createDirectories(fieldDir);
        Path historyFile = fieldDir.resolve("history.json");

        // Записываем старые данные (только до июня 2026, как в реальном кэше)
        SatelliteScan oldScan = new SatelliteScan(
                "old-id", "GoogleEarth", "/ndvi/...", "2026-06-01",
                0.0, null, null, null, null
        );
        FieldSpectralResponse oldData = new FieldSpectralResponse(fieldId, List.of(oldScan));
        when(parser.parse(historyFile, FieldSpectralResponse.class)).thenReturn(oldData);

        // 2. Мокаем API: возвращаем свежие данные (до октября 2026)
        SatelliteScan freshScan = new SatelliteScan(
                "fresh-id", "GoogleEarth", "/ndvi/...", "2026-10-19",
                0.0, null, null, null, null
        );
        FieldSpectralResponse freshData = new FieldSpectralResponse(fieldId, List.of(freshScan));
        when(loader.fetchSpectralData(anyList(), anyString(), anyString()))
                .thenReturn(Map.of(fieldId, freshData));

        // 3. Выполняем collectMetadata
        Map<Long, FieldSpectralResponse> result = strategy.collectMetadata(Set.of(fieldId));

        // 4. Проверки
        // 4.1. API был вызван (раньше пропускался из-за существующего файла)
        verify(loader, atLeastOnce()).fetchSpectralData(
                argThat(ids -> ids.contains(fieldId)),
                anyString(), anyString()
        );

        // 4.2. Результат содержит свежие данные (с октябрьским сканом)
        assertTrue(result.containsKey(fieldId), "Результат должен содержать поле " + fieldId);
        FieldSpectralResponse response = result.get(fieldId);
        assertFalse(response.data().isEmpty(), "Данные не должны быть пустыми");
        assertTrue(
                response.data().stream().anyMatch(s -> s.date().equals("2026-10-19")),
                "Должен присутствовать свежий скан от 2026-10-19"
        );
    }

    @Test
    @DisplayName("Должен перезапрашивать для полей без history.json на диске")
    void shouldFetchForNewFieldsWithoutCachedHistory() {
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
}
