package com.viking.generator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.viking.generator.config.record.AgroApiConfig;
import com.viking.generator.http.InternalHttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Офлайн-тесты синхронизации KML геозон.
 *
 * <p>Успешное скачивание проверяется через {@link MockWebServer} (localhost, без внешней
 * сети): реальный {@link InternalHttpClient} + {@code BodyHandlers.ofFile} + атомарный
 * перенос. Ошибка сервера проверяется через мок клиента (без сетевых retry-задержек).</p>
 */
class KmlSyncServiceTest {

    private static final String KML_BODY = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document><Placemark><name>ПС10-01</name></Placemark></Document>
            </kml>
            """;

    @TempDir
    Path tempDir;

    private AgroApiConfig apiConfig(String baseUrl) {
        return new AgroApiConfig(
                "test-key", baseUrl, "TestApp/1.0", 0L, 1000L,
                null, null, null, null, null, null);
    }

    @Test
    @DisplayName("Скачивает KML по exportGeozones и атомарно сохраняет в kmlDir")
    void shouldDownloadAndStoreKml() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse.Builder().code(200).body(KML_BODY).build());

            Path kmlDir = tempDir.resolve("kml");
            KmlSyncService service = new KmlSyncService(
                    new InternalHttpClient(1, 1000L, 0L),
                    apiConfig(server.url("/").toString()), kmlDir);

            Optional<Path> result = service.ensureKml(394851L);

            assertTrue(result.isPresent(), "Должен вернуть путь к сохранённому KML");
            Path target = result.get();
            assertEquals(kmlDir.resolve("394851.kml"), target);
            assertTrue(Files.exists(target), "Файл KML должен появиться на диске");

            RecordedRequest request = server.takeRequest();
            assertNotNull(request, "Сервер должен получить запрос");
            assertTrue(request.getRequestLine().startsWith("GET "),
                    "Должен быть GET: " + request.getRequestLine());
            assertTrue(request.getUrl().toString().contains("exportGeozones"),
                    "URL должен вести на exportGeozones: " + request.getUrl());
            assertEquals("394851", request.getUrl().queryParameter("geoZones"));
            assertEquals("kml", request.getUrl().queryParameter("exportType"));
            assertEquals("test-key", request.getUrl().queryParameter("apiKey"));
        }
    }

    @Test
    @DisplayName("Не скачивает повторно свежий KML (в пределах TTL)")
    void shouldSkipDownloadWhenKmlIsFresh() throws Exception {
        Path kmlDir = tempDir.resolve("kml");
        Files.createDirectories(kmlDir);
        Files.writeString(kmlDir.resolve("10.kml"), KML_BODY);

        InternalHttpClient client = mock(InternalHttpClient.class);
        KmlSyncService service = new KmlSyncService(client, apiConfig("http://test.local"), kmlDir);

        Optional<Path> result = service.ensureKml(10L);

        assertTrue(result.isPresent());
        verify(client, never()).sendRequest(any(), any());
    }

    @Test
    @DisplayName("Обновляет KML, когда файл устарел (старше TTL 30 дней)")
    void shouldRedownloadWhenKmlIsStale() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse.Builder().code(200).body(KML_BODY).build());

            Path kmlDir = tempDir.resolve("kml");
            Files.createDirectories(kmlDir);
            Path stale = kmlDir.resolve("22.kml");
            Files.writeString(stale, "<kml/>");
            Files.setLastModifiedTime(stale,
                    FileTime.from(Instant.now().minus(31, ChronoUnit.DAYS)));

            KmlSyncService service = new KmlSyncService(
                    new InternalHttpClient(1, 1000L, 0L),
                    apiConfig(server.url("/").toString()), kmlDir);

            Optional<Path> result = service.ensureKml(22L);

            assertTrue(result.isPresent(), "Устаревший KML должен быть перекачан");
            assertEquals(KML_BODY, Files.readString(result.get()),
                    "Содержимое должно замениться на свежее");
            assertNotNull(server.takeRequest(), "Должен был уйти повторный запрос");
        }
    }

    @Test
    @DisplayName("При ошибке сервера (5xx) tmp-файл удаляется и KML не создаётся")
    void shouldCleanupTempOnServerError() throws Exception {
        InternalHttpClient client = mock(InternalHttpClient.class);
        HttpResponse<Path> errorResponse = mock(HttpResponse.class);
        when(errorResponse.statusCode()).thenReturn(500);
        when(client.sendRequest(any(), any())).thenAnswer(inv -> errorResponse);

        Path kmlDir = tempDir.resolve("kml");
        KmlSyncService service = new KmlSyncService(client, apiConfig("http://test.local"), kmlDir);

        Optional<Path> result = service.ensureKml(5L);

        assertTrue(result.isEmpty(), "При 5xx не должно быть результата");
        assertFalse(Files.exists(kmlDir.resolve("5.kml")), "Целевой KML не должен появиться");
        boolean noTempLeft = !Files.exists(kmlDir)
                || Files.list(kmlDir).noneMatch(p -> p.getFileName().toString().endsWith(".tmp"));
        assertTrue(noTempLeft, "Временный файл .tmp должен быть удалён");
    }

    @Test
    @DisplayName("syncKml скачивает только отсутствующие поля и возвращает число успехов")
    void shouldSyncOnlyMissingFields() throws Exception {
        Path kmlDir = tempDir.resolve("kml");
        Files.createDirectories(kmlDir);
        Files.writeString(kmlDir.resolve("1.kml"), KML_BODY); // свежий — не трогаем

        InternalHttpClient client = mock(InternalHttpClient.class);
        when(client.sendRequest(any(), any())).thenReturn(null); // сервер недоступен

        KmlSyncService service = new KmlSyncService(client, apiConfig("http://test.local"), kmlDir);

        int downloaded = service.syncKml(List.of(1L, 2L, 3L));

        // 1 свежий (skip), 2 и 3 отсутствуют → попытка скачать, но клиент вернул null.
        assertEquals(0, downloaded, "Сервер недоступен — ничего не скачано");
        verify(client, times(2)).sendRequest(any(), any());
        assertFalse(Files.exists(kmlDir.resolve("2.kml")));
        assertFalse(Files.exists(kmlDir.resolve("3.kml")));
    }
}