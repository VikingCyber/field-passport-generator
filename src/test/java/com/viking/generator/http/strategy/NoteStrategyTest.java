package com.viking.generator.http.strategy;

import com.viking.generator.config.NoteConfig;
import com.viking.generator.http.LoadResult;
import com.viking.generator.http.LoadedResource;
import com.viking.generator.http.NoteImageLoader;
import com.viking.generator.util.HeicConverter;
import com.viking.generator.util.ImageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NoteStrategyTest {

    private NoteStrategy strategy;
    private NoteImageLoader loader;
    private HeicConverter heicConverter;
    private Path cacheDir;

    private static final byte[] JPEG = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x01};
    private static final byte[] HEIC = new byte[]{
            0x00, 0x00, 0x00, 0x18,
            0x66, 0x74, 0x79, 0x70, // ftyp
            0x68, 0x65, 0x69, 0x63  // heic
    };
    private static final byte[] PDF = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        loader = mock(NoteImageLoader.class);
        heicConverter = mock(HeicConverter.class);
        cacheDir = tempDir.resolve("cache");
        strategy = new NoteStrategy(loader, new NoteConfig("notes", "png", cacheDir), heicConverter);
    }

    @Test
    @DisplayName("Синхронизация сохраняет JPEG-вложение как .jpg, а не .png")
    void synchronizeSavesJpegWithRealExtension() {
        when(loader.load(List.of("a1"))).thenReturn(photo("a1", JPEG, "jpg"));

        strategy.synchronize(Set.of("a1"));

        Path saved = cacheDir.resolve("notes").resolve("a1.jpg");
        assertTrue(Files.exists(saved), "Должен появиться a1.jpg");
        assertFalse(Files.exists(cacheDir.resolve("notes").resolve("a1.png")), "a1.png не должно существовать");
        assertArrayEquals(JPEG, readBytes(saved));
    }

    @Test
    @DisplayName("PDF-вложение сохраняется с честным расширением .pdf")
    void synchronizePersistsPdfWithRealExtension() {
        when(loader.load(List.of("b1"))).thenReturn(photo("b1", PDF, "pdf"));

        strategy.synchronize(Set.of("b1"));

        Path saved = cacheDir.resolve("notes").resolve("b1.pdf");
        assertTrue(Files.exists(saved), "Должен появиться b1.pdf");
        assertFalse(Files.exists(cacheDir.resolve("notes").resolve("b1.png")), "b1.png не должно существовать");
    }

    @Test
    @DisplayName("HEIC-вложение конвертируется и сохраняется как .jpg")
    void synchronizeConvertsHeicAndSavesAsJpg() {
        when(heicConverter.toJpeg(HEIC)).thenReturn(JPEG);
        when(loader.load(List.of("c1"))).thenReturn(photo("c1", HEIC, "heic"));

        strategy.synchronize(Set.of("c1"));

        verify(heicConverter).toJpeg(HEIC);
        Path saved = cacheDir.resolve("notes").resolve("c1.jpg");
        assertTrue(Files.exists(saved), "Должен появиться c1.jpg после конвертации HEIC");
        assertArrayEquals(JPEG, readBytes(saved));
        assertFalse(Files.exists(cacheDir.resolve("notes").resolve("c1.heic")), "Исходник HEIC не должен сохраняться");
    }

    @Test
    @DisplayName("Ремонт кэша переименовывает JPEG под именем .png в .jpg")
    void repairRenamesHistoricalMisnamedFiles() throws IOException {
        Path notes = cacheDir.resolve("notes");
        Files.createDirectories(notes);
        Files.write(notes.resolve("old.png"), JPEG);

        strategy.synchronize(Set.of("old"));

        assertFalse(Files.exists(notes.resolve("old.png")), "old.png должен перестать существовать");
        Path renamed = notes.resolve("old.jpg");
        assertTrue(Files.exists(renamed), "Должен появиться old.jpg");
        assertArrayEquals(JPEG, readBytes(renamed));
        assertTrue(ImageUtils.detectKind(readBytes(renamed)) == ImageUtils.ImageKind.JPEG);
    }

    @Test
    @DisplayName("Синхронизация пропускает «мёртвые» id из кэша без запроса к API")
    void synchronizeSkipsCachedDeadIds() throws IOException {
        Files.createDirectories(cacheDir);
        Files.write(cacheDir.resolve(".notes-dead-ids"), List.of("z1"), StandardCharsets.UTF_8);

        strategy.synchronize(Set.of("z1"));

        verifyNoInteractions(loader);
        assertFalse(Files.exists(cacheDir.resolve("notes").resolve("z1.jpg")),
                "Для мёртвого id не должно создаваться файлов");
    }

    @Test
    @DisplayName("Подтверждённо отсутствующие id сохраняются в кэш и пропускаются при следующем синке")
    void deadIdsPersistedAndSkippedNextRun() {
        when(loader.load(List.of("d1")))
                .thenReturn(new LoadResult(Map.of(), 1, 0, 0, Map.of(), Set.of("d1")));

        strategy.synchronize(Set.of("d1"));

        Path deadFile = cacheDir.resolve(".notes-dead-ids");
        assertTrue(Files.exists(deadFile), "Файл мёртвых id должен создаться");
        assertTrue(readLines(deadFile).contains("d1"), "d1 должен попасть в кэш мёртвых id");

        // повторный синк — API больше не опрашивается
        strategy.synchronize(Set.of("d1"));
        verify(loader, times(1)).load(anyList());
    }

    private static LoadResult photo(String id, byte[] data, String ext) {
        return new LoadResult(Map.of(id, new LoadedResource(data, ext)), 1, 1, 0);
    }

    private static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}