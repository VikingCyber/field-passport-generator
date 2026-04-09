package com.viking.field_passport_generator.data.provider;


import com.viking.field_passport_generator.data.aggregator.FieldDataAggregator;
import com.viking.field_passport_generator.data.dto.*;
import com.viking.field_passport_generator.model.FieldPassport;
import com.viking.field_passport_generator.util.JsonDataParser;
import com.viking.field_passport_generator.util.ResourceReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;

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
        try (InputStream fieldsIs = ResourceReader.readAsStream("data/fieldData.json");
             InputStream operationIs = ResourceReader.readAsStream("data/operationsData.json");
             InputStream tmcIs = ResourceReader.readAsStream("data/tmc.json");
             InputStream notesIs = ResourceReader.readAsStream("data/notesData.json");
             InputStream unitsIs = ResourceReader.readAsStream("data/units.json") ) {

            RawApiResponse fieldsResponse = jsonDataParser.parse(fieldsIs, RawApiResponse.class);
            RawOperationsResponse opsResponse = jsonDataParser.parse(operationIs, RawOperationsResponse.class);
            RawGoodsResponse goodsResponse = jsonDataParser.parse(tmcIs, RawGoodsResponse.class);
            RawNotesResponse notesResponse = jsonDataParser.parse(notesIs, RawNotesResponse.class);
            RawMachineResponse machineResponse = jsonDataParser.parse(unitsIs, RawMachineResponse.class);



            return aggregator.aggregate(fieldsResponse, opsResponse, goodsResponse, notesResponse, machineResponse);
        } catch (Exception e) {
            log.error("Failed to load and aggregate passport data from resources", e);
            throw new RuntimeException("Failed to load and aggregate passport data from resources", e);
        }
    }
}
