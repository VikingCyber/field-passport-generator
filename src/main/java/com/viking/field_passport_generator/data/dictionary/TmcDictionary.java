package com.viking.field_passport_generator.data.dictionary;


import com.viking.field_passport_generator.models.TmcItem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TmcDictionary {
    private final Map<Long, TmcDictItem> tmcDictionary;

    public TmcDictionary(List<TmcDictItem> items) {
        this.tmcDictionary = items.stream()
                .collect(Collectors.toConcurrentMap(TmcDictItem::id, item -> item));
    }

    public String getName(Long id) {
        TmcDictItem item = tmcDictionary.get(id);
        return item != null ? item.name() : "ТМЦ-" + id;
    }

    public String getUnit(Long id) {
        TmcDictItem item = tmcDictionary.get(id);
        return item != null ? item.measureName() : "Error unknow measure!";
    }

    public boolean contains(Long id) {
        return tmcDictionary.containsKey(id);
    }

    public TmcItem createTmcItem(Long id, Double amount) {
        if (!contains(id)) return null;
        return new TmcItem(id, getName(id), amount, getUnit(id));
    }

    public int size() {
        return tmcDictionary.size();
    }
}
