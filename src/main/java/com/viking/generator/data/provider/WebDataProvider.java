package com.viking.generator.data.provider;

import com.viking.generator.web.dto.PassportSummary;

import java.util.List;

public interface WebDataProvider {
    List<PassportSummary> getPassportSummaries();
}
