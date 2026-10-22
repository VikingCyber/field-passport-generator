package com.viking.generator.service;

import com.viking.generator.model.FieldPassport;

import java.nio.file.Path;

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

    Path resolvePassportPath(FieldPassport passport);
}
