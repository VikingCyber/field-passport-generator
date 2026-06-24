package com.viking.generator.model.common;

import org.jetbrains.annotations.NotNull;

public record PassportKey(String fieldId, int year) {
    @NotNull
    @Override
    public String toString() {
        return fieldId + "-" + year;
    }
}
