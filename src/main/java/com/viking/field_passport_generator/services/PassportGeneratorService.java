package com.viking.field_passport_generator.services;

import java.util.List;

import com.viking.field_passport_generator.models.FieldPassport;

public interface PassportGeneratorService {
    void generate(FieldPassport passport);

    void generateAll(List<FieldPassport> passports);
}
