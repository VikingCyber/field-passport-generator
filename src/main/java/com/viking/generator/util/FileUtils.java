package com.viking.generator.util;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for common file operations with retry logic.
 *
 * <p>Provides file deletion and atomic move operation that handle filesystem issues typical
 * on Windows (file locking, access denials) and gracefully fall back when atomic move is not
 * supported by the underlying filesystem.</p>
 *
 */
public class FileUtils {

    private static final Logger log = LoggerFactory.getLogger(FileUtils.class);
    private static final int MAX_DELETE_ATTEMPTS = 3;
    private static final long DELETE_RETRY_DELAY_MS = 150;

    /**
     * Deletes a file, retrying on failure up to (@value #MAX_DELETE_ATTEMPTS) times.
     *
     * <p>This method handles transient filesystem errors (e.g. Windows file locking)
     * by sleeping {@value #DELETE_RETRY_DELAY_MS} ms between attempts. If the file does not exist,
     * method returns silently.</p>
     *
     * @param path the file to delete.
     */
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
                    log.warn("Не удалось удалить файл {} после {} попыток: {}", path, maxAttempts,
                            e.getMessage());
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

    /**
     * Atomically moves a file, retrying on failure up to 5 times.
     *
     * <p>Handles several edge cases:</p>
     * <ul>
     *   <li>{@link java.nio.file.FileAlreadyExistsException} or
     *       {@link java.nio.file.AccessDeniedException} — falls back to a
     *       non-atomic move with {@link StandardCopyOption#REPLACE_EXISTING}.</li>
     *   <li>{@link java.nio.file.AtomicMoveNotSupportedException} — immediately
     *       falls back to a non-atomic move without retrying.</li>
     *   <li>{@link java.nio.file.FileSystemException} — retries up to 5 times
     *       with exponential backoff.</li>
     * </ul>
     *
     * @param source the source file.
     * @param target the target file.
     * @param options copy options (typically
     *      {@link StandardCopyOption#ATOMIC_MOVE ATOMIC_MOVE} and
     *      {@link StandardCopyOption#REPLACE_EXISTING REPLACE_EXISTING}).
     * @throws IOException if the move fails after all retires.
     */
    public static void atomicMoveWithRetries(Path source, Path target, CopyOption... options)
            throws IOException {
        int maxAttempts = 5;
        long sleepMs = 100;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Files.move(source, target, options);
                return;
            } catch (FileAlreadyExistsException | AccessDeniedException e) {
                log.debug("Windows-specific блок: файл занят или уже существует (попытка {}). "
                        + "Применяем fallback...", attempt);

                try {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    return;
                } catch (IOException fallbackEx) {
                    if (attempt == maxAttempts) {
                        throw new IOException("Не удалось выполнить подмену файла через "
                                + "REPLACE_EXISTING после " + maxAttempts + " попыток", fallbackEx);
                    }
                }
            } catch (AtomicMoveNotSupportedException e) {
                log.warn(
                        "Atomic move not supported, falling back to non-atomic move for target: {}",
                        target);
                try {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    return;
                } catch (IOException fallbackEx) {
                    throw new IOException(
                            "Не удалось выполнить подмену файла после обнаружения отсутствия "
                                    + "поддержки atomic move", fallbackEx);
                }
            } catch (FileSystemException e) {
                if (attempt == maxAttempts) {
                    throw new IOException(
                            "Не удалось выполнить атомарную подмену файла после " + maxAttempts
                                    + " попыток", e);
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
