package com.viking.generator.model.media;

import com.viking.generator.model.common.SourceType;

/**
 * Образ снимка поля с карты: хранит идентификатор поля (sourceId), название участка
 * и байты готового PNG. Байты наполняет {@code FieldMapStrategy} на этапе кэширования,
 * а {@code PdfSectionFieldMap} вставляет изображение в паспорт.
 */
public class FieldMapImage implements ImageSource {

    private final String fieldId;
    private final String title;

    private byte[] imageBytes;

    public FieldMapImage(String fieldId, String title) {
        this.fieldId = fieldId;
        this.title = title;
    }

    @Override
    public String getId() {
        return fieldId;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public SourceType getType() {
        return SourceType.FIELD_MAP;
    }

    @Override
    public byte[] getImageBytes() {
        return imageBytes;
    }

    @Override
    public void setImageBytes(byte[] bytes) {
        this.imageBytes = bytes;
    }
}
