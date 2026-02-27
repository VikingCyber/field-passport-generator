package com.viking.field_passport_generator.data.dictionary;


import com.viking.field_passport_generator.models.TmcItem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class TmcDictionary {
    private final Map<Long, TmcDictItem> tmcDictionary;
    private static final String NOT_AVAILABLE = "н/д";

    public TmcDictionary(List<TmcDictItem> items) {
        this.tmcDictionary = items.stream()
                .collect(Collectors.toConcurrentMap(TmcDictItem::id, item -> item));
    }

    public String getName(Long id) {
        return Optional.ofNullable(tmcDictionary.get(id))
                .map(TmcDictItem::name)
                .orElse(NOT_AVAILABLE);
    }

    public String getUnit(Long id) {
        return Optional.ofNullable(tmcDictionary.get(id))
                .map(TmcDictItem::measureName)
                .orElse(NOT_AVAILABLE);
    }

    public boolean contains(Long id) {
        return tmcDictionary.containsKey(id);
    }

    public Optional<TmcItem> createTmcItem(Long id, Double amount) {
        return Optional.ofNullable(tmcDictionary.get(id))
                .map(item -> new TmcItem(id, item.name(), amount, item.measureName()));
    }

    public int size() {
        return tmcDictionary.size();
    }
}
