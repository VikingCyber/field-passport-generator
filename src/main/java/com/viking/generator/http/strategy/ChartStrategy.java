package com.viking.generator.http.strategy;

import com.viking.generator.config.ChartConfig;
import com.viking.generator.data.dto.satellite.FieldSpectralResponse;
import com.viking.generator.mapper.ChartMapper;
import com.viking.generator.model.media.ChartImage;
import com.viking.generator.model.media.ImageSource;
import com.viking.generator.model.common.SourceType;
import com.viking.generator.service.ChartGenerator;
import com.viking.generator.util.JsonDataParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

public class ChartStrategy implements ImageProviderStrategy {
    private static final Logger log = LoggerFactory.getLogger(ChartStrategy.class);

    private final ChartGenerator chartGenerator;
    private final JsonDataParser jsonParser;
    private final ChartMapper chartMapper;
    private final Path cachePath;
    private final String subDir;
    private final String defaultExt;
    private final String filePrefix;

    public ChartStrategy(ChartGenerator chartGenerator, ChartConfig config, JsonDataParser jsonParser,
                         ChartMapper chartMapper) {
        this.chartGenerator = chartGenerator;
        this.cachePath = config.cachePath();
        this.subDir = config.dir();
        this.defaultExt = config.extension();
        this.filePrefix = config.filePrefix();
        this.jsonParser = jsonParser;
        this.chartMapper = chartMapper;
    }

    @Override
    public Logger getLogger() {
        return log;
    }

    @Override
    public void synchronize(Set<String> ids) {
        log.info("Charts are generated on demand from local satellite metadata");
    }

    @Override
    public void process(ImageSource source) {
        process(source, false);
    }

    @Override
    public void process(ImageSource source, boolean force) {
        try {
            if (!(source instanceof ChartImage chart)) { return; }
            // Если байты уже в памяти и перегенерация не запрошена — не трогаем
            if (!force && chart.hasImage()) { return; }

            String id = chart.getId();
            String fileName = filePrefix + id + "_" + chart.getYear();
            Path localPath = resolvePath(cachePath, fileName + ".*", fileName + "." + defaultExt, id, subDir);

            // При force=true игнорируем PNG в кэше и всегда перестраиваем график
            // из актуальных метаданных (history.json), перезаписывая файл на диске.
            Optional<byte[]> fromDisk = force ? Optional.empty() : readFromDisk(localPath);
            fromDisk.or(() -> generateFromHistory(chart, localPath))
                    .ifPresent(chart::setImageBytes);
        } catch (Exception e) {
            log.error("Failed to process chart : {}", e.getMessage());
        }
    }

    private Optional<byte[]> generateFromHistory(ChartImage chart, Path localPath) {
        log.info("Кэш отсутствует или запрошена перегенерация, готовим данные графика: {}. Путь: {}",
                chart.getTitle(), localPath);

        // 1. Пытаемся прочитать историю прямо здесь, когда это реально нужно
        Path historyFile = cachePath.resolve(chart.getId()).resolve("history.json");

        return Optional.ofNullable(jsonParser.parse(historyFile, FieldSpectralResponse.class))
                .map(FieldSpectralResponse::data)
                .map(chartMapper::toChartPoints)
                .map(points -> points.stream()
                        .filter(p -> p.date().getYear() == chart.getYear())
                        .toList())
                .filter(filteredPoints -> !filteredPoints.isEmpty())
                .flatMap(filteredPoints -> {
                    // 2. Наполняем объект точками перед генерацией
                    chart.setPoints(filteredPoints);
                    return chartGenerator.generateCombinedChart(chart);
                })
                .map(bytes -> {
                    writeToDisk(localPath, bytes);
                    return bytes;
                });
    }

    @Override
    public SourceType getType() {
        return SourceType.CHART;
    }
}
