package com.viking.field_passport_generator.data.provider;


import java.io.InputStream;
import java.util.List;

import com.viking.field_passport_generator.data.aggregator.FieldDataAggregator;
import com.viking.field_passport_generator.data.dto.*;
import com.viking.field_passport_generator.util.JsonDataParser;
import com.viking.field_passport_generator.util.ResourceReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.viking.field_passport_generator.model.FieldPassport;

public class FileDataProvider implements DataProvider {
    private static final Logger log = LoggerFactory.getLogger(FileDataProvider.class);

    private final JsonDataParser jsonDataParser;
    private final FieldDataAggregator aggregator;

    public FileDataProvider(JsonDataParser jsonDataParser, FieldDataAggregator aggregator) {
        this.jsonDataParser = jsonDataParser;
        this.aggregator = aggregator;
    }

    @Override
    public List<FieldPassport> getPassportsData() {
        try (InputStream fieldsIs = ResourceReader.readAsStream("fieldData.json");
                InputStream operationIs = ResourceReader.readAsStream("operationsData.json");
                InputStream tmcIs = ResourceReader.readAsStream("tmc.json");
                InputStream notesIs = ResourceReader.readAsStream("notesData.json")) {

            RawApiResponse fieldsResponse = jsonDataParser.parse(fieldsIs, RawApiResponse.class);
            RawOperationsResponse opsResponse = jsonDataParser.parse(operationIs, RawOperationsResponse.class);
            RawGoodsResponse goodsResponse = jsonDataParser.parse(tmcIs, RawGoodsResponse.class);
            RawNotesResponse notesResponse = jsonDataParser.parse(notesIs, RawNotesResponse.class);

            return aggregator.aggregate(fieldsResponse, opsResponse, goodsResponse, notesResponse);
        } catch (Exception e) {
            e.printStackTrace(); // ВЫВЕДЕТ ТОЧНУЮ ПРИЧИНУ В КОНСОЛЬ
            log.error("Детали Jackson: {}", e.getMessage());
            throw new RuntimeException("Ошибка парсинга", e);
        }
    }
}
