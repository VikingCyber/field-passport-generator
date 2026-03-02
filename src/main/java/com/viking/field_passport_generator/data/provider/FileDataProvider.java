package com.viking.field_passport_generator.data.provider;


import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.viking.field_passport_generator.data.aggregator.FieldDataAggregator;
import com.viking.field_passport_generator.data.dictionary.TmcDictionary;
import com.viking.field_passport_generator.data.dto.*;
import com.viking.field_passport_generator.utils.JsonDataParser;
import com.viking.field_passport_generator.utils.ResourceReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.viking.field_passport_generator.mappers.PassportMapper;
import com.viking.field_passport_generator.mappers.OperationDataMapper;
import com.viking.field_passport_generator.models.FieldPassport;
import com.viking.field_passport_generator.models.OperationTableRow;
import com.viking.field_passport_generator.utils.YearUtils;

public class FileDataProvider implements DataProvider {
    private static final Logger log = LoggerFactory.getLogger(FileDataProvider.class);
    private final JsonDataParser jsonDataParser = new JsonDataParser();
    private final ResourceReader resourceReader = new ResourceReader();
    private final FieldDataAggregator aggregator = new FieldDataAggregator();
    private final OperationDataMapper operationDataMapper = new OperationDataMapper();


    @Override
    public List<FieldPassport> getPassportsData() {
        try (InputStream fieldsIs = resourceReader.readAsStream("fieldData.json");
                InputStream operationIs = resourceReader.readAsStream("operationsData.json");
                InputStream tmcIs = resourceReader.readAsStream("tmc.json")) {

            RawApiResponse fieldsResponse = jsonDataParser.parse(fieldsIs, RawApiResponse.class);
            RawOperationsResponse opsResponse = jsonDataParser.parse(operationIs, RawOperationsResponse.class);
            RawGoodsResponse goodsResponse = jsonDataParser.parse(tmcIs, RawGoodsResponse.class);

            return aggregator.aggregate(fieldsResponse, opsResponse, goodsResponse);
        } catch (Exception e) {
            log.error("Критическая ошибка при чтении JSON файлов: {}", e.getMessage());
            throw new RuntimeException("Ошибка загрузки данных из файла", e);
        }
    }
}
