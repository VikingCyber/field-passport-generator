package com.viking.generator.data.aggregator;

import com.viking.generator.data.dictionary.MachineDictionary;
import com.viking.generator.data.dictionary.TmcDictionary;
import com.viking.generator.data.dto.*;
import com.viking.generator.mapper.*;
import com.viking.generator.model.*;
import com.viking.generator.model.media.ChartImage;
import com.viking.generator.model.media.SatelliteImage;
import com.viking.generator.model.sections.CropRotation;
import com.viking.generator.model.sections.NoteSection;
import com.viking.generator.model.tables.OperationTableRow;
import com.viking.generator.model.tables.TechJournalTableRow;
import com.viking.generator.util.YearUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class FieldDataAggregator {

    private final OperationMapper operationMapper;
    private final NoteMapper noteMapper;
    private final TechJournalMapper techJournalMapper;
    private final SatelliteMapper satelliteMapper;
    private final ZoneId timezone;
    private static final Logger log = LoggerFactory.getLogger(FieldDataAggregator.class);

    private static final char KEY_SEPARATOR = '\u0000';

    public FieldDataAggregator(OperationMapper operationMapper, NoteMapper noteMapper,
            TechJournalMapper techJournalMapper, SatelliteMapper satelliteMapper, ZoneId timezone) {
        this.operationMapper = operationMapper;
        this.noteMapper = noteMapper;
        this.techJournalMapper = techJournalMapper;
        this.satelliteMapper = satelliteMapper;
        this.timezone = timezone;
    }

    public List<FieldPassport> aggregate(RawApiResponse fieldsResponse,
            RawOperationsResponse opsResponse,
            RawGoodsResponse goodsResponse, RawNotesResponse notesResponse,
            RawMachineResponse machineResponse) {

        TmcDictionary tmcDictionary = new TmcDictionary(goodsResponse.data());
        MachineDictionary machineDictionary = new MachineDictionary(machineResponse.data());

        Map<Long, List<RawOperationData>> opsByFieldId = opsResponse.data().stream()
                .filter(op -> op.getFieldId() != null)
                .collect(Collectors.groupingBy(RawOperationData::getFieldId));

        Map<Long, List<RawNote>> notesByFieldId = new HashMap<>();
        for (RawNote note : notesResponse.data()) {
            if (note.sources() == null) {
                continue;
            }
            for (String sourceIdStr : note.sources()) {
                if (sourceIdStr == null || sourceIdStr.isBlank()) {
                    continue;
                }
                String cleanId = sourceIdStr.trim();
                if (cleanId.isEmpty()) {
                    continue;
                }
                try {
                    Long fId = Long.valueOf(cleanId);
                    notesByFieldId.computeIfAbsent(fId, v -> new ArrayList<>()).add(note);
                } catch (NumberFormatException e) {
                    log.warn("Could not parse note source ID '{}' as a Long.", cleanId);
                }
            }
        }

        Set<String> allPlotNames = fieldsResponse.data().stream()
                .map(RawFieldData::plotName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Группируем записи по (имя участка, год). Участком считается посев (sowing),
        // если он задан, иначе — само поле. Записи материнских полей ("ТК21") с участками
        // "ТК21-01" и т.п. в паспорта не попадают: паспорта генерируются для участков,
        // а данные посевов берутся из строк родительского поля, где указан этот посев.
        // Несколько посевов одного участка за год сливаются в один паспорт со всеми
        // ротациями севооборота.
        Map<String, List<RawFieldData>> groupsByPlotYear = new LinkedHashMap<>();
        for (RawFieldData rawField : fieldsResponse.data()) {
            String plotName = rawField.plotName();
            if (plotName == null || plotName.isBlank()) {
                log.debug("Пропускаем запись без имени участка (fieldId={}): имя пустое",
                        rawField.fieldId());
                continue;
            }
            if (isParentUmbrella(plotName, allPlotNames)) {
                log.debug("Пропускаем запись материнского поля '{}' (fieldId={}): у него есть участки, "
                        + "паспорт генерируется только для участка", plotName, rawField.fieldId());
                continue;
            }
            String key = (plotName == null ? "" : plotName)
                    + KEY_SEPARATOR + YearUtils.extractYear(rawField.crop());
            groupsByPlotYear.computeIfAbsent(key, k -> new ArrayList<>()).add(rawField);
        }

        return groupsByPlotYear.values().stream()
                .map(group -> buildPassport(group, opsByFieldId, tmcDictionary,
                        machineDictionary, notesByFieldId))
                .collect(Collectors.toList());
    }

    /**
     * Запись является «материнским полем», если среди всех полей есть участок с именем
     * вида {@code "<thisName>-<digits>"} (например, "ТК21-01" для "ТК21").
     */
    private boolean isParentUmbrella(String name, Set<String> allFieldNames) {
        if (name == null || name.isBlank()) {
            return false;
        }
        for (String other : allFieldNames) {
            if (other == null || other.length() <= name.length() || !other.startsWith(name)
                    || other.charAt(name.length()) != '-') {
                continue;
            }
            if (other.substring(name.length() + 1).matches("\\d+")) {
                return true;
            }
        }
        return false;
    }

    private FieldPassport buildPassport(List<RawFieldData> group,
            Map<Long, List<RawOperationData>> opsByFieldId,
            TmcDictionary tmcDictionary, MachineDictionary machineDictionary,
            Map<Long, List<RawNote>> notesByFieldId) {
        RawFieldData primary = group.get(0);
        int passportYear = YearUtils.extractYear(primary.crop());

        List<Long> fieldIds = group.stream()
                .map(RawFieldData::fieldId)
                .distinct()
                .toList();
        // У всех записей одного участка площадь одинаковая; берём первую как эталонную
        // для привязки спутниковых снимков/графика.
        Long primaryFieldId = fieldIds.get(0);

        List<RawOperationData> cleanOps = new ArrayList<>();
        for (Long fieldId : fieldIds) {
            cleanOps.addAll(filterOperationsByYear(
                    opsByFieldId.getOrDefault(fieldId, List.of()), passportYear));
        }

        List<RawNote> fieldNotes = new ArrayList<>();
        for (Long fieldId : fieldIds) {
            fieldNotes.addAll(notesByFieldId.getOrDefault(fieldId, Collections.emptyList()));
        }
        // Одна заметка может быть привязана к нескольким sourceId участка.
        List<RawNote> uniqueNotes = fieldNotes.stream()
                .distinct()
                .toList();

        List<OperationTableRow> operationTableRows =
                operationMapper.mapToTableRow(cleanOps, tmcDictionary);
        NoteSection noteSection = noteMapper.map(uniqueNotes, String.valueOf(passportYear));
        List<SatelliteImage> satelliteImages =
                satelliteMapper.map(primaryFieldId, operationTableRows);
        satelliteImages.sort(Comparator.comparing(SatelliteImage::getPlanDate));
        List<TechJournalTableRow> techJournalTableRows =
                techJournalMapper.mapToTableRow(cleanOps, machineDictionary);

        String chartTitle = String.format("%s - %s %d", primary.plotName(), primary.department(),
                passportYear);
        ChartImage indexChart = new ChartImage(String.valueOf(primaryFieldId), passportYear,
                chartTitle, new ArrayList<>());

        List<CropRotation> rotations = group.stream()
                .map(FieldDataAggregator::toCropRotation)
                .toList();

        return PassportMapper.mapToDomain(
                primary,
                rotations,
                operationTableRows,
                noteSection,
                satelliteImages,
                indexChart,
                techJournalTableRows
        );
    }

    private static CropRotation toCropRotation(RawFieldData raw) {
        return new CropRotation(
                raw.crop() != null ? raw.crop().trim() : "-",
                raw.variety() != null ? raw.variety() : "-",
                raw.reproduction() != null ? raw.reproduction() : "-"
        );
    }

    private List<RawOperationData> filterOperationsByYear(List<RawOperationData> ops,
            int targetYear) {
        return ops.stream()
                .filter(RawOperationData::isValid)
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