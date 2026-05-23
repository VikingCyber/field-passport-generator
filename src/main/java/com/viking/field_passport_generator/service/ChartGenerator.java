package com.viking.field_passport_generator.service;

import com.viking.field_passport_generator.model.media.ChartImage;

import java.util.Optional;

public interface ChartGenerator {
    Optional<byte[]> generateCombinedChart(ChartImage chart);
}
