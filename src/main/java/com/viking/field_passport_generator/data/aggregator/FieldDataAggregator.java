package com.viking.field_passport_generator.data.aggregator;

import com.viking.field_passport_generator.data.dictionary.TmcDictionary;
import com.viking.field_passport_generator.data.dto.*;
import com.viking.field_passport_generator.mapper.NoteMapper;
import com.viking.field_passport_generator.mapper.OperationDataMapper;
import com.viking.field_passport_generator.mapper.PassportMapper;
import com.viking.field_passport_generator.model.FieldPassport;
import com.viking.field_passport_generator.model.NoteImage;
import com.viking.field_passport_generator.model.OperationTableRow;
import com.viking.field_passport_generator.service.ImageCacheService;
import com.viking.field_passport_generator.util.YearUtils;
import com.viking.field_passport_generator.model.NoteSection;

import java.util.*;
import java.util.stream.Collectors;

public class FieldDataAggregator {
    private final OperationDataMapper operationMapper;
    private final NoteMapper noteMapper;
    private final ImageCacheService cacheService;

    public FieldDataAggregator(OperationDataMapper operationMapper, NoteMapper noteMapper,
                               ImageCacheService cacheService) {
        this.operationMapper = operationMapper;
        this.noteMapper = noteMapper;
        this.cacheService = cacheService;
    }

    public List<FieldPassport> aggregate(RawApiResponse fieldsResponse,
                                         RawOperationsResponse opsResponse,
                                         RawGoodsResponse goodsResponse, RawNotesResponse notesResponse) {

        TmcDictionary tmcDictionary = new TmcDictionary(goodsResponse.data());

        Map<String, List<RawOperationData>> opsByFieldName = opsResponse.data().stream()
                .filter(op -> op.getGeoZone() != null)
                .collect(Collectors.groupingBy(RawOperationData::getGeoZone));

        Map<String, List<RawNote>> notesByFieldId = new HashMap<>();
        for (RawNote note : notesResponse.data()) {
            List<String> sources = note.sources();
            if (sources != null) {
                for (String fieldId : sources) {
                    notesByFieldId.computeIfAbsent(fieldId, k -> new ArrayList<>()).add(note);
                }
            }
        }

        return fieldsResponse.data().stream()
                .map(rawField -> buildPassport(rawField, opsByFieldName, tmcDictionary, notesByFieldId))
                .collect(Collectors.toList());
    }

    private FieldPassport buildPassport(RawFieldData rawField,
                                        Map<String, List<RawOperationData>> opsByFieldName,
                                        TmcDictionary tmcDictionary,
                                        Map<String, List<RawNote>> notesByFieldId) {
        String fieldName = rawField.field();
        String passportYear = YearUtils.extractYear(rawField.crop());
        List<RawOperationData> fieldOps = opsByFieldName.getOrDefault(fieldName, Collections.emptyList());
        List<OperationTableRow> tableRows = operationMapper.mapToTableRow(
                fieldOps, fieldName, passportYear, tmcDictionary
        );

        String fieldId = rawField.fieldId();
        List<RawNote> fieldNotes = notesByFieldId.getOrDefault(fieldId, Collections.emptyList());
        NoteSection noteSection = noteMapper.map(fieldNotes, passportYear);


        return PassportMapper.mapToDomain(rawField, tableRows, noteSection);
    }
}
