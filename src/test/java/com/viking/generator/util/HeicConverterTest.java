package com.viking.generator.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeicConverterTest {

    @Test
    @DisplayName("isAvailable = false, если ffmpeg не найден")
    void unavailableWhenBinaryMissing() {
        HeicConverter converter = new HeicConverter("no-such-ffmpeg-binary-xyz");
        assertFalse(converter.isAvailable());
    }

    @Test
    @DisplayName("toJpeg(null) и toJpeg(пусто) возвращают null")
    void rejectsNullOrEmpty() {
        HeicConverter converter = new HeicConverter("no-such-ffmpeg-binary-xyz");
        assertNull(converter.toJpeg(null));
        assertNull(converter.toJpeg(new byte[0]));
    }

    @Test
    @DisplayName("toJpeg без доступного ffmpeg возвращает null, а не исключение")
    void returnsNullWhenFfmpegMissing() {
        HeicConverter converter = new HeicConverter("no-such-ffmpeg-binary-xyz");
        byte[] heic = new byte[20];
        heic[4] = 0x66; heic[5] = 0x74; heic[6] = 0x79; heic[7] = 0x70; // "ftyp"
        heic[8] = 0x68; heic[9] = 0x65; heic[10] = 0x69; heic[11] = 0x63; // "heic"
        assertNull(converter.toJpeg(heic));
    }

    @Test
    @DisplayName("На машине разработчика ffmpeg должен быть доступен (smoke)")
    void ffmpegAvailableOnDevMachine() {
        // Smoke-тест: проект запускается только на машине оператора, где ffmpeg установлен.
        // Не скипается намеренно: конвертация HEIC — жёсткая зависимость этой машины.
        assertTrue(new HeicConverter("ffmpeg").isAvailable());
    }
}