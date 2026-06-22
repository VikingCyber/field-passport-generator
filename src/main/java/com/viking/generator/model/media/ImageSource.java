package com.viking.generator.model.media;

import com.viking.generator.model.common.SourceType;

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
