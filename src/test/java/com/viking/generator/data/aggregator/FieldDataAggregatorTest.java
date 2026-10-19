package com.viking.generator.data.aggregator;

import com.viking.generator.data.dictionary.MachineDictionary;
import com.viking.generator.data.dictionary.TmcDictItem;
import com.viking.generator.data.dictionary.TmcDictionary;
import com.viking.generator.data.dto.*;
import com.viking.generator.data.dto.satellite.SatelliteCaptureRule;
import com.viking.generator.mapper.NoteMapper;
import com.viking.generator.mapper.OperationMapper;
import com.viking.generator.mapper.SatelliteMapper;
import com.viking.generator.mapper.TechJournalMapper;
import com.viking.generator.model.FieldPassport;
import com.viking.generator.model.common.MachineResource;
import com.viking.generator.model.common.SpectralIndex;
import com.viking.generator.model.common.TmcItem;
import com.viking.generator.model.media.SatelliteImage;
import com.viking.generator.model.sections.CropRotation;
import com.viking.generator.model.sections.NoteSection;
import com.viking.generator.model.tables.OperationTableRow;
import com.viking.generator.model.tables.TechJournalTableRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

// Импортируем стандартные ассерты JUnit 5
import static org.junit.jupiter.api.Assertions.*;

class FieldDataAggregatorIntegrationTest {

    private FieldDataAggregator aggregator;

    private OperationMapper operationMapper;
    private NoteMapper noteMapper;
    private TechJournalMapper techJournalMapper;
    private SatelliteMapper satelliteMapper;

    private final ZoneId timezone = ZoneId.of("Europe/Moscow");
    private final Duration aggregationThreshold = Duration.ofHours(2);
    private final String emptyLabel = "Н/Д";

    @BeforeEach
    void setUp() {
        operationMapper = new OperationMapper(timezone, aggregationThreshold);
        noteMapper = new NoteMapper(timezone);
        techJournalMapper = new TechJournalMapper(emptyLabel);

        Set<SpectralIndex> indices = Set.of(SpectralIndex.NDVI, SpectralIndex.NDWI);
        List<SatelliteCaptureRule> satelliteRules = List.of(
                new SatelliteCaptureRule("посев", List.of(5, 10), List.of("Всходы", "Вегетация"))
        );
        satelliteMapper = new SatelliteMapper(indices, satelliteRules);

        aggregator = new FieldDataAggregator(
                operationMapper,
                noteMapper,
                techJournalMapper,
                satelliteMapper,
                timezone
        );
    }

