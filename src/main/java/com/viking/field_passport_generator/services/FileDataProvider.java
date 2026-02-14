package com.viking.field_passport_generator.services;


import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.viking.field_passport_generator.dto.RawApiResponse;
import com.viking.field_passport_generator.dto.RawOperationData;
import com.viking.field_passport_generator.dto.RawOperationsResponse;
import com.viking.field_passport_generator.mappers.PassportMapper;
import com.viking.field_passport_generator.mappers.WorkDataMapper;
import com.viking.field_passport_generator.models.FieldPassport;
import com.viking.field_passport_generator.models.OperationTableRow;
import com.viking.field_passport_generator.utils.YearUtils;

public class FileDataProvider implements DataProvider {
    private static final Logger log = LoggerFactory.getLogger(FileDataProvider.class);
    private final WorkDataMapper workDataMapper = new WorkDataMapper();
    private final ObjectMapper mapper = JsonMapper.builder()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build();

    @Override
    public List<FieldPassport> getPassportsData() {
        try (InputStream fieldsIs = getClass().getClassLoader().getResourceAsStream("fieldData.json");
                InputStream operationIs = getClass().getClassLoader().getResourceAsStream("operationsData.json")) {
            
            if (fieldsIs == null || operationIs == null) {
                throw new RuntimeException("Один из файлов с данными не найден в ресурасах");
            }

            RawApiResponse fieldsResponse = mapper.readValue(fieldsIs, RawApiResponse.class);
            RawOperationsResponse opsResponse = mapper.readValue(operationIs, RawOperationsResponse.class);

            Map<String, List<RawOperationData>> opsByFieldName = opsResponse.data().stream()
                .filter(op -> op.geoZone() != null)
                .collect(Collectors.groupingBy(RawOperationData::geoZone));

            return fieldsResponse.data().stream()
                .map(rawField -> {
                String fieldName = rawField.field();
                String passportYear = YearUtils.extractYear(rawField.crop());
                List<RawOperationData> fieldOps = opsByFieldName.getOrDefault(fieldName, Collections.emptyList());
                List<OperationTableRow> tableRows = workDataMapper.mapToTableRow(fieldOps, fieldName, passportYear);
                return PassportMapper.mapToDomain(rawField, tableRows);
            })
            .toList();

        } catch (Exception e) {
            log.error("Критическая ошибка при чтении JSON файлов: {}", e.getMessage());
            throw new RuntimeException("Ошибка загрузки данных из файла", e);
        }
    }
    
}
