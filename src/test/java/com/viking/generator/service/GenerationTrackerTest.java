package com.viking.generator.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.viking.generator.model.common.PassportKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GenerationTrackerTest {

    private static final PassportKey KEY = new PassportKey("field-1", 2025);

    @Test
    @DisplayName("tryLock регистрирует паспорт в очереди, повторный вызов до старта — отклоняется")
    void tryLockRegistersQueuedPassport() {
        GenerationTracker tracker = new GenerationTracker();
        assertTrue(tracker.tryLock(KEY));
        assertTrue(tracker.isQueued(KEY));
        assertFalse(tracker.tryLock(KEY), "Паспорт уже в очереди — повторный запрос должен отсекаться");
    }

    @Test
    @DisplayName("markActive переводит паспорт из очереди в активную генерацию")
    void markActiveMovesFromQueueToProcessing() {
        GenerationTracker tracker = new GenerationTracker();
        tracker.tryLock(KEY);
        tracker.markActive(KEY);
        assertFalse(tracker.isQueued(KEY), "После старта паспорт покидает очередь");
        assertTrue(tracker.isProcessing(KEY));
    }

    @Test
    @DisplayName("Повторный tryLock для активно генерирующегося паспорта отклоняется")
    void tryLockRejectsActiveGeneration() {
        GenerationTracker tracker = new GenerationTracker();
        tracker.tryLock(KEY);
        tracker.markActive(KEY);
        assertFalse(tracker.tryLock(KEY), "Дедупликация должна срабатывать и для активной генерации");
    }

    @Test
    @DisplayName("unlock снимает паспорт и с очереди, и с активной генерации")
    void unlockRemovesFromQueueAndActive() {
        GenerationTracker tracker = new GenerationTracker();
        tracker.tryLock(KEY);
        tracker.markActive(KEY);
        tracker.unlock(KEY);
        assertFalse(tracker.isQueued(KEY));
        assertFalse(tracker.isProcessing(KEY));
        assertTrue(tracker.tryLock(KEY), "После unlock паспорт можно регистрировать заново");
    }

    @Test
    @DisplayName("Паспорт в очереди НЕ протухает по TTL, сколько бы ни ждал слот")
    void queuedPassportDoesNotExpireByTtl() throws InterruptedException {
        GenerationTracker tracker = new GenerationTracker(100);
        tracker.tryLock(KEY);
        Thread.sleep(250);
        assertTrue(tracker.isQueued(KEY), "Ожидание в очереди не должно истекать по TTL");
    }

    @Test
    @DisplayName("Активная генерация, длящаяся дольше TTL, считается зависшей")
    void activeGenerationExpiresByTtl() throws InterruptedException {
        GenerationTracker tracker = new GenerationTracker(100);
        tracker.tryLock(KEY);
        tracker.markActive(KEY);
        Thread.sleep(250);
        assertFalse(tracker.isProcessing(KEY), "Активная генерация дольше TTL должна считаться зависшей");
    }

    @Test
    @DisplayName("getActiveLocks подчищает просроченные активные блокировки")
    void getActiveLocksDropsExpiredActive() throws InterruptedException {
        GenerationTracker tracker = new GenerationTracker(100);
        tracker.tryLock(KEY);
        tracker.markActive(KEY);
        Thread.sleep(250);
        assertTrue(tracker.getActiveLocks().isEmpty(), "Просроченная активная блокировка должна быть подчищена");
    }
}