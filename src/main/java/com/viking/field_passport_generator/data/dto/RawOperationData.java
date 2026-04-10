package com.viking.field_passport_generator.data.dto;


import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RawOperationData {
    @JsonProperty("geoZoneId") Long fieldId;
    private String geoZone;
    private Long operationId;
    private String operation;
    private Long startTime;
    private Long endTime;
    private Double area;
    private Double validHa;
    private Double fuelC;
    private Long duration;
    private Double avgSpeed;
    private String seasons;
    private String fieldTool;
    private List<Long> fieldToolIds;
    private String driver;
    private Long unitId;
    private String date;
    private Long mTime;

    @JsonProperty("period")
    private void unpackPeriod(Map<String, Object> period) {
        if (period == null) return;
        this.date = (String) period.get("date");
        Object mTimeVal = period.get("mTime");
        if (mTimeVal instanceof Number) {
            this.mTime = ((Number) mTimeVal).longValue();
        }
    }


    private static final Pattern TMC_ID_PATTERN = Pattern.compile("_(\\d+)$");
    private static final String TMC_PREFIX = "fact_qwn_";

    private final Map<String, Object> tmcFields = new HashMap<>();

    public RawOperationData() {}

    @JsonAnySetter
    public void setDynamicField(String key, Object value) {
        if (key.startsWith(TMC_PREFIX)) {
            tmcFields.put(key, value);
        }
    }

    @JsonIgnore
    public Map<Long, Double> getTmcAmounts() {
        Map<Long, Double> result = new HashMap<>();
        tmcFields.forEach((key, value) -> {
            Matcher matcher = TMC_ID_PATTERN.matcher(key);
            if (matcher.find() && value instanceof Number number) {
                Long id = Long.parseLong(matcher.group(1));
                result.put(id, number.doubleValue());
            }
        });
        return result;
    }

    @JsonIgnore
    public boolean isValid() {
        return operation != null && !operation.isBlank() &&
                startTime != null && endTime != null &&
                area != null && area > 0;
    }

    public String getGeoZone() { return geoZone; }

    public void setGeoZone(String geoZone) {
        this.geoZone = geoZone;
    }

    public Long getOperationId() {
        return operationId;
    }

    public void setOperationId(Long operationId) {
        this.operationId = operationId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public Double getArea() {
        return area;
    }

    public void setArea(Double area) {
        this.area = area;
    }

    public Double getValidHa() {
        return validHa;
    }

    public void setValidHa(Double validHa) {
        this.validHa = validHa;
    }

    public Double getFuelC() {
        return fuelC;
    }

    public void setFuelC(Double fuelC) {
        this.fuelC = fuelC;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }

    public Double getAvgSpeed() {
        return avgSpeed;
    }

    public void setAvgSpeed(Double avgSpeed) {
        this.avgSpeed = avgSpeed;
    }

    public String getSeasons() {
        return seasons;
    }

    public void setSeasons(String seasons) {
        this.seasons = seasons;
    }

    public String getFieldTool() {
        return fieldTool;
    }

    public void setFieldTool(String fieldTool) {
        this.fieldTool = fieldTool;
    }

    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public List<Long> getFieldToolIds() {
        return fieldToolIds;
    }

    public void setFieldToolIds(List<Long> fieldToolIds) {
        this.fieldToolIds = fieldToolIds;
    }

    public Long getUnitId() { return unitId; }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public Long getFieldId() {
        return fieldId;
    }

    public void setFieldId(Long fieldId) { this.fieldId = fieldId; }

    public String getDate() { return date; }

    public Long getMTime() { return mTime; }
}

