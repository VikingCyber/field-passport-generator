package com.viking.field_passport_generator.data.aggregator;

import com.viking.field_passport_generator.data.dictionary.MachineDictionary;
import com.viking.field_passport_generator.data.dictionary.TmcDictionary;
import com.viking.field_passport_generator.data.dto.*;
import com.viking.field_passport_generator.mapper.NoteMapper;
import com.viking.field_passport_generator.mapper.OperationMapper;
import com.viking.field_passport_generator.mapper.PassportMapper;
import com.viking.field_passport_generator.mapper.TechJournalMapper;
import com.viking.field_passport_generator.model.FieldPassport;
import com.viking.field_passport_generator.model.NoteSection;
import com.viking.field_passport_generator.model.OperationTableRow;
import com.viking.field_passport_generator.model.TechJournalTableRow;
import com.viking.field_passport_generator.util.YearUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class FieldDataAggregator {
    private final OperationMapper operationMapper;
    private final NoteMapper noteMapper;
    private final TechJournalMapper techJournalMapper;
    private final ZoneId timezone;

    public FieldDataAggregator(OperationMapper operationMapper, NoteMapper noteMapper,
                               TechJournalMapper techJournalMapper, ZoneId timezone) {
        this.operationMapper = operationMapper;
        this.noteMapper = noteMapper;
        this.techJournalMapper = techJournalMapper;
        this.timezone = timezone;
    }

    public List<FieldPassport> aggregate(RawApiResponse fieldsResponse, RawOperationsResponse opsResponse,
                                         RawGoodsResponse goodsResponse, RawNotesResponse notesResponse,
                                         RawMachineResponse machineResponse) {

        TmcDictionary tmcDictionary = new TmcDictionary(goodsResponse.data());
        MachineDictionary machineDictionary = new MachineDictionary(machineResponse.data());

        Map<Long, List<RawOperationData>> opsByFieldId = opsResponse.data().stream()
                .filter(op -> op.getFieldId() != null)
                .collect(Collectors.groupingBy(RawOperationData::getFieldId));

        Map<Long, List<RawNote>> notesByFieldId = new HashMap<>();
        for (RawNote note : notesResponse.data()) {
            if (note.sources() != null) {
                for (String sourceIdStr :  note.sources()) {
                    try {
                        Long fId = Long.valueOf(sourceIdStr);
                        notesByFieldId.computeIfAbsent(fId, v -> new ArrayList<>()).add(note);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        return fieldsResponse.data().stream()
                .map(rawField -> buildPassport(rawField, opsByFieldId, tmcDictionary,
                        machineDictionary, notesByFieldId))
                .collect(Collectors.toList());
    }

    private FieldPassport buildPassport(RawFieldData rawField,
                                        Map<Long, List<RawOperationData>> opsByFieldId,
                                        TmcDictionary tmcDictionary, MachineDictionary machineDictionary,
                                        Map<Long, List<RawNote>> notesByFieldId) {
        Long fieldId = rawField.fieldId();
        int passportYear = Integer.parseInt(YearUtils.extractYear(rawField.crop()));
        List<RawOperationData> cleanOps = filterOperationsByYear(opsByFieldId.getOrDefault(fieldId, List.of()), passportYear);
        List<RawNote> fieldNotes = notesByFieldId.getOrDefault(fieldId, Collections.emptyList());
        List<OperationTableRow> operationTableRows = operationMapper.mapToTableRow(cleanOps, tmcDictionary);
        NoteSection noteSection = noteMapper.map(fieldNotes, String.valueOf(passportYear));
        List<TechJournalTableRow> techJournalTableRows = techJournalMapper.mapToTableRow(cleanOps, machineDictionary);

        return PassportMapper.mapToDomain(rawField, operationTableRows, noteSection, techJournalTableRows);
    }

    private List<RawOperationData> filterOperationsByYear(List<RawOperationData> ops, int targetYear) {
        return ops.stream()
                .filter(RawOperationData::isValid)
                .filter(r -> r.getArea() != null && r.getArea() > 0)
                .filter(r -> {
                    ZonedDateTime zdt = ZonedDateTime.ofInstant(
                            Instant.ofEpochMilli(r.getStartTime()), timezone);
                    return zdt.getYear() == targetYear;
                })
                .sorted(Comparator.comparing(RawOperationData::getOperation)
                        .thenComparing(RawOperationData::getStartTime))
                .toList();
    }
}
