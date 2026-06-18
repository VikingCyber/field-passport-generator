package com.viking.field_passport_generator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viking.field_passport_generator.config.record.AgroApiConfig;
import com.viking.field_passport_generator.config.record.AgroSyncConfig;
import com.viking.field_passport_generator.config.record.LocalFilesConfig;
import com.viking.field_passport_generator.data.dto.CropRotationRequest;
import com.viking.field_passport_generator.data.dto.UnitOperationsRequest;
import com.viking.field_passport_generator.http.InternalHttpClient;
import com.viking.field_passport_generator.util.FileUtils;
import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AgroMetadataSyncService {
    private static final Logger log = LoggerFactory.getLogger(AgroMetadataSyncService.class);
    private final InternalHttpClient httpClient;
    private final AgroApiConfig apiConfig;
    private final AgroSyncConfig syncConfig;
    private final LocalFilesConfig localConfig;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public AgroMetadataSyncService(InternalHttpClient httpClient, AgroApiConfig apiConfig, AgroSyncConfig syncConfig,
                                   LocalFilesConfig localConfig) {
        this.httpClient = httpClient;
        this.apiConfig = apiConfig;
        this.syncConfig = syncConfig;
        this.localConfig = localConfig;
    }

    public void syncAllMetadata() throws IOException {
        log.info("==> Запуск синхронизации метаданных с API Агросигнал через стриминг на диск...");
        List<FilePair> atomicMoveList = new ArrayList<>();
        try {
            log.info("Скачивание справочника ТМЦ (/goods)...");
            downloadGet(apiConfig.tmcEndpoint(), Map.of(), localConfig.tmcPath(), atomicMoveList);
            log.info("Скачивание списка техники (/units)...");
            downloadGet(apiConfig.unitsEndpoint(), Map.of("limit", "500"), localConfig.unitsPath(), atomicMoveList);

            log.info("Скачивание заметок агрономов (/notes)...");
            Map<String, String> notesParam = Map.of(
                    "from", syncConfig.fromDate(),
                    "to", syncConfig.toDate()
            );
            downloadGet(apiConfig.notesEndpoint(), notesParam, localConfig.notesPath(), atomicMoveList);

            String formatFixFrom = syncConfig.fromDate().replace("Z", ".000Z"); // "2023-01-01T00:00:00.000Z"
            String formatFixTo = syncConfig.toDate().replace("Z", ".000Z");
            log.info("Формирование POST-запроса для отчета севооборота (cropRotation)...");
            CropRotationRequest requestBody = new CropRotationRequest(
                    formatFixFrom,
                    formatFixTo,
                    List.of(), List.of(),
                    new CropRotationRequest.Filters(List.of(), List.of(), List.of(), List.of()),
                    25, 1, 0,
                    "cropRotation", "Monitor", -420
            );

            String cropJson = objectMapper.writeValueAsString(requestBody);
            log.info("===> СИНХРОНИЗАЦИЯ ШЛЕТ ВОТ ТАКОЙ JSON: {}", cropJson);
            downloadPost(apiConfig.fieldReportEndpoint(), Map.of(), cropJson, localConfig.fieldDataPath(), atomicMoveList);

            // ==================== НАЧАЛО ПОМЕСЯЧНОЙ СИНХРОНИЗАЦИИ ОПЕРАЦИЙ ТЕХНИКИ ====================
            log.info("Формирование тяжелого POST-запроса для операций техники (unitOperationsReport) помесячно...");

            Path finalTempPath = localConfig.operationsPath().resolveSibling(localConfig.operationsPath().getFileName() + ".combined.tmp");

            java.time.format.DateTimeFormatter isoFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

            try (var writer = Files.newBufferedWriter(finalTempPath)) {
                writer.write("{\"data\":[");
                boolean isFirstDataAdded = false;

                LocalDate startDate = ZonedDateTime.parse(formatFixFrom).toLocalDate();
                LocalDate endDate = ZonedDateTime.parse(formatFixTo).toLocalDate();
                LocalDate currentMonthStart = startDate.withDayOfMonth(1);

                while (!currentMonthStart.isAfter(endDate)) {
                    // Вычисляем точные границы месяца в LocalDateTime
                    java.time.LocalDateTime fromDateTime = (currentMonthStart.isBefore(startDate))
                            ? ZonedDateTime.parse(formatFixFrom).toLocalDateTime()
                            : currentMonthStart.atStartOfDay();

                    LocalDate currentMonthEnd = currentMonthStart.withDayOfMonth(currentMonthStart.lengthOfMonth());
                    java.time.LocalDateTime toDateTime = (currentMonthEnd.isAfter(endDate))
                            ? ZonedDateTime.parse(formatFixTo).toLocalDateTime()
                            : currentMonthEnd.atTime(23, 59, 59);

                    // Форматируем строго по маске (с указанием UTC таймзоны, чтобы проставилась литера Z)
                    String currentFrom = fromDateTime.atZone(java.time.ZoneOffset.UTC).format(isoFormatter);
                    String currentTo = toDateTime.atZone(java.time.ZoneOffset.UTC).format(isoFormatter);

                    log.info("==> Запрашиваю порцию операций техники за период: {} - {}", currentFrom, currentTo);

                    UnitOperationsRequest operationsRequest = new UnitOperationsRequest(
                            "unitOperationsReport",
                            currentFrom,
                            currentTo,
                            syncConfig.companyId(),
                            List.of(), List.of(),
                            new UnitOperationsRequest.Filters(List.of()),
                            25000, // Лимит на один месяц
                            1, 0, -420
                    );

                    String operationsJson = objectMapper.writeValueAsString(operationsRequest);
                    Path chunkTempFile = localConfig.operationsPath().resolveSibling("chunk_" + currentMonthStart.getYear() + "_" + currentMonthStart.getMonthValue() + ".tmp");

                    try {
                        // Используем твой стандартный эндпоинт из конфига
                        downloadPostChunk(apiConfig.fieldReportEndpoint(), Map.of(), operationsJson, chunkTempFile);

                        String chunkContent = Files.readString(chunkTempFile);
                        JsonNode rootNode = objectMapper.readTree(chunkContent);

                        if (rootNode.has("data") && rootNode.get("data").isArray() && !rootNode.get("data").isEmpty()) {
                            String arrayJson = objectMapper.writeValueAsString(rootNode.get("data"));
                            String pureObjects = arrayJson.substring(1, arrayJson.length() - 1);

                            if (!pureObjects.isBlank()) {
                                if (isFirstDataAdded) {
                                    writer.write(",");
                                }
                                writer.write(pureObjects);
                                isFirstDataAdded = true;
                                log.info("-> Успешно добавлен кусок за {}/{}", currentMonthStart.getMonth(), currentMonthStart.getYear());
                            }
                        }
                    } catch (Exception chunkEx) {
                        log.warn("❌ Пропущен месяц {}/{} из-за ошибки: {}",
                                currentMonthStart.getMonth(), currentMonthStart.getYear(), chunkEx.getMessage());
                    } finally {
                        Files.deleteIfExists(chunkTempFile);
                    }

                    currentMonthStart = currentMonthStart.plusMonths(1);
                }

                writer.write("],\"total\":100}");
            }

            atomicMoveList.add(new FilePair(finalTempPath, localConfig.operationsPath()));
// ==================== КОНЕЦ ПОМЕСЯЧНОЙ СИНХРОНИЗАЦИИ ====================

            log.info("Все данные успешно скачаны во временные файлы. Применение изменений (Atomic Move)...");
            for (FilePair pair : atomicMoveList) {
                FileUtils.atomicMoveWithRetries(pair.temp(), pair.target());
            }
            log.info("✅ Синхронизация метаданных успешно завершена!");
        } catch (Exception e) {
            log.error("Критическая ошибка при синхронизации. Очистка временных файлов...", e);
            for (FilePair pair : atomicMoveList) {
                FileUtils.deleteWithRetries(pair.temp());
            }
            throw new IOException("Синхронизация Агросигнала прервана", e);
        }
    }

    private void downloadGet(String endpoint, Map<String, String> queryParams, Path targetPath, List<FilePair> moveList) throws IOException {
        HttpUrl baseUrl = HttpUrl.parse(apiConfig.baseUrl());
        if (baseUrl == null) {
            throw new IllegalArgumentException(String.format("Неверный базовый URL: '%s'", apiConfig.baseUrl()));
        }

        HttpUrl fullUrl = baseUrl.resolve(endpoint);
        if (fullUrl == null) {
            throw new IllegalArgumentException(String.format("Не удалось построить URL: base='%s', endpoint='%s'", apiConfig.baseUrl(), endpoint));
        }

        HttpUrl.Builder urlBuilder = fullUrl.newBuilder();
        urlBuilder.addQueryParameter("apiKey", apiConfig.apiKey());

        if (queryParams != null && !queryParams.isEmpty()) {
            queryParams.forEach(urlBuilder::addQueryParameter);
        }

        URI uri = urlBuilder.build().uri();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .header("User-Agent", apiConfig.userAgent())
                .GET()
                .build();

        executeAndStreamToFile(request, targetPath, moveList);
    }

    private void downloadPost(String endpoint, Map<String, String> queryParams, String jsonBody, Path targetPath, List<FilePair> moveList) throws IOException {
        HttpUrl baseUrl = HttpUrl.parse(apiConfig.baseUrl());
        if (baseUrl == null) {
            throw new IllegalArgumentException(String.format("Неверный базовый URL: '%s'", apiConfig.baseUrl()));
        }

        HttpUrl fullUrl = baseUrl.resolve(endpoint);
        if (fullUrl == null) {
            throw new IllegalArgumentException(String.format("Не удалось построить URL: base='%s', endpoint='%s'", apiConfig.baseUrl(), endpoint));
        }

        HttpUrl.Builder urlBuilder = fullUrl.newBuilder();
        urlBuilder.addQueryParameter("apiKey", apiConfig.apiKey());

        if (queryParams != null && !queryParams.isEmpty()) {
            queryParams.forEach(urlBuilder::addEncodedQueryParameter);
        }

        URI uri = urlBuilder.build().uri();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", apiConfig.userAgent())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        executeAndStreamToFile(request, targetPath, moveList);
    }

    /**
     * Вспомогательный метод для прямого скачивания отдельного чанка во временный файл
     */
    private void downloadPostChunk(String endpoint, Map<String, String> queryParams, String jsonBody, Path targetPath) throws IOException {
        HttpUrl baseUrl = HttpUrl.parse(apiConfig.baseUrl());
        HttpUrl fullUrl = baseUrl.resolve(endpoint);
        HttpUrl.Builder urlBuilder = fullUrl.newBuilder();
        urlBuilder.addQueryParameter("apiKey", apiConfig.apiKey());
        if (queryParams != null && !queryParams.isEmpty()) {
            queryParams.forEach(urlBuilder::addEncodedQueryParameter);
        }

        URI uri = urlBuilder.build().uri();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMinutes(2)) // На один месяц двух минут хватит за глаза
                .header("User-Agent", apiConfig.userAgent())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<Path> response = httpClient.sendRequest(request, HttpResponse.BodyHandlers.ofFile(targetPath));
        if (response == null || response.statusCode() != 200) {
            throw new IOException("Ошибка скачивания фрагмента. HTTP код: " + (response != null ? response.statusCode() : "null"));
        }
    }

    private void executeAndStreamToFile(HttpRequest request, Path targetPath, List<FilePair> moveList) throws IOException {
        Path tempFile = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
        log.debug("Скачивание трансляцией в файл: {}", tempFile.getFileName());
        HttpResponse<Path> response = httpClient.sendRequest(request, HttpResponse.BodyHandlers.ofFile(tempFile));
        if (response == null || response.statusCode() != 200) {
            FileUtils.deleteWithRetries(tempFile);
            throw new IOException("Запрос к " + request.uri() + " вернул ошибку или Circuit Breaker открыт");
        }
        moveList.add(new FilePair(tempFile, targetPath));
    }

    public record FilePair(Path temp, Path target) {}
}
