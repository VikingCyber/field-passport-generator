package com.viking.generator.service.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.viking.generator.data.provider.InMemoryDataProvider;
import com.viking.generator.model.FieldPassport;
import com.viking.generator.model.common.PassportKey;
import com.viking.generator.model.common.PassportStatus;
import com.viking.generator.model.sections.GeneralInfo;
import com.viking.generator.service.GenerationTracker;
import com.viking.generator.service.PassportGeneratorService;
import com.viking.generator.service.SyncService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PassportOrchestratorTest {

    @TempDir
    Path tempDir;

    private SyncService syncService;
    private PassportGeneratorService pdfService;
    private InMemoryDataProvider cacheProvider;
    private GenerationTracker generationTracker;
    private PassportOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        syncService = mock(SyncService.class);
        pdfService = mock(PassportGeneratorService.class);
        cacheProvider = mock(InMemoryDataProvider.class);
        generationTracker = new GenerationTracker();
        orchestrator = new PassportOrchestrator(
                syncService, pdfService, cacheProvider, 4, p -> {}, generationTracker);
    }

    @Test
    @DisplayName("Пропуск уже существующего паспорта (force=false) не должен оставлять фантомную блокировку и ложный PROCESSING")
    void skipForExistingPdfMustNotLeavePhantomLock() throws Exception {
        // --- ARRANGE ---
        FieldPassport passport = passport("field-1", "Поле №1", 2025);
        Path existingPdf = tempDir.resolve("field-1_2025.pdf");
        Files.writeString(existingPdf, "уже готовый pdf");

        AtomicBoolean skipBranchReached = new AtomicBoolean(false);
        when(pdfService.resolvePassportPath(passport)).thenAnswer(inv -> {
            skipBranchReached.set(true);
            return existingPdf;
        });
        when(cacheProvider.findPassport("field-1", 2025)).thenReturn(Optional.of(passport));

        // --- ACT ---
        // «Актуализировать паспорта»: force=false, PDF уже есть → генерация должна быть пропущена
        orchestrator.processMassGeneration(List.of(passport), false);

        // --- ASSERT ---
        // Дожидаемся, пока виртуальный поток дойдёт до ветки пропуска
        awaitUntil(skipBranchReached::get, 5000, "Поток не дошёл до ветки «паспорт уже существует»");
        // После завершения пропуска паспорт не должен числиться ни в очереди, ни в активной генерации.
        // БАГ: tryLock уже записал ключ, а ветка пропуска выходит без unlock → ключ висит до TTL (10 минут)
        awaitUntil(() -> generationTracker.getQueuedLocks().isEmpty()
                        && generationTracker.getActiveLocks().isEmpty(), 5000,
                "После пропуска существующего паспорта осталась фантомная блокировка");
        assertEquals(PassportStatus.READY, orchestrator.getPassportStatus("field-1", 2025),
                "Существующий паспорт должен остаться READY, а не стать PROCESSING");
    }

    @Test
    @DisplayName("Повторная генерация с force=true после пропуска не должна молча блокироваться фантомным локом")
    void forcedRegenerationAfterSkipMustNotBeSilentlyBlocked() throws Exception {
        // --- ARRANGE ---
        FieldPassport passport = passport("field-1", "Поле №1", 2025);
        PassportKey key = new PassportKey("field-1", 2025);
        Path existingPdf = tempDir.resolve("field-1_2025.pdf");
        Files.writeString(existingPdf, "уже готовый pdf");

        AtomicBoolean skipBranchReached = new AtomicBoolean(false);
        AtomicInteger generateCalls = new AtomicInteger(0);
        when(pdfService.resolvePassportPath(passport)).thenAnswer(inv -> {
            skipBranchReached.set(true);
            return existingPdf;
        });
        doAnswer(inv -> {
            generateCalls.incrementAndGet();
            return null;
        }).when(pdfService).generate(passport);
        when(cacheProvider.getPassportsData()).thenReturn(List.of(passport));

        // --- ACT ---
        // Первый запуск «Актуализировать» (force=false): PDF есть → пропуск
        orchestrator.processMassGeneration(List.of(passport), false);
        awaitUntil(skipBranchReached::get, 5000, "Первый поток не дошёл до ветки пропуска");
        // Гарантируем, что первый вызов полностью завершился: блокировка снята (или не создана)
        awaitUntil(() -> !generationTracker.isProcessing(key), 5000,
                "Первый пропуск оставил фантомную блокировку — следующий запуск будет молча проигнорирован");

        // Второй запуск «Пересоздать всё» (force=true): должна пройти реальная генерация
        orchestrator.processMassGeneration(List.of(passport), true);

        // --- ASSERT ---
        awaitUntil(() -> generateCalls.get() > 0, 5000,
                "Повторная генерация с force=true молча пропущена из-за фантомной блокировки");
    }

    @Test
    @DisplayName("Паспорт, ожидающий слот в массовой генерации, остаётся QUEUED и не теряет позицию по TTL")
    void waitingPassportMustNotLoseLockByTtlWhileHoldingSlot() throws Exception {
        // Короткий TTL (1,2 с) и всего 1 слот: один паспорт генерируется (завис на latch),
        // второй ждёт слот дольше TTL и должен остаться в очереди (QUEUED).
        GenerationTracker shortTtlTracker = new GenerationTracker(1_200);
        PassportOrchestrator oneSlotOrchestrator = new PassportOrchestrator(
                syncService, pdfService, cacheProvider, 1, p -> {}, shortTtlTracker);

        FieldPassport first = passport("field-1", "Поле №1", 2025);
        FieldPassport waiting = passport("field-2", "Поле №2", 2025);
        CountDownLatch holdGeneration = new CountDownLatch(1);
        // Генерация любого паспорта «зависает», пока держим единственный слот занятым.
        // Кто именно займёт слот первым — гонка виртуальных потоков, нам это неважно:
        // важно, что оставшийся в очереди не теряет позицию по TTL.
        doAnswer(inv -> {
            holdGeneration.await();
            return null;
        }).when(pdfService).generate(any());

        try {
            // --- ACT ---
            oneSlotOrchestrator.processMassGeneration(List.of(first, waiting), true);

            // Один паспорт вышел в активную генерацию, второй остался в очереди
            awaitUntil(() -> shortTtlTracker.getActiveLocks().size() == 1
                            && shortTtlTracker.getQueuedLocks().size() == 1, 5000,
                    "Один паспорт должен генерироваться, второй — ждать слот");

            // Ждём заметно дольше TTL активной блокировки (1,2 с)
            Thread.sleep(2_500);

            // --- ASSERT ---
            // Ожидающий паспорт обязан остаться в очереди — позиция не теряется по TTL
            assertEquals(1, shortTtlTracker.getQueuedLocks().size(),
                    "Ожидающий паспорт не должен терять позицию в очереди по TTL");

            // Через оркестратор его статус обязан быть QUEUED, а не READY/NOT_FOUND
            PassportKey queuedKey = shortTtlTracker.getQueuedLocks().iterator().next();
            assertEquals(PassportStatus.QUEUED,
                    oneSlotOrchestrator.getPassportStatus(queuedKey.fieldId(), queuedKey.year()),
                    "Статус ожидающего паспорта должен быть QUEUED");
        } finally {
            holdGeneration.countDown();
        }
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

    private void awaitUntil(BooleanSupplier condition, long timeoutMillis, String failureMessage)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        fail(failureMessage);
    }
}