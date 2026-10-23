package com.viking.generator.service;

import com.viking.generator.config.record.AgroApiConfig;
import com.viking.generator.http.InternalHttpClient;
import com.viking.generator.util.FileUtils;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Синхронизация KML-файлов геозон с АгроСигнала: скачивает геометрию полей
 * по эндпоинту {@code exportGeozones} (GET, разрешён пользователем) в локальную
 * директорию {@code <kmlDir>/<fieldId>.kml} и обновляет устаревшие файлы.
 *
 * <p>TTL обновления — 30 дней (по образцу {@code SatelliteStrategy.METADATA_TTL_DAYS}).</p>
 */
public class KmlSyncService {

    private static final Logger log = LoggerFactory.getLogger(KmlSyncService.class);

    /** Эндпоинт выгрузки геозон в KML (baseUrl из {@link AgroApiConfig}). */
    public static final String EXPORT_GEOZONES_ENDPOINT = "exportGeozones";

    /** Максимальный возраст KML-файла, после которого он обновляется заново. */
    public static final long TTL_DAYS = 30L;

    private final InternalHttpClient httpClient;
    private final AgroApiConfig apiConfig;
    private final Path kmlDir;

    public KmlSyncService(InternalHttpClient httpClient, AgroApiConfig apiConfig, Path kmlDir) {
        this.httpClient = httpClient;
        this.apiConfig = apiConfig;
        this.kmlDir = kmlDir;
    }

    /** Путь к KML-файлу поля. */
    public Path kmlPath(Long fieldId) {
        return kmlDir.resolve(fieldId + ".kml");
    }

    /** Скачивает KML для всех переданных полей, пропуская свежие. Возвращает число скачанных. */
    public int syncKml(Collection<Long> fieldIds) {
        if (fieldIds == null || fieldIds.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        int downloaded = 0;
        for (Long fieldId : fieldIds) {
            if (downloadIfStale(fieldId, now)) {
                downloaded++;
            }
        }
        return downloaded;
    }

    /**
     * Гарантирует наличие локального KML для поля: возвращает путь к файлу,
     * скачивая его при отсутствии или устаревании. При сбое скачивания возвращает
     * {@code Optional.empty()}.
     */
    public Optional<Path> ensureKml(Long fieldId) {
        Path target = kmlPath(fieldId);
        Instant now = Instant.now();
        if (isKmlFresh(target, now)) {
            log.debug("KML для поля {} уже актуален: {}", fieldId, target);
            return Optional.of(target);
        }
        return downloadKml(fieldId, now);
    }

    /** Карта «id → есть ли свежий KML», используется стратегией при предварительной синхронизации. */
    public Set<Long> freshFields(Collection<Long> fieldIds) {
        Instant now = Instant.now();
        return fieldIds.stream()
                .filter(id -> isKmlFresh(kmlPath(id), now))
                .collect(Collectors.toSet());
    }

    private boolean downloadIfStale(Long fieldId, Instant now) {
        Path target = kmlPath(fieldId);
        if (isKmlFresh(target, now)) {
            return false;
        }
        return downloadKml(fieldId, now).isPresent();
    }

    private boolean isKmlFresh(Path target, Instant now) {
        if (!Files.exists(target)) {
            return false;
        }
        try {
            Instant modified = Files.getLastModifiedTime(target).toInstant();
            return modified.isAfter(now.minus(TTL_DAYS, ChronoUnit.DAYS));
        } catch (IOException e) {
            log.warn("Не удалось прочитать время KML {}. Считаем файл устаревшим.", target);
            return false;
        }
    }

    private Optional<Path> downloadKml(Long fieldId, Instant now) {
        Path target = kmlPath(fieldId);
        Path tempFile = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(kmlDir);
            HttpRequest request = buildRequest(fieldId);
            HttpResponse<Path> response =
                    httpClient.sendRequest(request, HttpResponse.BodyHandlers.ofFile(tempFile));
            if (response == null || response.statusCode() != 200) {
                FileUtils.deleteWithRetries(tempFile);
                log.warn("Запрос KML для поля {} вернул ошибку ({}). Файл не обновлён.",
                        fieldId, response == null ? "Circuit Breaker" : response.statusCode());
                return Optional.empty();
            }
            // Проверяем, что ответ — валидный KML: содержит тег <kml>.
            if (!isKmlContent(tempFile)) {
                FileUtils.deleteWithRetries(tempFile);
                log.warn("Ответ по KML поля {} не похож на KML (пустой или HTML).", fieldId);
                return Optional.empty();
            }
            FileUtils.atomicMoveWithRetries(tempFile, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            log.info("KML поля {} обновлён: {}", fieldId, target.getFileName());
            return Optional.of(target);
        } catch (IllegalArgumentException e) {
            FileUtils.deleteWithRetries(tempFile);
            log.warn("Неверный конфиг URL для KML поля {}: {}", fieldId, e.getMessage());
            return Optional.empty();
        } catch (IOException e) {
            FileUtils.deleteWithRetries(tempFile);
            log.warn("Ошибка скачивания KML поля {}: {}", fieldId, e.getMessage());
            return Optional.empty();
        }
    }

    private HttpRequest buildRequest(Long fieldId) {
        HttpUrl baseUrl = HttpUrl.parse(apiConfig.baseUrl());
        if (baseUrl == null) {
            throw new IllegalArgumentException("Неверный базовый URL: " + apiConfig.baseUrl());
        }
        HttpUrl fullUrl = baseUrl.resolve(EXPORT_GEOZONES_ENDPOINT);
        if (fullUrl == null) {
            throw new IllegalArgumentException("Неверный эндпоинт: " + EXPORT_GEOZONES_ENDPOINT);
        }
        HttpUrl url = fullUrl.newBuilder()
                .addQueryParameter("apiKey", apiConfig.apiKey())
                .addQueryParameter("geoZones", String.valueOf(fieldId))
                .addQueryParameter("exportType", "kml")
                .build();
        URI uri = url.uri();
        return HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .header("User-Agent", apiConfig.userAgent())
                .GET()
                .build();
    }

    /** Минимальная проверка, что файл — KML (защита от HTML-ошибок/Auth страниц). */
    private boolean isKmlContent(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length < 16) {
                return false;
            }
            String head = new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                    .trim()
                    .toLowerCase();
            return head.startsWith("<kml") || head.contains("<kml");
        } catch (IOException e) {
            return false;
        }
    }
}
