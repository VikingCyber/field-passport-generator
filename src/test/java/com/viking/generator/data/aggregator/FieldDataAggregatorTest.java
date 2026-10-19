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
                "Посажено в 2025 году: Овес", "Скакун", "Элита"
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
                "Северный", 100L, "ТК21-01", 150.0, "Овес 2025", "Скакун", "Элита");
        RawFieldData sowing2 = new RawFieldData(
                "Северный", 200L, "ТК21-01", 150.0, "Ячмень 2025", "Сладкий", "Репр1");
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
    @DisplayName("Должен пропустить материнское поле и сгенерировать паспорт только на участок")
    void shouldDropParentUmbrellaAndKeepPlotPassport() {
        // --- ARRANGE ---
        RawFieldData parent = new RawFieldData(
                "Северный", 500L, "ТК21", 500.0, "Пшеница 2025", "Сибирь", "Элита");
        RawFieldData plot = new RawFieldData(
                "Северный", 501L, "ТК21-01", 150.0, "Овес 2025", "Скакун", "Элита");
        RawApiResponse fieldsResponse = new RawApiResponse(List.of(parent, plot));

        // --- ACT ---
        List<FieldPassport> passports = aggregator.aggregate(fieldsResponse,
                new RawOperationsResponse(List.of()), new RawGoodsResponse(List.of()),
                new RawNotesResponse(List.of(), false, 0), new RawMachineResponse(List.of()));

        // --- ASSERT ---
        assertEquals(1, passports.size(), "Материнское поле не должно давать отдельный паспорт-дубликат");
        FieldPassport passport = passports.getFirst();
        assertEquals("ТК21-01", passport.generalInfo().fieldName());
        assertEquals("501", passport.fieldId());
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