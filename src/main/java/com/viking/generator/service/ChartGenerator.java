package com.viking.generator.service;

import com.viking.generator.model.media.ChartImage;

import java.util.Optional;

public interface ChartGenerator {
    Optional<byte[]> generateCombinedChart(ChartImage chart);
}
