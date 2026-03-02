package com.viking.field_passport_generator.data.aggregator;

import com.viking.field_passport_generator.data.dictionary.TmcDictionary;
import com.viking.field_passport_generator.data.dto.*;
import com.viking.field_passport_generator.mappers.OperationDataMapper;
import com.viking.field_passport_generator.mappers.PassportMapper;
import com.viking.field_passport_generator.models.FieldPassport;
import com.viking.field_passport_generator.models.OperationTableRow;
import com.viking.field_passport_generator.utils.YearUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FieldDataAggregator {
    private final OperationDataMapper operationMapper;

    public FieldDataAggregator(OperationDataMapper operationMapper) {
        this.operationMapper = operationMapper;
    }

    public List<FieldPassport> aggregate(RawApiResponse fieldsResponse,
                                         RawOperationsResponse opsResponse,
                                         RawGoodsResponse goodsResponse) {

        TmcDictionary tmcDictionary = new TmcDictionary(goodsResponse.data());

        Map<String, List<RawOperationData>> opsByFieldName = opsResponse.data().stream()
                .filter(op -> op.getGeoZone() != null)
                .collect(Collectors.groupingBy(RawOperationData::getGeoZone));

        return fieldsResponse.data().stream()
                .map(rawField -> buildPassport(rawField, opsByFieldName, tmcDictionary))
                .collect(Collectors.toList());
    }

    private FieldPassport buildPassport(RawFieldData rawField,
                                        Map<String, List<RawOperationData>> opsByFieldName,
                                        TmcDictionary tmcDictionary) {
        String fieldName = rawField.field();
        String passportYear = YearUtils.extractYear(rawField.crop());
        List<RawOperationData> fieldOps = opsByFieldName.getOrDefault(fieldName, Collections.emptyList());
        List<OperationTableRow> tableRows = operationMapper.mapToTableRow(
                fieldOps, fieldName, passportYear, tmcDictionary
        );

        return PassportMapper.mapToDomain(rawField, tableRows);
    }
}
