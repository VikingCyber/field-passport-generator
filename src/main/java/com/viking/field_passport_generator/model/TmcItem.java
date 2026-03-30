package com.viking.field_passport_generator.model;


public record TmcItem(
    Long id,
    String name,
    Double amount,
    String unit
) {
    public String formatForPassport() {
        return String.format("%s, %s/%.2f", name, unit, amount);
    }
}
