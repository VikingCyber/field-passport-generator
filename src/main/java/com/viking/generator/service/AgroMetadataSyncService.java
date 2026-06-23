package com.viking.generator.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viking.generator.config.record.AgroApiConfig;
import com.viking.generator.config.record.AgroSyncConfig;
import com.viking.generator.config.record.LocalFilesConfig;
import com.viking.generator.data.dto.CropRotationRequest;
import com.viking.generator.data.dto.UnitOperationsRequest;
import com.viking.generator.http.InternalHttpClient;
import com.viking.generator.util.FileUtils;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Synchronizes metadata from the Agrosignal API to local files.
 *
 * <p>Downloads dictionaries, notes, crop rotation and unit operations, saving them via atomic
 * file moves. Unit operations are fetched month by month and streamed to disk.</p>
 */
public class AgroMetadataSyncService {

    private static final Logger log = LoggerFactory.getLogger(AgroMetadataSyncService.class);
    private final InternalHttpClient httpClient;
    private final AgroApiConfig apiConfig;
    private final AgroSyncConfig syncConfig;
    private final LocalFilesConfig localConfig;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Construct a new sync service with the given configurations.
     *
     * @param httpClient HTTP client for API requests.
     * @param apiConfig API endpoint configuration
     * @param syncConfig synchronization period, timezone, companyId
     * @param localConfig local file paths for downloaded data.
     */
    public AgroMetadataSyncService(InternalHttpClient httpClient, AgroApiConfig apiConfig,
            AgroSyncConfig syncConfig,
            LocalFilesConfig localConfig) {
        this.httpClient = httpClient;
        this.apiConfig = apiConfig;
        this.syncConfig = syncConfig;
        this.localConfig = localConfig;
    }

