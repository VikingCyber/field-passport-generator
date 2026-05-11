package com.viking.field_passport_generator.http.strategy;

import com.viking.field_passport_generator.model.ImageSource;
import com.viking.field_passport_generator.model.SourceType;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

/**
 * ImageProviderStrategy defines the contract for different image loading behaviors.
 * It encapsulates the logic for both "Eager" (Notes) and "Lazy" (Satellite) loading.
 */
public interface ImageProviderStrategy {
    org.slf4j.Logger getLogger();
    /**
     * Performs preliminary data preparation.
     * For notes: triggers a bulk download of all images in batches of 50.
     * For satellites: synchronize the local 'history.json' file with the latest metadata
     * from the API.
     *
     * @param ids a set of unique identifiers (Field IDs or Note IDs) to synchronize
     */
    void synchronize(Set<String> ids);

    void process(ImageSource source);

    SourceType getType();

    default Path resolvePath(Path root, String fileNamePattern, String defaultName, String... subDirs) {
        Path targetDir = root;
        for (String sub : subDirs) {
            targetDir = targetDir.resolve(sub);
        }
        if (Files.isDirectory(targetDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetDir, fileNamePattern)) {
                Iterator<Path> iterator = stream.iterator();
                if (iterator.hasNext()) {
                    return iterator.next();
                }
            } catch (IOException e) {
                getLogger().error("Error scanning cache directory {}: {}", targetDir, e.getMessage());
            }
        }
        Path defaultPath = targetDir.resolve(defaultName);
        getLogger().debug("Cache miss. Default path: {}", defaultPath);
        return defaultPath;
    }

    default void writeToDisk(Path targetPath, byte[] data) {
        if (data == null || data.length == 0) {
            getLogger().warn("Attempted to save empty data to: {}", targetPath);
            return;
        }
        try {
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, data);
            getLogger().info("Successfully saved: {}", targetPath.getFileName());
        } catch (IOException e) {
            getLogger().error("Critical I/O error while saving {}: {}", targetPath, e.getMessage());
        }
    }

    default Optional<byte[]> readFromDisk(Path path) {
        try {
            if (path != null && Files.exists(path)) {
                return Optional.of(Files.readAllBytes(path));
            }
        } catch (IOException e) {
            getLogger().error("Error reading file {}: {}", path, e.getMessage());
        }
        return Optional.empty();
    }
}

