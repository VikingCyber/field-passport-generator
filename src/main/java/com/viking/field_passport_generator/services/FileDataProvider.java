package com.viking.field_passport_generator.services;


import java.io.InputStream;
import java.util.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.viking.field_passport_generator.dto.RawApiResponse;
import com.viking.field_passport_generator.mappers.PassportMapper;
import com.viking.field_passport_generator.models.FieldPassport;

public class FileDataProvider implements DataProvider {
    private final ObjectMapper mapper = JsonMapper.builder()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build();

    @Override
    public List<FieldPassport> getPassportsData() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("fieldData.json")) {

            RawApiResponse response = mapper.readValue(is, RawApiResponse.class);

            return response.data().stream().map(PassportMapper::mapToDomain).toList();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка загрузки данных из файла", e);
        }
    }
    
}
