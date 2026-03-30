package com.viking.field_passport_generator.data.provider;

import com.viking.field_passport_generator.model.FieldPassport;

import java.util.List;

public interface DataProvider {
    List<FieldPassport> getPassportsData();
}