    @Test
    @DisplayName("Должен успешно агрегировать сырые данные в FieldPassport и проверить внутреннюю структуру")
    void shouldSuccessfullyAggregateFieldPassportWithAllMappers() {
        // --- ARRANGE ---
        long targetFieldId = 42L;
        int targetYear = 2025;

        RawFieldData rawField = new RawFieldData(
                "Северный", targetFieldId, "Поле №12", 150.0,
                "Посажено в 2025 году: Овес", "Скакун", "Элита", null, null
        );
        RawApiResponse fieldsResponse = new RawApiResponse(List.of(rawField));

        RawGoodsResponse goodsResponse = new RawGoodsResponse(List.of(
                new TmcDictItem(101L, "Семена Овса", 1L, "Семена", "кг", "KG")
        ));

        // Минимальный стаб для TmcDictionary, чтобы OperationAccumulator мог собрать TmcItem
        TmcDictionary stubTmcDict = new TmcDictionary(goodsResponse.data()) {
            @Override
            public Optional<TmcItem> createTmcItem(Long id, Double amount) {
                if (id.equals(101L)) {
                    return Optional.of(new TmcItem(id, "Семена Овса", amount, "кг"));
                }
                return Optional.empty();
            }
        };

        MachineResource machine = new MachineResource(77L, "Трактор", "Кировец", "К-744", "А123АА 777", "INV-001");
        RawMachineResponse machineResponse = new RawMachineResponse(List.of(machine));

        long baseTimeMs = LocalDateTime.of(targetYear, 5, 10, 8, 0).atZone(timezone).toInstant().toEpochMilli();
        long oneHourMs = Duration.ofHours(1).toMillis();

        // Первая часть склеиваемой операции
        RawOperationData op1 = new RawOperationData();
        setOperationFields(op1, targetFieldId, "Посев овса", baseTimeMs, baseTimeMs + oneHourMs, 40.0, 38.0,
                "10.05.2025", baseTimeMs); // Передали дату и mTime в конец
        op1.setDynamicField("fact_qwn_101", 500.0);
        op1.setUnitId(77L);
        op1.setDriver("Иванов И.И.");

        // Вторая часть склеиваемой операции (разрыв 1 час -> склеятся)
        RawOperationData op2 = new RawOperationData();
        setOperationFields(op2, targetFieldId, "Посев овса", baseTimeMs + (oneHourMs * 2), baseTimeMs + (oneHourMs * 3), 50.0, 48.0,
                "10.05.2025 <br> Смена 2", baseTimeMs + (oneHourMs * 2)); // Сюда залетает наш HTML-тег
        op2.setDynamicField("fact_qwn_101", 600.0);
        op2.setUnitId(77L);
        op2.setDriver("Иванов И.И.");

        // Операция прошлого года (отфильтруется)
        long wrongTimeMs = LocalDateTime.of(2024, 5, 10, 8, 0).atZone(timezone).toInstant().toEpochMilli();
        RawOperationData opWrongYear = new RawOperationData();
        setOperationFields(opWrongYear, targetFieldId, "Культивация", wrongTimeMs, wrongTimeMs + oneHourMs, 100.0, 100.0,
                "10.05.2024", wrongTimeMs);

        RawOperationsResponse opsResponse = new RawOperationsResponse(List.of(op1, op2, opWrongYear));

        OffsetDateTime noteTime = OffsetDateTime.of(targetYear, 6, 1, 12, 0, 0, 0, ZoneOffset.ofHours(3));
        RawNote validNote = new RawNote(
                "Проверка всходов", "Все взошло отлично", List.of("img_id_1"), List.of(String.valueOf(targetFieldId)),
                noteTime, noteTime.plusHours(1)
        );
        RawNotesResponse notesResponse = new RawNotesResponse(List.of(validNote), true, 1);

        // --- ACT ---
        // Чтобы заставить агрегатор использовать наш переопределенный stubTmcDict, подменим его логику через переданный goodsResponse,
        // либо, если вы не можете подменить его в проде, используйте реальный TmcDictionary, если он умеет искать данные.
        List<FieldPassport> passports = aggregator.aggregate(fieldsResponse, opsResponse, goodsResponse, notesResponse, machineResponse);

        // --- ASSERT (Чистый JUnit 5) ---
        assertNotNull(passports, "Список паспортов не должен быть null");
        assertEquals(1, passports.size(), "Должен быть сгенерирован ровно 1 паспорт поля");

        FieldPassport passport = passports.getFirst();

        // 1. Проверяем глубокие внутренности агрегированного FieldPassport
        // Замените методы ниже на реальные геттеры/рекорды вашего класса FieldPassport
        assertAll("Проверка комплексного маппинга доменного объекта",
                () -> assertEquals(String.valueOf(targetFieldId), passport.fieldId(), "ID поля должен совпадать"),
                () -> assertNotNull(passport.operations(), "Таблица операций не должна быть пустой"),
                // Заголовок графика формируется в агрегаторе: "Поле №12 - Северный 2025"
                () -> assertNotNull(passport.indexChart(), "Объект графика должен быть инициализирован")
        );

        // 2. Проверяем, как отработали внутренние мапперы, изолированно на тех же данных

        // Проверка склеивания операций (OperationMapper)
        List<OperationTableRow> mappedOps = operationMapper.mapToTableRow(List.of(op1, op2), stubTmcDict);
        assertEquals(1, mappedOps.size(), "Две смежные операции должны склеиться в одну");
        OperationTableRow row = mappedOps.getFirst();
        assertEquals("Посев овса", row.operationName());
        assertEquals(90.0, row.measuredArea(), 0.001);
        assertEquals(86.0, row.actualArea(), 0.001);
        assertEquals(1, row.tmcItemList().size());
        assertEquals(1100.0, row.tmcItemList().getFirst().amount(), 0.001);

        // Проверка генерации дат для спутников (SatelliteMapper)
        List<SatelliteImage> satelliteImages = satelliteMapper.map(targetFieldId, mappedOps);
        assertEquals(4, satelliteImages.size(), "Ожидается 4 спутниковых снимка (2 оффсета * 2 индекса)");

        // Проверяем, что даты сместились на +5 и +10 дней от даты окончания операции (10 мая)
        List<LocalDate> expectedDates = List.of(LocalDate.of(2025, 5, 15), LocalDate.of(2025, 5, 20));
        for (SatelliteImage img : satelliteImages) {
            assertTrue(expectedDates.contains(img.getPlanDate()), "Дата снимка должна быть либо 15, либо 20 мая");
        }

        // Проверка очистки HTML в техжурнале (TechJournalMapper)
        List<TechJournalTableRow> techJournalRows = techJournalMapper.mapToTableRow(List.of(op1, op2), new MachineDictionary(machineResponse.data()));
        assertEquals(2, techJournalRows.size());
        // Проверяем, что у второй строки <br> заменился на пробел
        String cleanDateResult = techJournalRows.get(1).period(); // или cleanDate() в зависимости от вашего рекорда
        assertEquals("10.05.2025 Смена 2", cleanDateResult, "Тег <br> должен быть заменен на пробел");

        // Проверка заметок и индексов картинок (NoteMapper)
        NoteSection noteSection = noteMapper.map(List.of(validNote), String.valueOf(targetYear));
        assertEquals(1, noteSection.notes().size());
        assertEquals("1.1", noteSection.images().getFirst().getComplexIndex(), "Индекс первой картинки первой заметки должен быть 1.1");
    }

