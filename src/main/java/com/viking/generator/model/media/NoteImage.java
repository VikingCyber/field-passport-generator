package com.viking.generator.model.media;


import com.viking.generator.model.common.SourceType;

public class NoteImage implements ImageSource {
    private final String id;
    private final String complexIndex;
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

    public String getComplexIndex() {
        return complexIndex;
    }
}