package com.viking.field_passport_generator.mapper;

import com.viking.field_passport_generator.data.dto.RawFieldData;
import com.viking.field_passport_generator.model.*;
import com.viking.field_passport_generator.util.YearUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

public class PassportMapper {
    private static final Logger log = LoggerFactory.getLogger(PassportMapper.class);

    /**
     * Превращает сырые данные поля и подготовленный список операций в доменный объект паспорта.
     */
    public static FieldPassport mapToDomain(
            RawFieldData raw,
            List<OperationTableRow> operations,
            NoteSection noteSection) {
        String rawCrop = raw.crop();

        String yearStr = YearUtils.extractYear(rawCrop);
        int year = yearStr.isEmpty() ? -1 : Integer.parseInt(yearStr);

        if (year == -1) {
            log.warn("Год не найден в описании культуры поля {}: '{}'", raw.field(), rawCrop);
        }

        GeneralInfo info = new GeneralInfo(
            raw.department() != null ? raw.department() : "Не указано",
            raw.field() != null ? raw.field() : "Без названия",
            raw.fieldArea(),
            year,
            new CropRotation(
                rawCrop != null ? rawCrop.trim() : "-",
                raw.variety() != null ? raw.variety() : "-",
                raw.reproduction() != null ? raw.reproduction() : "-"
            )
        );

        return new FieldPassport(
                info,
                operations != null ? operations : Collections.emptyList(),
                noteSection != null ? noteSection : new NoteSection(Collections.emptyList(), Collections.emptyList())
        );
    }
}