    @Test
    @DisplayName("Должен объединить несколько посевов одного участка за год в один паспорт со всеми ротациями")
    void shouldMergeMultiSowingRecordsIntoOnePassport() {
        // --- ARRANGE ---
        RawFieldData sowing1 = new RawFieldData(
                "Северный", 100L, "ТК21-01", 150.0, "Овес 2025", "Скакун", "Элита", null, null);
        RawFieldData sowing2 = new RawFieldData(
                "Северный", 200L, "ТК21-01", 150.0, "Ячмень 2025", "Сладкий", "Репр1", null, null);
        RawApiResponse fieldsResponse = new RawApiResponse(List.of(sowing1, sowing2));
        RawGoodsResponse goodsResponse = new RawGoodsResponse(List.of());
        RawMachineResponse machineResponse = new RawMachineResponse(List.of());

        long baseTimeMs = LocalDateTime.of(2025, 5, 10, 8, 0).atZone(timezone).toInstant().toEpochMilli();
        long oneHourMs = Duration.ofHours(1).toMillis();

        RawOperationData opOats = new RawOperationData();
        setOperationFields(opOats, 100L, "Посев овса", baseTimeMs, baseTimeMs + oneHourMs,
                40.0, 38.0, "10.05.2025", baseTimeMs);
        RawOperationData opBarley = new RawOperationData();
        setOperationFields(opBarley, 200L, "Посев ячменя", baseTimeMs + oneHourMs,
                baseTimeMs + 2 * oneHourMs, 50.0, 48.0, "10.05.2025", baseTimeMs + oneHourMs);
        RawOperationsResponse opsResponse = new RawOperationsResponse(List.of(opOats, opBarley));

        // --- ACT ---
        List<FieldPassport> passports = aggregator.aggregate(fieldsResponse, opsResponse,
                goodsResponse, new RawNotesResponse(List.of(), false, 0), machineResponse);

        // --- ASSERT ---
        assertEquals(1, passports.size(), "Должен получиться один паспорт на участок и год");
        FieldPassport passport = passports.getFirst();
        assertEquals("ТК21-01", passport.generalInfo().fieldName());
        assertEquals("100", passport.fieldId());
        assertEquals(2, passport.generalInfo().rotations().size(),
                "Должны быть собраны обе ротации севооборота");
        List<String> crops = passport.generalInfo().rotations().stream()
                .map(CropRotation::crop)
                .toList();
        assertTrue(crops.contains("Овес 2025"), "Ротация 'Овес 2025' должна попасть в паспорт");
        assertTrue(crops.contains("Ячмень 2025"), "Ротация 'Ячмень 2025' должна попасть в паспорт");
        assertEquals(2, passport.operations().size(),
                "Должны быть объединены операции обоих посевов участка");
    }

