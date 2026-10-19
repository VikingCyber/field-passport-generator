package com.viking.generator.mapper;

import com.viking.generator.data.dto.RawFieldData;
import com.viking.generator.model.*;
import com.viking.generator.model.media.ChartImage;
import com.viking.generator.model.media.SatelliteImage;
import com.viking.generator.model.sections.CropRotation;
import com.viking.generator.model.sections.NoteSection;
import com.viking.generator.model.sections.GeneralInfo;
import com.viking.generator.model.tables.OperationTableRow;
import com.viking.generator.model.tables.TechJournalTableRow;
import com.viking.generator.util.YearUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PassportMapper {
    private static final Logger log = LoggerFactory.getLogger(PassportMapper.class);

    private static final CropRotation UNKNOWN_ROTATION = new CropRotation("-", "-", "-");

    /**
     * Превращает сырые данные поля и подготовленный список операций в доменный объект паспорта.
     *
     * @param rotations все ротации севооборота участка за год (несколько при нескольких посевах)
     */
    public static FieldPassport mapToDomain(RawFieldData raw, List<CropRotation> rotations,
                                            List<OperationTableRow> operations,
                                            NoteSection noteSection, List<SatelliteImage> satelliteImages,
                                            ChartImage chartImage, List<TechJournalTableRow> techJournal) {
        String rawCrop = raw.crop();

        int year = YearUtils.extractYear(rawCrop);

        if (year == -1) {
            log.debug("Год не найден в описании культуры поля {}: '{}'", raw.field(), rawCrop);
        }

        GeneralInfo info = new GeneralInfo(
            raw.department() != null ? raw.department() : "Не указано",
            raw.field() != null ? raw.field() : "Без названия",
            raw.fieldArea(),
            year,
            rotations != null && !rotations.isEmpty()
                    ? rotations
                    : new ArrayList<>(List.of(UNKNOWN_ROTATION))
        );

        return new FieldPassport(
                String.valueOf(raw.fieldId()),
                info,
                operations != null ? operations : Collections.emptyList(),
                noteSection != null ? noteSection : new NoteSection(Collections.emptyList(), Collections.emptyList()),
                chartImage,
                satelliteImages != null ? satelliteImages : Collections.emptyList(),
                techJournal != null ? techJournal : Collections.emptyList()
        );
    }
}