package com.viking.generator.model.media;

import com.viking.generator.data.dto.chart.ChartPoint;
import com.viking.generator.model.common.SourceType;

import java.util.List;

public class ChartImage implements ImageSource {
    private final String fieldId;
    private final int year;
    private final String title;
    private List<ChartPoint> points;
    private byte[] imageBytes;

    public ChartImage(String fieldId, int year, String title, List<ChartPoint> points) {
        this.fieldId = fieldId;
        this.year = year;
        this.title = title;
        this.points = points;
    }

    @Override
    public String getId() {
        return fieldId;
    }

    public int getYear() {
        return year;
    }

    public String getTitle() { return title; }

    @Override
    public SourceType getType() {
        return SourceType.CHART;
    }

    @Override
    public byte[] getImageBytes() {
        return imageBytes;
    }

    public List<ChartPoint> getPoints() {
        return points;
    }

    public void setPoints(List<ChartPoint> points) {
        this.points = points;
    }

    @Override
    public void setImageBytes(byte[] bytes) {
        this.imageBytes = bytes;
    }
}