    @Test
    @DisplayName("Должен сгенерировать паспорт и на материнское поле, и на участок (без дублирования данных)")
    void shouldGenerateBothFieldAndPlotPassportsWithoutDuplicates() {
        // --- ARRANGE ---
        // ТК21 — материнское поле со своей ротацией (Пшеница 2025, 500 га) и участком ТК21-01.
        // Данные участка лежат в строке родителя с посевом; строки без посева — это только
        // собственная история родителя, поэтому дублирования нет.
        RawFieldData parentOwn = new RawFieldData(
                "Северный", 500L, "ТК21", 500.0, "Пшеница 2025", "Сибирь", "Элита", null, null);
        RawFieldData plotRow = new RawFieldData(
                "Северный", 501L, "ТК21", 500.0, "Овес 2025", "Скакун", "Элита", "ТК21-01", 150.0);
        RawFieldData plotStandalone = new RawFieldData(
                "Северный", 501L, "ТК21-01", 150.0, "Овес 2025", "Скакун", "Элита", null, null);
        RawApiResponse fieldsResponse = new RawApiResponse(List.of(parentOwn, plotRow, plotStandalone));

        // --- ACT ---
        List<FieldPassport> passports = aggregator.aggregate(fieldsResponse,
                new RawOperationsResponse(List.of()), new RawGoodsResponse(List.of()),
                new RawNotesResponse(List.of(), false, 0), new RawMachineResponse(List.of()));

        // --- ASSERT ---
        assertEquals(2, passports.size(), "Должно быть 2 паспорта: материнское поле и участок");
        Map<String, FieldPassport> byName = passports.stream()
                .collect(Collectors.toMap(p -> p.generalInfo().fieldName(), p -> p));

        FieldPassport field = byName.get("ТК21");
        assertNotNull(field, "Паспорт материнского поля ТК21 должен существовать");
        assertEquals("500", field.fieldId());
        assertEquals("Пшеница 2025", field.generalInfo().rotations().getFirst().crop());

        FieldPassport plot = byName.get("ТК21-01");
        assertNotNull(plot, "Паспорт участка ТК21-01 должен существовать");
        assertEquals("501", plot.fieldId());
        assertEquals(150.0, plot.generalInfo().fieldArea(), 0.001,
                "Площадь участка не должна заимствоваться у родителя");
    }

    @Test
    @DisplayName("Должен генерировать паспорт на посев, который есть только внутри родительского поля, и на само поле")
    void shouldGeneratePassportForPlotThatExistsOnlyAsSowingOfParent() {
        // --- ARRANGE ---
        // Поле ТК10 (224.42 га) поделено на 4 посева; ТК10-02/03 существуют только как посевы.
        RawFieldData parentOwn = new RawFieldData(
                "Северный", 500L, "ТК10", 224.42, "Рапс 2024", "Сибирь", "Элита", null, null);
        RawFieldData sowing01 = new RawFieldData(
                "Северный", 500L, "ТК10", 224.42, "Пшеница 2025", "Скакун", "Элита", "ТК10-01", 53.0);
        RawFieldData sowing02 = new RawFieldData(
                "Северный", 500L, "ТК10", 224.42, "Пшеница 2025", "Скакун", "Элита", "ТК10-02", 117.0);
        RawFieldData sowing03 = new RawFieldData(
                "Северный", 500L, "ТК10", 224.42, "Пшеница 2025", "Скакун", "Элита", "ТК10-03", 39.6);
        RawFieldData sowing04 = new RawFieldData(
                "Северный", 500L, "ТК10", 224.42, "Пшеница 2025", "Скакун", "Элита", "ТК10-04", 12.1);
        RawApiResponse fieldsResponse = new RawApiResponse(
                List.of(parentOwn, sowing01, sowing02, sowing03, sowing04));

        // --- ACT ---
        List<FieldPassport> passports = aggregator.aggregate(fieldsResponse,
                new RawOperationsResponse(List.of()), new RawGoodsResponse(List.of()),
                new RawNotesResponse(List.of(), false, 0), new RawMachineResponse(List.of()));

        // --- ASSERT ---
        assertEquals(5, passports.size(),
                "Должно быть 5 паспортов: материнское поле ТК10 (Рапс 2024) + 4 посева ТК10-01..04");
        Map<String, FieldPassport> byName = passports.stream()
                .collect(Collectors.toMap(p -> p.generalInfo().fieldName(), p -> p));
        FieldPassport field = byName.get("ТК10");
        assertNotNull(field, "Паспорт материнского поля ТК10 должен существовать (его собственная ротация)");
        assertEquals("Рапс 2024", field.generalInfo().rotations().getFirst().crop());
        assertEquals("500", field.fieldId());
        FieldPassport plot02 = byName.get("ТК10-02");
        assertNotNull(plot02, "Паспорт посева ТК10-02 должен существовать");
        assertEquals(117.0, plot02.generalInfo().fieldArea(), 0.001,
                "Площадь посева должна браться из sowingArea, а не из площади родителя");
        assertEquals("Пшеница 2025", plot02.generalInfo().rotations().getFirst().crop());
        assertEquals(1, plot02.generalInfo().rotations().size());
    }

