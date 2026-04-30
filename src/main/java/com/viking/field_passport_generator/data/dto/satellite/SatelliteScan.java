package com.viking.field_passport_generator.data.dto.satellite;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashMap;
import java.util.Map;

public record SatelliteScan(
    String id,
    String objectType,
    String imagePath,
    String date,
    Double cloud,
    IndexData ndvi,
    IndexData ndwi,
    IndexData msi,
    IndexData gli
) {
    @JsonIgnore
    public Map<String, IndexData> getAllIndices() {
        Map<String, IndexData> map = new HashMap<>();
        if (ndvi != null) map.put("ndvi", ndvi);
        if (ndwi != null) map.put("ndwi", ndwi);
        if (msi != null) map.put("msi", msi);
        if (gli != null) map.put("gli", gli);
        return map;
    }
}
