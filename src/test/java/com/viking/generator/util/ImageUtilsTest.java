package com.viking.generator.util;

import com.viking.generator.util.ImageUtils.ImageKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageUtilsTest {

    @Test
    @DisplayName("JPEG определяется по сигнатуре FF D8 FF")
    void detectsJpeg() {
        assertEquals(ImageKind.JPEG, ImageUtils.detectKind(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01}));
    }

    @Test
    @DisplayName("PNG определяется по сигнатуре 89 50 4E 47")
    void detectsPng() {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D};
        assertEquals(ImageKind.PNG, ImageUtils.detectKind(png));
    }

    @Test
    @DisplayName("GIF определяется по сигнатуре GIF8")
    void detectsGif() {
        assertEquals(ImageKind.GIF, ImageUtils.detectKind("GIF89a".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    @DisplayName("BMP определяется по сигнатуре BM")
    void detectsBmp() {
        byte[] bmp = new byte[]{0x42, 0x4D, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        assertEquals(ImageKind.BMP, ImageUtils.detectKind(bmp));
    }

    @Test
    @DisplayName("WEBP определяется по RIFF....WEBP")
    void detectsWebp() {
        byte[] webp = new byte[]{0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};
        assertEquals(ImageKind.WEBP, ImageUtils.detectKind(webp));
    }

    @Test
    @DisplayName("PDF определяется по сигнатуре %PDF-")
    void detectsPdf() {
        assertEquals(ImageKind.PDF, ImageUtils.detectKind("%PDF-1.6".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    @DisplayName("HEIC с брендом heic определяется как HEIC")
    void detectsHeic() {
        byte[] raw = new byte[12];
        writeAscii(raw, 4, "ftyp");
        writeAscii(raw, 8, "heic");
        assertEquals(ImageKind.HEIC, ImageUtils.detectKind(raw));
    }

    @Test
    @DisplayName("HEIF с брендом mif1/msf1 определяется как HEIC")
    void detectsHeifBrands() {
        for (String brand : new String[]{"mif1", "msf1", "avif"}) {
            byte[] raw = new byte[12];
            writeAscii(raw, 4, "ftyp");
            writeAscii(raw, 8, brand);
            assertEquals(ImageKind.HEIC, ImageUtils.detectKind(raw), "brand=" + brand);
        }
    }

    @Test
    @DisplayName("Короткий или неизвестный массив — UNKNOWN")
    void detectsUnknown() {
        assertEquals(ImageKind.UNKNOWN, ImageUtils.detectKind(null));
        assertEquals(ImageKind.UNKNOWN, ImageUtils.detectKind(new byte[]{1, 2, 3}));
        assertEquals(ImageKind.UNKNOWN, ImageUtils.detectKind("просто текст".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("extensionFor возвращает честное расширение по формату")
    void extensionForMapsKindsToExtensions() {
        assertEquals("jpg", ImageUtils.extensionFor(ImageKind.JPEG));
        assertEquals("png", ImageUtils.extensionFor(ImageKind.PNG));
        assertEquals("gif", ImageUtils.extensionFor(ImageKind.GIF));
        assertEquals("bmp", ImageUtils.extensionFor(ImageKind.BMP));
        assertEquals("webp", ImageUtils.extensionFor(ImageKind.WEBP));
        assertEquals("heic", ImageUtils.extensionFor(ImageKind.HEIC));
        assertEquals("pdf", ImageUtils.extensionFor(ImageKind.PDF));
        assertEquals(null, ImageUtils.extensionFor(ImageKind.UNKNOWN));
        assertEquals(null, ImageUtils.extensionFor(null));
    }

    private static void writeAscii(byte[] data, int offset, String s) {
        for (int i = 0; i < s.length(); i++) {
            data[offset + i] = (byte) s.charAt(i);
        }
    }
}