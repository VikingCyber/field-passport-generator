package com.viking.field_passport_generator.data.provider;


import com.viking.field_passport_generator.config.record.LocalFilesConfig;
import com.viking.field_passport_generator.data.aggregator.FieldDataAggregator;
import com.viking.field_passport_generator.data.dto.*;
import com.viking.field_passport_generator.model.FieldPassport;
import com.viking.field_passport_generator.util.JsonDataParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

public class FileDataProvider implements DataProvider {
    private static final Logger log = LoggerFactory.getLogger(FileDataProvider.class);

    private final JsonDataParser jsonDataParser;
    private final FieldDataAggregator aggregator;
    private final LocalFilesConfig config;

    public FileDataProvider(JsonDataParser jsonDataParser, FieldDataAggregator aggregator, LocalFilesConfig config) {
        this.jsonDataParser = jsonDataParser;
        this.aggregator = aggregator;
        this.config = config;
    }

    @Override
    public List<FieldPassport> getPassportsData() {
        log.info("Загрузка сырых данных с физического диска...");

        try (InputStream fieldsIs = Files.newInputStream(config.fieldDataPath());
             InputStream operationIs = Files.newInputStream(config.operationsPath());
             InputStream tmcIs = Files.newInputStream(config.tmcPath());
             InputStream notesIs = Files.newInputStream(config.notesPath());
             InputStream unitsIs = Files.newInputStream(config.unitsPath())) {

            RawApiResponse fieldsResponse = jsonDataParser.parse(fieldsIs, RawApiResponse.class);
            RawOperationsResponse opsResponse = jsonDataParser.parse(operationIs, RawOperationsResponse.class);
            RawGoodsResponse goodsResponse = jsonDataParser.parse(tmcIs, RawGoodsResponse.class);
            RawNotesResponse notesResponse = jsonDataParser.parse(notesIs, RawNotesResponse.class);
            RawMachineResponse machineResponse = jsonDataParser.parse(unitsIs, RawMachineResponse.class);

            return aggregator.aggregate(fieldsResponse, opsResponse, goodsResponse, notesResponse, machineResponse);
        } catch (Exception e) {
            log.error("КРИТИЧЕСКАЯ ОШИБКА: Не удалось прочитать JSON-файлы с диска. Проверьте пути в application.yml!", e);
            throw new RuntimeException("Failed to load passport data from external files", e);
        }
    }
}
