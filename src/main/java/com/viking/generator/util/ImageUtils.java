package com.viking.generator.util;

import com.viking.generator.data.dto.note.Data;

public class ImageUtils {

    /**
     * Универсальный метод для извлечения расширения из строки (пути или имени файла).
     * Используется для спутниковых снимков и общих задач.
     */
    public static String determineExtension(String path) {
        if (path == null || path.isBlank()) {
            return "png"; // дефолт для спутников
        }
        // Очищаем от параметров запроса (например, ?token=...), если они есть
        String cleanPath = path.contains("?") ? path.substring(0, path.indexOf("?")) : path;

        if (cleanPath.contains(".")) {
            return cleanPath.substring(cleanPath.lastIndexOf(".") + 1).toLowerCase();
        }
        return "png";
    }

    /**
     * Продвинутый метод для объектов Data (заметки).
     * Сначала пытается взять из имени, затем из mimeType.
     */
    public static String determineExtension(Data data) {
        if (data == null) {
            return "jpg";
        }

        String fileName = data.name();
        // Если в имени файла есть точка, используем базовый метод
        if (fileName != null && fileName.contains(".")) {
            return determineExtension(fileName);
        }

        String mimeType = (data.meta() != null) ? data.meta().mimeType() : null;
        if (mimeType != null && mimeType.contains("/")) {
            String extension = mimeType.substring(mimeType.lastIndexOf("/") + 1).toLowerCase();
            return "jpeg".equals(extension) ? "jpg" : extension;
        }

        return "jpg";
    }
}