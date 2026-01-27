package com.viking.field_passport_generator.services;

import java.util.List;

import com.viking.field_passport_generator.models.FieldPassport;

public interface DataProvider {
    List<FieldPassport> getPassportData();
}
