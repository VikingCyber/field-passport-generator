package com.viking.generator.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;

public class FileUtils {
    private static final Logger log = LoggerFactory.getLogger(FileUtils.class);
    private static final int MAX_DELETE_ATTEMPTS = 3;
    private static final long DELETE_RETRY_DELAY_MS = 150;

    public static void deleteWithRetries(Path path) {
        int maxAttempts = MAX_DELETE_ATTEMPTS;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                if (Files.deleteIfExists(path)) {
                    log.debug("Файл удален: {}", path);
                    return;
                }
                return; // Файла нет, значит задача выполнена
            } catch (IOException e) {
                if (i == maxAttempts - 1) {
                    log.warn("Не удалось удалить файл {} после {} попыток: {}", path, maxAttempts, e.getMessage());
                    return;
                }
                try {
                    Thread.sleep(DELETE_RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    public static void atomicMoveWithRetries(Path source, Path target, CopyOption... options) throws IOException {
        int maxAttempts = 5;
        long sleepMs = 100;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Files.move(source, target, options);
                return;
            } catch (FileAlreadyExistsException | AccessDeniedException e) {
                log.debug("Windows-specific блок: файл занят или уже существует (попытка {}). Применяем fallback...", attempt);

                try {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    return;
                } catch (IOException fallbackEx) {
                    if (attempt == maxAttempts) {
                        throw new IOException("Не удалось выполнить подмену файла даже через REPLACE_EXISTING после " + maxAttempts + " попыток", fallbackEx);
                    }
                }
            } catch (FileSystemException e) {
                if (attempt == maxAttempts) {
                    throw new IOException("Не удалось выполнить атомарную подмену файла после " + maxAttempts + " попыток", e);
                }
            }
            try {
                Thread.sleep(sleepMs * attempt);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("Перенос файла прерван", ie);
            }
        }
    }
}
