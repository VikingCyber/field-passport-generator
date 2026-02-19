package com.viking.field_passport_generator.dto;


public record RawOperationData(
    Long geoZoneId,
    String geoZone,
    Long operationId, 
    String operation,
    Long startTime,
    Long endTime,
    Double area,             
    Double validHa,       
    Double fuelC,      
    Long duration,     
    Double avgSpeed,
    String seasons,
    String fieldTool,
    String driver
) {

    public boolean isValid() {
        return operation != null && !operation.isBlank() &&
                startTime != null && endTime != null && area != null;
    }
}