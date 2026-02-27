package com.viking.field_passport_generator.data.dto;

import com.viking.field_passport_generator.data.dictionary.TmcDictItem;

import java.util.List;

public record RawGoodsResponse(
    List<TmcDictItem> data
) {
}
