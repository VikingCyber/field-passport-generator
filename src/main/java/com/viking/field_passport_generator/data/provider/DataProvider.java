package com.viking.field_passport_generator.data.provider;

import java.util.List;

import com.viking.field_passport_generator.model.FieldPassport;

public interface DataProvider {
    List<FieldPassport> getPassportsData();
}
