package com.viking.generator.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viking.generator.data.dto.DownloadInfo;
import com.viking.generator.data.dto.note.AttachmentResponse;
import com.viking.generator.data.dto.note.Data;
import com.viking.generator.util.ImageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class NoteImageLoader {
    private static final Logger log = LoggerFactory.getLogger(NoteImageLoader.class);

    private final String attachmentsEndpoint;
    private final Semaphore networkSemaphore = new Semaphore(10);

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final InternalHttpClient internalClient;
    private final String baseUrl;
    private final String apiKey;
    private final String userAgent;

    public NoteImageLoader(InternalHttpClient internalClient, String baseUrl, String apiKey,
                           String userAgent, String attachmentsEndpoint) {
        this.internalClient = internalClient;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.userAgent = userAgent;
        this.attachmentsEndpoint = attachmentsEndpoint;
    }

    public LoadResult load(Collection<String> ids) {
        List<String> idList = new ArrayList<>(ids);
        LinkLookup lookup = lookupLinks(idList);
        Map<String, DownloadInfo> links = lookup.links();
        Map<String, LoadedResource> images = new ConcurrentHashMap<>();
        AtomicInteger failedDownloads = new AtomicInteger(0);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            links.forEach((id, info) -> executor.submit(() -> {
                try {
                    networkSemaphore.acquire();
                    byte[] bytes = downloadBytes(info.url());
                    if (bytes != null) {
                        images.put(id, new LoadedResource(bytes, info.extension()));
                    } else {
                        failedDownloads.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("Network error for ID {}: {}", id, e.getMessage());
                } finally {
                    networkSemaphore.release();
                }
            }));
        }

        if (failedDownloads.get() > 0) {
            log.warn("Download issues: {} files failed to download", failedDownloads.get());
        }

        return new LoadResult(images, idList.size(), links.size(), failedDownloads.get(),
                Map.of(), lookup.confirmedAbsent());
    }

    public Map<String, DownloadInfo> fetchDownloadUrls(List<String> ids) {
        return lookupLinks(new ArrayList<>(ids)).links();
    }

    /**
     * Запрашивает у API ссылки на вложения и заодно определяет, какие из запрошенных id
     * подтверждённо отсутствуют (ответ 200 + success:true, но записи с id нет в data).
     * Сетевые ошибки и не-200 в «подтверждённое отсутствие» НЕ попадают — их нельзя
     * кэшировать как «мёртвые», они могут быть временными.
     */
    private LinkLookup lookupLinks(List<String> ids) {
        try {
            String jsonBody = objectMapper.writeValueAsString(Map.of("ids", ids));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl).resolve(attachmentsEndpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("User-Agent", userAgent)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = internalClient.sendRequest(request, HttpResponse.BodyHandlers.ofString());

            if (response != null && response.statusCode() == 200) {
                return parseLinkResponse(response.body(), ids);
            }
        } catch (Exception e) {
            log.error("Error executing request: {}", e.getMessage());
        }
        return LinkLookup.empty();
    }

    private LinkLookup parseLinkResponse(String body, List<String> requested) throws JsonProcessingException {
        AttachmentResponse response = objectMapper.readValue(body, AttachmentResponse.class);
        if (response == null || !response.success() || response.data() == null) {
            return LinkLookup.empty();
        }
        Map<String, DownloadInfo> links = response.data().stream()
                .filter(d -> d.meta() != null && d.meta().resourceUrl() != null)
                .collect(Collectors.toMap(
                        Data::id,
                        d -> new DownloadInfo(
                                d.meta().resourceUrl(),
                                ImageUtils.determineExtension(d)
                                ),
                                (existing, replacement) -> existing
                ));
        Set<String> confirmedAbsent = requested.stream()
                .filter(id -> !links.containsKey(id))
                .collect(Collectors.toSet());
        return new LinkLookup(links, confirmedAbsent);
    }

    /** Результат разрешения ссылок: найденные ссылки + подтверждённо отсутствующие id. */
    private record LinkLookup(Map<String, DownloadInfo> links, Set<String> confirmedAbsent) {
        static LinkLookup empty() {
            return new LinkLookup(Map.of(), Set.of());
        }
    }

    public byte[] downloadBytes(String path) {
        return internalClient.downloadBytes(this.baseUrl, path, this.userAgent);
    }
}
