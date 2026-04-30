package com.viking.field_passport_generator.model;

import java.time.LocalDate;

public class SatelliteImage {
    private final Long fieldId;
    private final String description;
    private SpectralIndex index;
    private LocalDate planDate;
    private LocalDate actualDate;
    private String remoteId;
    private byte[] imageBytes;

    public SatelliteImage(Long fieldId, LocalDate planDate, String label, SpectralIndex index) {
        this.fieldId = fieldId;
        this.planDate = planDate;
        this.description = label;
        this.index = index;
    }

    public Long getFieldId() { return fieldId; }
    public LocalDate getPlanDate() { return planDate; }
    public void setPlanDate(LocalDate planDate) { this.planDate = planDate; }

    public LocalDate getActualDate() { return actualDate; }
    public void setActualDate(LocalDate actualDate) { this.actualDate = actualDate; }

    public SpectralIndex getIndex() { return index; }
    public void setIndex(SpectralIndex index) { this.index = index; }
    public String getDescription() { return description; }

    public byte[] getImageBytes() { return imageBytes; }
    public void setImageBytes(byte[] imageBytes) { this.imageBytes = imageBytes; }

    public boolean hasImage() {
        return imageBytes != null && imageBytes.length > 0;
    }
}
