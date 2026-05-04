package com.viking.field_passport_generator.model;

public interface ImageSource {
    String getId();
    SourceType getType();
    byte[] getImageBytes();
    void setImageBytes(byte[] bytes);
}
