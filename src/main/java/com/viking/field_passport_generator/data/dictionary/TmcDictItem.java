package com.viking.field_passport_generator.data.dictionary;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TmcDictItem(
    Long id,
    @JsonProperty("title")
    String name,
    @JsonProperty("group")
    Long groupId,
    String groupName,
    String measureName,
    String measureCode
) {
}
