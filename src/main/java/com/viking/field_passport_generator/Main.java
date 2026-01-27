package com.viking.field_passport_generator;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.viking.field_passport_generator.models.FieldPassport;
import com.viking.field_passport_generator.services.DataProvider;
import com.viking.field_passport_generator.services.FileDataProvider;
import com.viking.field_passport_generator.services.PassportGeneratorService;
import com.viking.field_passport_generator.services.PdfGeneratorService;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        DataProvider dataProvider = new FileDataProvider();
        PassportGeneratorService pdfService = new PdfGeneratorService();
        
        try {
            log.info("Запуск генератора паспортов полей...");

            List<FieldPassport> allPassports = dataProvider.getPassportData();
            log.info("Загружено объектов из файла: {}", allPassports.size());

            String targetFieldName = "ПС10-01";
            FieldPassport sample = allPassports.stream()
                .filter(p -> p.generalInfo().fieldName().equalsIgnoreCase(targetFieldName))
                .findFirst()
                .orElse(null);

            if (sample != null) {
                log.info("Эталонное поле '{}' успешно найдено.", targetFieldName);
                log.info("Детали поля: [Подразделение: {}, Площадь: {} га, Культура: {}, Тип семеян: {}]",
                sample.generalInfo().department(),
                sample.generalInfo().fieldArea(),
                sample.generalInfo().rotation().crop(),
                sample.generalInfo().rotation().reproduction());
                pdfService.generate(sample);
            } else {
                log.error("Поле '{}' не найдено в исходных данных!", targetFieldName);
            }
        } catch (Exception e) {
            log.error("Критический сбой при выполнении скрипта", e);
        }
    }
}
