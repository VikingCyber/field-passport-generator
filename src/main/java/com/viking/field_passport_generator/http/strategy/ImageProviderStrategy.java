package com.viking.field_passport_generator.http.strategy;

import com.viking.field_passport_generator.model.ImageSource;
import com.viking.field_passport_generator.model.SourceType;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * ImageProviderStrategy defines the contract for different image loading behaviors.
 * It encapsulates the logic for both "Eager" (Notes) and "Lazy" (Satellite) loading.
 */
public interface ImageProviderStrategy {
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

    /**
     * Resolves the local file system path for the given resource.
     *
     * @param root the root directory of the cache.
     * @param id the identifier of the entity
     * @param params implementation-specific parameters used to construct the file path.
     * @return the resolved Path to the file.
     */
    Path resolvePath(Path root, String id, Map<String, String> params);

    SourceType getType();
}