    /**
     * Runs the full metadata synchronization pipeline.
     *
     * <p>Downloads all data types into temporary files, then atomically moves them to their
     * target locations. If any step fails, all temporary files are deleted and the exception is
     * rethrown</p>
     *
     * @throws IOException synchronization step fails.
     */
    public void syncAllMetadata() throws IOException {
        log.info("==> Запуск синхронизации метаданных с API Агросигнал через стриминг на диск...");
        List<FilePair> atomicMoveList = new ArrayList<>();
        try {
            syncDictionariesAndNotes(atomicMoveList);
            syncCropRotation(atomicMoveList);
            syncUnitOperation(atomicMoveList);

            log.info("Все данные успешно скачаны во временные файлы. "
                    + "Применение изменений (Atomic Move)...");
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

    /**
     * Downloads data via a GET request and streams it to a file.
     *
     * @param endpoint API path relative to the base URL.
     * @param queryParams query parameters (maybe empty).
     * @param targetPath where to save the downloaded file.
     * @param moveList accumulates temp-target file pairs for atomic move.
     * @throws IOException if the request fails.
     * @throws IllegalArgumentException if the URL cannot be built.
     */
    private void downloadGet(String endpoint, Map<String, String> queryParams, Path targetPath,
            List<FilePair> moveList) throws IOException {
        HttpUrl baseUrl = HttpUrl.parse(apiConfig.baseUrl());
        if (baseUrl == null) {
            throw new IllegalArgumentException(
                    String.format("Неверный базовый URL: '%s'", apiConfig.baseUrl()));
        }

        HttpUrl fullUrl = baseUrl.resolve(endpoint);
        if (fullUrl == null) {
            throw new IllegalArgumentException(
                    String.format("Не удалось построить URL: base='%s', endpoint='%s'",
                            apiConfig.baseUrl(), endpoint));
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

    /**
     * Downloads data via a POST request with a JSON body.
     *
     * @param endpoint API path relative to the base URL.
     * @param queryParams query parameters (maybe empty).
     * @param jsonBody JSON payload.
     * @param targetPath where to save the downloaded file.
     * @param moveList accumulates temp-target file pairs for atomic move.
     * @throws IOException if the request fails.
     * @throws IllegalArgumentException if the URL cannot be built.
     */
    private void downloadPost(String endpoint, Map<String, String> queryParams, String jsonBody,
            Path targetPath, List<FilePair> moveList) throws IOException {
        HttpUrl baseUrl = HttpUrl.parse(apiConfig.baseUrl());
        if (baseUrl == null) {
            throw new IllegalArgumentException(
                    String.format("Неверный базовый URL: '%s'", apiConfig.baseUrl()));
        }

        HttpUrl fullUrl = baseUrl.resolve(endpoint);
        if (fullUrl == null) {
            throw new IllegalArgumentException(
                    String.format("Не удалось построить URL: base='%s', endpoint='%s'",
                            apiConfig.baseUrl(), endpoint));
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
     * Downloads a single chunk (one month) directly into a temporary file.
     *
     * @param endpoint API path relative to the base URL.
     * @param queryParams query parameters (maybe empty).
     * @param jsonBody JSON payload for the POST request.
     * @param targetPath where to save the chunk.
     * @throws IOException if the request fails or returns a non-200 status.
     */
    private void downloadPostChunk(String endpoint, Map<String, String> queryParams,
            String jsonBody, Path targetPath) throws IOException {
        HttpUrl baseUrl = HttpUrl.parse(apiConfig.baseUrl());
        if (baseUrl == null) {
            throw new IllegalArgumentException(
                    "Неверный базовый URL: '" + apiConfig.baseUrl() + "'");
        }
        HttpUrl fullUrl = baseUrl.resolve(endpoint);
        if (fullUrl == null) {
            throw new IllegalArgumentException(
                    String.format("Не удалось построить URL: base='%s', endpoint='%s'",
                            apiConfig.baseUrl(), endpoint));

        }
        HttpUrl.Builder urlBuilder = fullUrl.newBuilder();
        urlBuilder.addQueryParameter("apiKey", apiConfig.apiKey());
        if (queryParams != null && !queryParams.isEmpty()) {
            queryParams.forEach(urlBuilder::addEncodedQueryParameter);
        }

        URI uri = urlBuilder.build().uri();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", apiConfig.userAgent())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<Path> response = httpClient.sendRequest(request,
                HttpResponse.BodyHandlers.ofFile(targetPath));
        if (response == null || response.statusCode() != 200) {
            throw new IOException("Ошибка скачивания фрагмента. HTTP код: " + (response != null
                    ? response.statusCode() : "null"));
        }
    }

    /**
     * Executes an HTTP request and streams the response body to a temporary file.
     *
     * <p>The temporary file is added to the move list for later atomic move.</p>
     *
     * @param request the HTTP request to execute.
     * @param targetPath the final target path (used to derive the temp file name).
     * @param moveList accumulates temp-target file pairs for atomic move.
     * @throws IOException if the request fails.
     */
    private void executeAndStreamToFile(HttpRequest request, Path targetPath,
            List<FilePair> moveList) throws IOException {
        Path tempFile = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
        log.debug("Скачивание трансляцией в файл: {}", tempFile.getFileName());
        try {
            HttpResponse<Path> response = httpClient.sendRequest(request,
                    HttpResponse.BodyHandlers.ofFile(tempFile));
            if (response == null || response.statusCode() != 200) {
                FileUtils.deleteWithRetries(tempFile);
                throw new IOException(
                        "Запрос к " + request.uri() + " вернул ошибку или Circuit Breaker открыт");
            }
            moveList.add(new FilePair(tempFile, targetPath));
        } catch (Exception e) {
            FileUtils.deleteWithRetries(tempFile);
            throw e;
        }
    }

    /**
     * Downloads dictionaries (goods, units) and agronomist notes.
     *
     * @param moveList accumulates temp-target file pairs for atomic move.
     * @throws IOException if any download fails.
     */
    private void syncDictionariesAndNotes(List<FilePair> moveList) throws IOException {
        log.info("Скачивание справочника ТМЦ (/goods)...");
        downloadGet(apiConfig.tmcEndpoint(), Map.of(), localConfig.tmcPath(), moveList);
        log.info("Скачивание списка техники (/units)...");
        downloadGet(apiConfig.unitsEndpoint(), Map.of("limit", "500"), localConfig.unitsPath(),
                moveList);
        log.info("Скачивание заметок агрономов (/notes)...");
        Map<String, String> notesParam = Map.of(
                "from", syncConfig.fromDate().toString(),
                "to", syncConfig.toDate().toString()
        );
        downloadGet(apiConfig.notesEndpoint(), notesParam, localConfig.notesPath(), moveList);
    }

    /**
     * Downloads crop rotation data via a POST request.
     *
     * @param moveList accumulates temp-target file pairs for atomic move.
     * @throws IOException if the download fails.
     */
    private void syncCropRotation(List<FilePair> moveList) throws IOException {
        log.info("Формирование POST-запроса для отчета севооборота (cropRotation)...");
        CropRotationRequest requestBody = new CropRotationRequest(
                syncConfig.fromDate().toString(),
                syncConfig.toDate().toString(),
                List.of(), List.of(),
                new CropRotationRequest.Filters(List.of(), List.of(), List.of(), List.of()),
                25, 1, 0,
                "cropRotation", "Monitor", -420
        );
        String cropJson = objectMapper.writeValueAsString(requestBody);
        downloadPost(apiConfig.fieldReportEndpoint(), Map.of(), cropJson,
                localConfig.fieldDataPath(), moveList);
    }

    /**
     * Downloads unit operations month by month and combines them into a single file.
     *
     * <p>Each month is fetched as a separate chunk to avoid timeouts and memory issues.
     * Chunks are streamed directly into a combined temporary file.</p>
     *
     * @param moveList accumulates temp-target file pairs for atomic move.
     * @throws IOException if any monthly chunk fails.
     */
    private void syncUnitOperation(List<FilePair> moveList) throws IOException {
        log.info("Синхронизация операций техники (unitOperationsReport) помесячно...");
        Path finalTempPath = localConfig.operationsPath()
                .resolveSibling(localConfig.operationsPath().getFileName() + ".combined.tmp");
        moveList.add(new FilePair(finalTempPath, localConfig.operationsPath()));
        LocalDate startDate = syncConfig.fromDate().atZone(syncConfig.timezone()).toLocalDate();
        LocalDate endDate = syncConfig.toDate().atZone(syncConfig.timezone()).toLocalDate();
        LocalDate currentMonthStart = startDate.withDayOfMonth(1);

        try (var writer = Files.newBufferedWriter(finalTempPath)) {
            writer.write("{\"data\":[");
            boolean isFirstDataAdded = false;
            while (!currentMonthStart.isAfter(endDate)) {
                isFirstDataAdded = processSingleMonth(currentMonthStart, startDate, endDate, writer,
                        isFirstDataAdded);
                currentMonthStart = currentMonthStart.plusMonths(1);
            }
            writer.write("]}");
        }
    }

    /**
     * Downloads and writes a single month of unit operations.
     *
     * <p>Calculates the correct UTC boundaries for the given month, respecting
     * the configured timezone and overall sync period.</p>
     *
     * @param currentMonthStart first day of the month to process.
     * @param startDate overall sync start date.
     * @param endDate overall sync end date.
     * @param writer writer for the combined output file.
     * @param isFirstDataAdded whether any data has been written yet.
     * @return {@code true} if data was written, {@code false} otherwise.
     * @throws IOException if the download or write fails.
     */
    private boolean processSingleMonth(LocalDate currentMonthStart, LocalDate startDate,
            LocalDate endDate,
            Writer writer, boolean isFirstDataAdded) throws IOException {

        LocalDateTime fromDateTime = (currentMonthStart.isBefore(startDate))
                ? syncConfig.fromDate().atZone(syncConfig.timezone()).toLocalDateTime()
                : currentMonthStart.atStartOfDay();

        LocalDate currentMonthEnd = currentMonthStart.withDayOfMonth(
                currentMonthStart.lengthOfMonth());
        LocalDateTime toDateTime = (currentMonthEnd.isAfter(endDate))
                ? syncConfig.toDate().atZone(syncConfig.timezone()).toLocalDateTime()
                : currentMonthEnd.atTime(23, 59, 59);

        String currentFrom = fromDateTime.atZone(syncConfig.timezone()).toInstant().toString();
        String currentTo = toDateTime.atZone(syncConfig.timezone()).toInstant().toString();

        log.info("==> Запрашиваю порцию операций техники за период: {} - {}", currentFrom,
                currentTo);
        UnitOperationsRequest operationsRequest = new UnitOperationsRequest(
                "unitOperationsReport",
                currentFrom,
                currentTo,
                syncConfig.companyId(),
                List.of(), List.of(),
                new UnitOperationsRequest.Filters(List.of()),
                100000,
                1, 0, -420
        );
        String operationsJson = objectMapper.writeValueAsString(operationsRequest);
        Path chunkTempFile = localConfig.operationsPath().resolveSibling(
                "chunk_" + currentMonthStart.getYear() + "_" + currentMonthStart.getMonthValue()
                        + ".tmp");
        try {
            downloadPostChunk(apiConfig.fieldReportEndpoint(), Map.of(), operationsJson,
                    chunkTempFile);

            isFirstDataAdded = parseAndWriteChunkStream(chunkTempFile, writer, isFirstDataAdded);
            log.info("-> Успешно добавлен кусок за {}/{}", currentMonthStart.getMonth(),
                    currentMonthStart.getYear());
        } finally {
            FileUtils.deleteWithRetries(chunkTempFile);
        }
        return isFirstDataAdded;
    }

    /**
     * Parses a chunk file and writes its {@code data} array elements directly to the writer using
     * streaming, avoiding loading the entire file into memory.
     *
     * @param chunkFile the temporary file containing the chunk JSON.
     * @param writer writer for the combined output.
     * @param isFirstDataAdded whether any data has been written before this chunk.
     * @return {@code true} if data was written, {@code false} if the chunk was empty.
     * @throws IOException if reading or writing fails.
     */
    boolean parseAndWriteChunkStream(Path chunkFile, Writer writer, boolean isFirstDataAdded)
            throws IOException {
        try (JsonParser parser = objectMapper.getFactory().createParser(chunkFile.toFile())) {
            JsonGenerator generator = objectMapper.getFactory().createGenerator(writer);
            generator.disable(Feature.AUTO_CLOSE_TARGET);
            try (generator) {
                while (parser.nextToken() != null && parser.currentToken()
                        != JsonToken.END_OBJECT) {
                    if ("data".equals(parser.currentName())) {
                        parser.nextToken();
                        if (parser.isExpectedStartArrayToken()) {
                            while (parser.nextToken() != JsonToken.END_ARRAY) {
                                if (isFirstDataAdded) {
                                    generator.flush();
                                    writer.write(",");
                                }
                                generator.copyCurrentStructure(parser);
                                isFirstDataAdded = true;
                            }
                        }
                        break;
                    }
                }
                generator.flush();
            }
        }
        return isFirstDataAdded;
    }

    /**
     * A temporary-target file pair used for atomic move operations.
     *
     * @param temp the temporary file.
     * @param target the final target file.
     */
    public record FilePair(Path temp, Path target) {

    }
}
