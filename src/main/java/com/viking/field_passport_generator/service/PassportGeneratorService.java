package com.viking.field_passport_generator.service;

import com.viking.field_passport_generator.model.FieldPassport;

import java.nio.file.Path;
import java.util.List;

/**
 * Контракт для компонентов, обеспечивающих формирование документов
 * на основе данных о паспортах полей.
 */
public interface PassportGeneratorService {
    /**
     * Форматирует документ для указанного паспорта поля
     * 
     * @param passport объект, содержащий исходные данные поля
     */
    void generate(FieldPassport passport);

    /**
     * Выполняет пакетное формирование документов для списка паспортов.
     * 
     * @param passports коллекция данных для обработки
     */
    void generateAll(List<FieldPassport> passports);

    Path resolvePassportPath(FieldPassport passport);
}
