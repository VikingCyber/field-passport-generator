package com.viking.field_passport_generator.data.provider;

import com.viking.field_passport_generator.web.dto.PassportSummary;

import java.util.List;

public interface WebDataProvider {
    List<PassportSummary> getPassportSummaries();
}
