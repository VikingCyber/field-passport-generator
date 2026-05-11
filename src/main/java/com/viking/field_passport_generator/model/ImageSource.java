package com.viking.field_passport_generator.model;

public interface ImageSource {
    String getId();
    SourceType getType();
    byte[] getImageBytes();
    void setImageBytes(byte[] bytes);

    default boolean hasImage() {
        byte[] data = getImageBytes();
        return data != null && data.length > 0;
    }
}
