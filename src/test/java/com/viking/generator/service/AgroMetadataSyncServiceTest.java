package com.viking.generator.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.viking.generator.config.record.AgroApiConfig;
import com.viking.generator.config.record.AgroSyncConfig;
import com.viking.generator.config.record.LocalFilesConfig;
import com.viking.generator.http.InternalHttpClient;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AgroMetadataSyncServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldWriteCommaBetweenChunks() throws Exception {
        String json = """
                {
                     "data": [
                         {"id": 1, "name": "first"},
                         {"id": 2, "name": "second"}
                     ]
                }
                """;
        Path chunkFile = tempDir.resolve("chunk.json");
        Files.writeString(chunkFile, json);
        StringWriter writer = new StringWriter();
        AgroMetadataSyncService service = new AgroMetadataSyncService(null, null, null, null);
        boolean result = service.parseAndWriteChunkStream(chunkFile, writer, true);

        assertTrue(result, "Метод должен вернуть true для непустого массива");
        String output = writer.toString();
        assertTrue(output.startsWith(","), "Вывод должен начинаться с запятой");
        assertTrue(output.contains("\"id\":1"), "Должен содержать первый объект");
        assertTrue(output.contains("\"id\":2"), "Должен содержать второй объект");
        assertFalse(output.contains("[") && output.contains("]"),
                "Не должен содержать квадратных скобок массива");
    }

    @Test
    void shouldReturnFalseForEmptyDataArray() throws Exception {
        String json = """
                {
                    "data": []
                }
                
                """;
        Path chunkFile = tempDir.resolve("empty.json");
        Files.writeString(chunkFile, json);
        StringWriter writer = new StringWriter();
        AgroMetadataSyncService service = new AgroMetadataSyncService(null, null, null, null);
        boolean result = service.parseAndWriteChunkStream(chunkFile, writer, false);
        assertFalse(result, "Должен вернуть false для пустого массива");
        assertTrue(writer.toString().isEmpty(), "Writer должен остаться пустым");
    }

    @Test
    void shouldDeleteTempFilesOnSyncFailure() throws IOException {
        InternalHttpClient mockHttpClient = mock(InternalHttpClient.class);
        when(mockHttpClient.sendRequest(any(), any())).thenReturn(null);

        AgroApiConfig apiConfig = new AgroApiConfig(
                "key123",
                "http://test.local",
                "TestApp/1.0",
                0L,
                0L,
                null,
                null,
                "/field-report",
                "/tmc",
                "/units",
                "/notes"
        );
        AgroSyncConfig syncConfig = new AgroSyncConfig(
                ZoneId.of("UTC"),
                Instant.parse("2023-01-01T00:00:00.000Z"),
                Instant.parse("2025-01-01T00:00:00.000Z"),
                12345L
        );
        LocalFilesConfig localConfig = new LocalFilesConfig(
                tempDir.resolve("tmc.json"),
                tempDir.resolve("units.json"),
                tempDir.resolve("notes.json"),
                tempDir.resolve("fieldData.json"),
                tempDir.resolve("operations.json"),
                tempDir.resolve("kml")
        );

        AgroMetadataSyncService service = new AgroMetadataSyncService(
                mockHttpClient, apiConfig, syncConfig, localConfig
        );

        try {
            service.syncAllMetadata();
            fail("Должно было выбросить исключение");
        } catch (IOException ignored) {

        }

        Path opsTemp = localConfig.operationsPath()
                .resolveSibling(localConfig.operationsPath().getFileName() + ".combined.tmp");
        assertFalse(Files.exists(opsTemp));
        verify(mockHttpClient, atLeastOnce()).sendRequest(any(), any());
    }
}
