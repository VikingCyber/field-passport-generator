package com.viking.field_passport_generator.model;


public class NoteImage implements ImageSource {
    private final String id;
    private final String complexIndex; // Оставляем, если он нужен для маппинга или PDF позже
    private byte[] imageBytes;

    public NoteImage(String id, String complexIndex) {
        this.id = id;
        this.complexIndex = complexIndex;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public SourceType getType() {
        return SourceType.NOTE;
    }

    @Override
    public byte[] getImageBytes() {
        return imageBytes;
    }

    @Override
    public void setImageBytes(byte[] bytes) {
        this.imageBytes = bytes;
    }

    public boolean hasImage() {
        return imageBytes != null && imageBytes.length > 0;
    }

    public String getComplexIndex() {
        return complexIndex;
    }
}