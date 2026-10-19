package com.viking.generator.data.dto;

import com.viking.generator.data.dictionary.TmcDictItem;

import java.util.List;

public record RawGoodsResponse(
    List<TmcDictItem> data
) {
}