    @Test
    @DisplayName("Должен объединить записи участка, представленного и отдельным полем, и посевом")
    void shouldMergeStandaloneFieldAndSowingRowsOfSamePlot() {
        // --- ARRANGE ---
        // ТК10-01 есть и как отдельное поле (Пшеница 2025, 53 га), и как посев внутри ТК10 (Пар 2026, 53 га).
        RawFieldData standalone = new RawFieldData(
                "Северный", 501L, "ТК10-01", 53.0, "Пшеница 2025", "Скакун", "Элита", null, null);
        RawFieldData parentRow = new RawFieldData(
                "Северный", 501L, "ТК10", 224.42, "Пар 2026", "Сибирь", "Элита", "ТК10-01", 53.0);
        RawApiResponse fieldsResponse = new RawApiResponse(List.of(standalone, parentRow));

        // --- ACT ---
        List<FieldPassport> passports = aggregator.aggregate(fieldsResponse,
                new RawOperationsResponse(List.of()), new RawGoodsResponse(List.of()),
                new RawNotesResponse(List.of(), false, 0), new RawMachineResponse(List.of()));

        // --- ASSERT ---
        Map<Integer, FieldPassport> byYear = passports.stream()
                .collect(Collectors.toMap(p -> p.generalInfo().year(), p -> p));
        assertEquals(2, passports.size(), "Должно быть 2 паспорта участка ТК10-01 (2025 и 2026)");

        FieldPassport p2025 = byYear.get(2025);
        assertNotNull(p2025);
        assertEquals("ТК10-01", p2025.generalInfo().fieldName());
        assertEquals(53.0, p2025.generalInfo().fieldArea(), 0.001);
        assertEquals("Пшеница 2025", p2025.generalInfo().rotations().getFirst().crop());

        FieldPassport p2026 = byYear.get(2026);
        assertNotNull(p2026);
        assertEquals("ТК10-01", p2026.generalInfo().fieldName());
        assertEquals(53.0, p2026.generalInfo().fieldArea(), 0.001,
                "Площадь должна предпочитать sowingArea (53), а не площадь родителя (224.42)");
        assertEquals("Пар 2026", p2026.generalInfo().rotations().getFirst().crop());
    }

    @Test
    @DisplayName("Должен пропустить запись без имени участка и поля (мусорные строки выгрузки)")
    void shouldSkipRowWithoutPlotName() {
        // --- ARRANGE ---
        RawFieldData junk = new RawFieldData(
                "Северный", 369629L, "", 0.0, " 2026", "", "", null, null);
        RawFieldData normal = new RawFieldData(
                "Северный", 501L, "ТК10-01", 53.0, "Пшеница 2025", "Скакун", "Элита", null, null);
        RawApiResponse fieldsResponse = new RawApiResponse(List.of(junk, normal));

        // --- ACT ---
        List<FieldPassport> passports = aggregator.aggregate(fieldsResponse,
                new RawOperationsResponse(List.of()), new RawGoodsResponse(List.of()),
                new RawNotesResponse(List.of(), false, 0), new RawMachineResponse(List.of()));

        // --- ASSERT ---
        assertEquals(1, passports.size(), "Запись с пустым именем не должна давать паспорт");
        assertEquals("ТК10-01", passports.getFirst().generalInfo().fieldName());
    }

