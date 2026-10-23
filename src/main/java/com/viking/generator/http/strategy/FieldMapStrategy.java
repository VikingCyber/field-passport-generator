package com.viking.generator.http.strategy;

import com.viking.generator.model.common.SourceType;
import com.viking.generator.model.geometry.FieldGeometry;
import com.viking.generator.model.media.FieldMapImage;
import com.viking.generator.model.media.ImageSource;
import com.viking.generator.service.FieldMapRenderer;
import com.viking.generator.service.KmlSyncService;
import com.viking.generator.util.KmlGeometryParser;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Стратегия «снимок поля с карты»: гарантирует наличие локального KML геозоны,
 * разбирает его в геометрию и рендерит PNG-снимок (OSM-подложка + контур поля)
 * в кэш {@code cacheBaseDir/<fieldId>/maps/fieldmap_<fieldId>.png}.
 *
 * <p>Аналог {@link ChartStrategy}: процесс порционный — синхронизация KML выполняется
 * заранее в {@link #synchronize(Set)}, а рендер — лениво при {@link #process(ImageSource)}.</p>
 */
public class FieldMapStrategy implements ImageProviderStrategy {

    private static final Logger log = LoggerFactory.getLogger(FieldMapStrategy.class);

    private static final String FILE_PREFIX = "fieldmap_";

    private final KmlSyncService kmlSyncService;
    private final KmlGeometryParser geometryParser;
    private final FieldMapRenderer renderer;
    private final Path cachePath;
    private final String subDir;
    private final String defaultExt;

    public FieldMapStrategy(KmlSyncService kmlSyncService, KmlGeometryParser geometryParser,
            FieldMapRenderer renderer, Path cachePath, String subDir, String defaultExt) {
        this.kmlSyncService = kmlSyncService;
        this.geometryParser = geometryParser;
        this.renderer = renderer;
        this.cachePath = cachePath;
        this.subDir = subDir;
        this.defaultExt = defaultExt;
    }

    @Override
    public Logger getLogger() {
        return log;
    }

    @Override
    public SourceType getType() {
        return SourceType.FIELD_MAP;
    }

    /** Предварительная синхронизация: скачивает KML всех полей (обновляет устаревшие). */
    @Override
    public void synchronize(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Set<Long> fieldIds = new HashSet<>();
        for (String id : ids) {
            try {
                fieldIds.add(Long.valueOf(id));
            } catch (NumberFormatException e) {
                log.warn("Некорректный fieldId '{}' в синхронизации KML, пропущен", id);
            }
        }
        int downloaded = kmlSyncService.syncKml(fieldIds);
        log.info("Синхронизация KML полей: полей {}, скачано/обновлено {}", fieldIds.size(),
                downloaded);
    }

    @Override
    public void process(ImageSource source) {
        process(source, false);
    }

    @Override
    public void process(ImageSource source, boolean force) {
        if (!(source instanceof FieldMapImage map)) {
            return;
        }
        if (!force && map.hasImage()) {
            return;
        }
        try {
            String id = map.getId();
            String fileName = FILE_PREFIX + id;
            Path localPath = resolvePath(cachePath, fileName + ".*",
                    fileName + "." + defaultExt, id, subDir);

            Optional<FieldGeometry> geometry = loadGeometry(id);
            if (geometry.isEmpty()) {
                return;
            }

            Optional<byte[]> fromDisk = force ? Optional.empty() : readFromDisk(localPath);
            byte[] png;
            if (fromDisk.isPresent()) {
                png = fromDisk.get();
            } else {
                png = renderer.renderPng(geometry.get());
                writeToDisk(localPath, png);
            }
            if (png != null && png.length > 0) {
                map.setImageBytes(png);
                log.debug("Снимок поля {} готов: {} байт", id, png.length);
            }
        } catch (Exception e) {
            log.error("Ошибка формирования снимка поля {}: {}", map.getId(), e.getMessage());
        }
    }

    /** Обеспечивает KML и разбирает его; при недоступности данных возвращает пусто. */
    private Optional<FieldGeometry> loadGeometry(String id) {
        Long fieldId;
        try {
            fieldId = Long.valueOf(id);
        } catch (NumberFormatException e) {
            log.warn("Некорректный fieldId '{}' для снимка поля с карты", id);
            return Optional.empty();
        }
        Optional<Path> kml = kmlSyncService.ensureKml(fieldId);
        if (kml.isEmpty()) {
            log.warn("KML для поля {} недоступен — снимок с карты не сформирован", id);
            return Optional.empty();
        }
        Optional<FieldGeometry> geometry = geometryParser.parse(kml.get());
        if (geometry.isEmpty()) {
            log.warn("Не удалось разобрать KML поля {} ({})", id, kml.get());
        }
        return geometry;
    }
}
