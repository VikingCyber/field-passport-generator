package com.viking.field_passport_generator.mappers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.viking.field_passport_generator.dto.RawFieldData;
import com.viking.field_passport_generator.models.CropRotation;
import com.viking.field_passport_generator.models.FieldPassport;
import com.viking.field_passport_generator.models.GeneralInfo;

public class PassportMapper {
    private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})");
    private static final Logger log = LoggerFactory.getLogger(PassportMapper.class);

    public static FieldPassport mapToDomain(RawFieldData raw) {
        String rawCrop = raw.crop();
        int year = -1;

        if (rawCrop != null && !rawCrop.isBlank()) {
            Matcher matcher = YEAR_PATTERN.matcher(rawCrop);
            if (matcher.find()) {
                year = Integer.parseInt(matcher.group(1));
                log.debug("Извлечен год {} из строки {}", year, rawCrop);
            } else {
                log.warn("Год не найден в описании культуры: '{}', Установлено значение по умолчанию -1", rawCrop);

            }
        }

        return new FieldPassport(
            new GeneralInfo(
                raw.department() != null ? raw.department() : "Не указано",
                raw.field() != null ? raw.field() : "Без названия",
                raw.fieldArea(),
                year,
                new CropRotation(
                    rawCrop != null ? rawCrop.trim() : "-",
                    raw.variety() != null ? raw.variety() : "-",
                    raw.reproduction() != null ? raw.reproduction() : "-"
                )
            )
        );
    }
}
