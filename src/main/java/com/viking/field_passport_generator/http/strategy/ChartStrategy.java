package com.viking.field_passport_generator.http.strategy;

import com.viking.field_passport_generator.config.ChartConfig;
import com.viking.field_passport_generator.data.dto.satellite.FieldSpectralResponse;
import com.viking.field_passport_generator.mapper.ChartMapper;
import com.viking.field_passport_generator.model.ChartImage;
import com.viking.field_passport_generator.model.ImageSource;
import com.viking.field_passport_generator.model.SourceType;
import com.viking.field_passport_generator.service.ChartGenerator;
import com.viking.field_passport_generator.util.JsonDataParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.ZoneId;
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
    private final ZoneId timezone;

    public ChartStrategy(ChartGenerator chartGenerator, ChartConfig config, JsonDataParser jsonParser,
                         ChartMapper chartMapper, ZoneId timezone) {
        this.chartGenerator = chartGenerator;
        this.cachePath = config.cachePath();
        this.subDir = config.dir();
        this.defaultExt = config.extension();
        this.filePrefix = config.filePrefix();
        this.jsonParser = jsonParser;
        this.chartMapper = chartMapper;
        this.timezone = timezone;
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
        try {
            if (!(source instanceof ChartImage chart) || chart.hasImage()) { return; }

            String id = chart.getId();
            String fileName = filePrefix + id + "_" + chart.getYear();
            Path localPath = resolvePath(cachePath,fileName + ".*", fileName + "." + defaultExt, id, subDir);
            System.out.println(">>> localPath = " + localPath.toAbsolutePath());
            readFromDisk(localPath).or(() -> {
                log.info("Cache is empty, preparing data for chart: {}. Path: {}", chart.getTitle(), localPath);

                // 1. Пытаемся прочитать историю прямо здесь, когда это реально нужно
                Path historyFile = cachePath.resolve(id).resolve("history.json");

                return Optional.ofNullable(jsonParser.parse(historyFile, FieldSpectralResponse.class))
                        .map(FieldSpectralResponse::data)
                        .map(chartMapper::toChartPoints)
                        .map(points -> points.stream()
                                .filter(p ->  p.date().getYear() == chart.getYear())
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
            }).ifPresent(chart::setImageBytes);
        } catch (Exception e) {
            log.error("Ошибка та самая: ", e);
        }

    }

    @Override
    public SourceType getType() {
        return SourceType.CHART;
    }
}
