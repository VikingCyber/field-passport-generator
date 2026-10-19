package com.viking.generator.data.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RawFieldData(
    String department,
    @JsonProperty("sourceId") Long fieldId,
    String field,
    double fieldArea,
    String crop,
    String variety,
    String reproduction,
    @JsonProperty("sowing") String sowing,
    @JsonProperty("sowingArea") Double sowingArea
) {

    /**
     * Имя участка (посева), к которому относится запись. Если задан посев (sowing),
     * то участком является посев; иначе — само поле.
     */
    public String plotName() {
        return sowing != null && !sowing.isBlank() ? sowing : field;
    }

    /**
     * Площадь участка. Для строки посева берём площадь посева (sowingArea),
     * иначе — площадь поля (fieldArea). Родительских строк (с посевом) не бывает.
     */
    public double plotArea() {
        return (sowingArea != null && sowingArea > 0) ? sowingArea : fieldArea;
    }
}
