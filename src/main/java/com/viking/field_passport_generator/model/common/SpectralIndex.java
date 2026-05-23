package com.viking.field_passport_generator.model.common;

public enum SpectralIndex {
    NDVI, NDWI, MSI, GLI;

    public String getIndexName() {
        return this.name().toLowerCase();
    }
}