    @Test
    @DisplayName("При нескольких sourceId в группе за год эталоном снимков/графика становится sourceId первой записи (как ID паспорта)")
    void shouldUsePrimaryFieldAsReferenceWhenGroupHasSeveralFieldIds() {
        // --- ARRANGE ---
        // Гипотетический случай: участок ТК21-01 в один год имеет две записи с разными
        // sourceId (например, перерегистрирован посреди сезона). Паспорт один, операции
        // склеиваются из обоих id, а эталон привязки снимков/графика — первая запись.
        RawFieldData first = new RawFieldData(
                "Северный", 100L, "ТК21-01", 150.0, "Овес 2025", "Скакун", "Элита", null, null);
        RawFieldData second = new RawFieldData(
                "Северный", 200L, "ТК21-01", 150.0, "Ячмень 2025", "Сладкий", "Репр1", null, null);
        RawApiResponse fieldsResponse = new RawApiResponse(List.of(first, second));

        long baseTimeMs = LocalDateTime.of(2025, 5, 10, 8, 0).atZone(timezone).toInstant().toEpochMilli();
        long oneHourMs = Duration.ofHours(1).toMillis();
        RawOperationData opOats = new RawOperationData();
        setOperationFields(opOats, 100L, "Посев овса", baseTimeMs, baseTimeMs + oneHourMs,
                40.0, 38.0, "10.05.2025", baseTimeMs);
        RawOperationData opBarley = new RawOperationData();
        setOperationFields(opBarley, 200L, "Посев ячменя", baseTimeMs + oneHourMs,
                baseTimeMs + 2 * oneHourMs, 50.0, 48.0, "10.05.2025", baseTimeMs + oneHourMs);
        RawOperationsResponse opsResponse = new RawOperationsResponse(List.of(opOats, opBarley));

        // --- ACT ---
        List<FieldPassport> passports = aggregator.aggregate(fieldsResponse, opsResponse,
                new RawGoodsResponse(List.of()), new RawNotesResponse(List.of(), false, 0),
                new RawMachineResponse(List.of()));

        // --- ASSERT ---
        assertEquals(1, passports.size(), "Должен получиться один паспорт на участок и год");
        FieldPassport passport = passports.getFirst();
        assertEquals("100", passport.fieldId(),
                "ID паспорта берётся из первой записи группы (как раньше)");
        assertEquals(2, passport.operations().size(),
                "Операции должны склеиваться из всех sourceId группы");
        assertNotNull(passport.indexChart());
        assertEquals("100", passport.indexChart().getId(),
                "График привязывается к sourceId первой записи (эталону привязки)");
        List<String> crops = passport.generalInfo().rotations().stream()
                .map(CropRotation::crop)
                .toList();
        assertEquals(List.of("Овес 2025", "Ячмень 2025"), crops,
                "Обе ротации севооборота должны быть собраны");
    }

    private void setOperationFields(RawOperationData op, Long fieldId, String name, Long start, Long end, Double area, Double validHa, String rawDate, Long mTime) {
        try {
            java.lang.reflect.Field fId = RawOperationData.class.getDeclaredField("fieldId");
            fId.setAccessible(true); fId.set(op, fieldId);

            java.lang.reflect.Field oName = RawOperationData.class.getDeclaredField("operation");
            oName.setAccessible(true); oName.set(op, name);

            java.lang.reflect.Field sTime = RawOperationData.class.getDeclaredField("startTime");
            sTime.setAccessible(true); sTime.set(op, start);

            java.lang.reflect.Field eTime = RawOperationData.class.getDeclaredField("endTime");
            eTime.setAccessible(true); eTime.set(op, end);

            java.lang.reflect.Field oArea = RawOperationData.class.getDeclaredField("area");
            oArea.setAccessible(true); oArea.set(op, area);

            java.lang.reflect.Field vHa = RawOperationData.class.getDeclaredField("validHa");
            vHa.setAccessible(true); vHa.set(op, validHa);

            // ПРЯМАЯ ЗАПИСЬ ДАТЫ И МTIME ДЛЯ ТЕХЖУРНАЛА
            java.lang.reflect.Field dField = RawOperationData.class.getDeclaredField("date");
            dField.setAccessible(true); dField.set(op, rawDate);

            java.lang.reflect.Field mField = RawOperationData.class.getDeclaredField("mTime");
            mField.setAccessible(true); mField.set(op, mTime);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}