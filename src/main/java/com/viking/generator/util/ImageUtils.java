package com.viking.generator.util;

import com.viking.generator.data.dto.note.Data;

public class ImageUtils {

    /** Реальный формат изображения, определённый по магическим байтам. */
    public enum ImageKind { JPEG, PNG, GIF, BMP, WEBP, HEIC, PDF, UNKNOWN }

    /**
     * Определяет реальный формат файла по магическим байтам.
     * Вложения AgroSignal иногда имеют расширение, не соответствующее содержимому
     * (например, JPEG/HEIC/PDF, сохранённые как .png), поэтому полагаться нужно
     * только на сигнатуру, а не на имя файла.
     */
    public static ImageKind detectKind(byte[] data) {
        if (data == null || data.length < 4) return ImageKind.UNKNOWN;

        if (startsWith(data, 0, 0xFF, 0xD8, 0xFF)) return ImageKind.JPEG;
        if (startsWith(data, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return ImageKind.PNG;
        if (startsWithAscii(data, 0, "GIF8")) return ImageKind.GIF;
        if (startsWith(data, 0, 0x42, 0x4D)) return ImageKind.BMP;
        if (data.length >= 12 && startsWith(data, 0, 0x52, 0x49, 0x46, 0x46)
                && startsWith(data, 8, 0x57, 0x45, 0x42, 0x50)) return ImageKind.WEBP;
        if (startsWith(data, 0, 0x25, 0x50, 0x44, 0x46)) return ImageKind.PDF;

        // HEIF/HEIC/AVIF — ISO Base Media File Format: байты [4..8) == "ftyp"
        if (startsWith(data, 4, 0x66, 0x74, 0x79, 0x70)) {
            String brand = ascii(data, 8, 4);
            if (brand.startsWith("heic") || brand.startsWith("heix")
                    || brand.startsWith("hevc") || brand.startsWith("hevx")
                    || brand.startsWith("heim") || brand.startsWith("heis")
                    || brand.startsWith("hevm") || brand.startsWith("hevs")
                    || brand.startsWith("mif1") || brand.startsWith("msf1")
                    || brand.startsWith("avif") || brand.startsWith("avis")) {
                return ImageKind.HEIC;
            }
        }
        return ImageKind.UNKNOWN;
    }

    /**
     * Расширение файла (без точки), соответствующее реальному формату.
     * Используется, чтобы кэш-файлы вложений сохранялись с честным расширением,
     * а не все под .png. Для нераспознанного формата — {@code null}.
     */
    public static String extensionFor(ImageKind kind) {
        if (kind == null) return null;
        return switch (kind) {
            case JPEG -> "jpg";
            case PNG -> "png";
            case GIF -> "gif";
            case BMP -> "bmp";
            case WEBP -> "webp";
            case HEIC -> "heic";
            case PDF -> "pdf";
            case UNKNOWN -> null;
        };
    }

    private static boolean startsWith(byte[] data, int offset, int... expected) {
        if (offset + expected.length > data.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }

    private static boolean startsWithAscii(byte[] data, int offset, String ascii) {
        if (offset + ascii.length() > data.length) return false;
        for (int i = 0; i < ascii.length(); i++) {
            if ((data[offset + i] & 0xFF) != ascii.charAt(i)) return false;
        }
        return true;
    }

    private static String ascii(byte[] data, int offset, int length) {
        if (offset + length > data.length) return "";
        StringBuilder sb = new StringBuilder(length);
        for (int i = offset; i < offset + length; i++) {
            sb.append((char) data[i]);
        }
        return sb.toString();
    }

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