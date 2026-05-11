package com.viking.field_passport_generator.model;

import java.time.LocalDate;

public class SatelliteImage implements ImageSource {
    private final String fieldId;
    private final String description;
    private SpectralIndex index;
    private LocalDate planDate;
    private LocalDate actualDate;
    private String remoteId;
    private byte[] imageBytes;

    public SatelliteImage(String fieldId, LocalDate planDate, String label, SpectralIndex index) {
        this.fieldId = fieldId;
        this.planDate = planDate;
        this.description = label;
        this.index = index;
    }

    @Override
    public String getId() { return fieldId; }

    @Override
    public SourceType getType() { return SourceType.SATELLITE; }

    @Override
    public byte[] getImageBytes() { return imageBytes; }

    @Override
    public void setImageBytes(byte[] imageBytes) { this.imageBytes = imageBytes; }

    public LocalDate getPlanDate() { return planDate; }
    public void setPlanDate(LocalDate planDate) { this.planDate = planDate; }

    public LocalDate getActualDate() { return actualDate; }
    public void setActualDate(LocalDate actualDate) { this.actualDate = actualDate; }

    public SpectralIndex getIndex() { return index; }
    public void setIndex(SpectralIndex index) { this.index = index; }
    public String getDescription() { return description; }
}
