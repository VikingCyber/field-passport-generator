package com.viking.field_passport_generator.model;

public record NoteImage(
        String id,
        String complexIndex,
        byte[] data
) {

    public NoteImage(String id, String complexIndex) {
        this(id, complexIndex, null);
    }

    public NoteImage withData(byte[] imageData) {
        return new NoteImage(this.id, this.complexIndex, imageData);
    }
}
