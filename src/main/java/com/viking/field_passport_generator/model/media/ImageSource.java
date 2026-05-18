package com.viking.field_passport_generator.model.media;

import com.viking.field_passport_generator.model.common.SourceType;

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
