package com.viking.generator.model.common;

public enum PassportStatus {
    /** Паспорт зарегистрирован на генерацию и ждёт свободный слот в очереди. */
    QUEUED,
    /** Паспорт прямо сейчас генерируется (получил слот и выполняет работу). */
    PROCESSING,
    READY,
    NOT_FOUND
}