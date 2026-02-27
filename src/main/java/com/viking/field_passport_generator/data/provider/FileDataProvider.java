package com.viking.field_passport_generator.data.provider;


import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.viking.field_passport_generator.data.dictionary.TmcDictionary;
import com.viking.field_passport_generator.data.dto.*;
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
    private final OperationDataMapper operationDataMapper = new OperationDataMapper();
    private final ObjectMapper mapper = JsonMapper.builder()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build();

    @Override
    public List<FieldPassport> getPassportsData() {
        try (InputStream fieldsIs = getClass().getClassLoader().getResourceAsStream("fieldData.json");
                InputStream operationIs = getClass().getClassLoader().getResourceAsStream("operationsData.json");
                InputStream tmcIs = getClass().getClassLoader().getResourceAsStream("tmc.json")) {
            
            if (fieldsIs == null || operationIs == null || tmcIs == null) {
                throw new RuntimeException("Один из файлов с данными не найден в ресурсах");
            }

            RawApiResponse fieldsResponse = mapper.readValue(fieldsIs, RawApiResponse.class);
            RawOperationsResponse opsResponse = mapper.readValue(operationIs, RawOperationsResponse.class);
            RawGoodsResponse goodsResponse = mapper.readValue(tmcIs, RawGoodsResponse.class);

            TmcDictionary tmcDictionary = new TmcDictionary(goodsResponse.data());
            log.info("Загружено {} ТМЦ из справочника", tmcDictionary.size());

            Map<String, List<RawOperationData>> opsByFieldName = opsResponse.data().stream()
                .filter(op -> op.getGeoZone() != null)
                .collect(Collectors.groupingBy(RawOperationData::getGeoZone));

            return fieldsResponse.data().stream()
                    .map(rawField -> buildPassport(rawField, opsByFieldName, tmcDictionary))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Критическая ошибка при чтении JSON файлов: {}", e.getMessage());
            throw new RuntimeException("Ошибка загрузки данных из файла", e);
        }
    }

    private FieldPassport buildPassport(RawFieldData rawField,
                                        Map<String, List<RawOperationData>> opsByFieldName,
                                        TmcDictionary tmcDictionary) {
        String fieldName = rawField.field();
        String passportYear = YearUtils.extractYear(rawField.crop());
        List<RawOperationData> fieldOps = opsByFieldName.getOrDefault(fieldName, Collections.emptyList());
        List<OperationTableRow> tableRows = operationDataMapper.mapToTableRow(
                fieldOps, fieldName, passportYear, tmcDictionary
        );

        return PassportMapper.mapToDomain(rawField, tableRows);
    }
    
}
