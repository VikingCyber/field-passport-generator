package com.viking.generator.data.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.viking.generator.model.FieldPassport;
import com.viking.generator.model.sections.GeneralInfo;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InMemoryDataProviderTest {

    @TempDir
    Path tempDir;

    private InMemoryDataProvider provider;

    @BeforeEach
    void setUp() {
        provider = new InMemoryDataProvider(tempDir.toString(), ".pdf");
    }

    @Test
    @DisplayName("refreshFromFiles строит индекс, поиск паспорта за O(1) по ключу")
    void refreshFromFilesBuildsPassportIndex() {
        // --- ARRANGE ---
        FileDataProvider fileLoader = mock(FileDataProvider.class);
        FieldPassport p1 = passport("field-1", "Поле №1", 2025);
        FieldPassport p2 = passport("field-2", "Поле №2", 2024);
        when(fileLoader.getPassportsData()).thenReturn(List.of(p1, p2));

        // --- ACT ---
        provider.refreshFromFiles(fileLoader);

        // --- ASSERT ---
        assertEquals(p1, provider.findPassport("field-1", 2025).orElse(null),
                "Индекс должен находить паспорт по {fieldId, year} за O(1)");
        assertEquals(p2, provider.findPassport("field-2", 2024).orElse(null));
        assertFalse(provider.findPassport("field-1", 2024).isPresent(),
                "Неверная комбинация {fieldId, year} не должна находиться");
        assertFalse(provider.findPassport("unknown", 2025).isPresent());
        assertEquals(2, provider.getPassportsData().size());
    }

    @Test
    @DisplayName("registerNewPassport добавляет паспорт в индекс, removePassport удаляет его")
    void registerAndRemovePassportKeepIndexConsistent() {
        // --- ARRANGE ---
        FieldPassport p1 = passport("field-1", "Поле №1", 2025);

        // --- ACT ---
        provider.registerNewPassport(p1);

        // --- ASSERT ---
        assertTrue(provider.findPassport("field-1", 2025).isPresent(),
                "После регистрации паспорт должен быть доступен через индекс");
        assertTrue(provider.findPassportPath("field-1", 2025).isPresent(),
                "Путь сгенерированного паспорта должен быть в кэше");

        // --- ACT 2 ---
        provider.removePassport("field-1", 2025);

        // --- ASSERT 2 ---
        assertFalse(provider.findPassport("field-1", 2025).isPresent(),
                "После удаления паспорт не должен находиться через индекс");
        assertFalse(provider.findPassportPath("field-1", 2025).isPresent());
    }

    private FieldPassport passport(String fieldId, String fieldName, int year) {
        return new FieldPassport(
                fieldId,
                new GeneralInfo("ЦХ", fieldName, 100.0, year, List.of()),
                List.of(),
                null,
                null,
                List.of(),
                List.of()
        );
    }
}
