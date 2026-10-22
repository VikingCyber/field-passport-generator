package com.viking.generator.service;

import com.viking.generator.model.common.PassportKey;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Отслеживает паспорта, зарегистрированные на генерацию.
 *
 * <p>Состояния разделены на два контейнера:
 * <ul>
 *   <li>{@link #queued} — паспорт зарегистрирован и <b>ждёт свободный слот</b> в очереди
 *       массовой генерации. Ожидание может длиться сколько угодно долго, поэтому для
 *       очереди TTL не применяется — в противном случае паспорта в длинной очереди
 *       «протухали» бы ещё до начала генерации.</li>
 *   <li>{@link #activeLocks} — паспорт <b>реально генерируется</b> (получил слот).
 *       Здесь действует TTL как защита от зависшей генерации.</li>
 * </ul>
 */
public class GenerationTracker {
    /** Стандартный срок жизни активной блокировки: 10 минут. */
    public static final long DEFAULT_LOCK_TTL_MS = 600_000L;

    private final Set<PassportKey> queued = ConcurrentHashMap.newKeySet();
    private final Map<PassportKey, Long> activeLocks = new ConcurrentHashMap<>();
    private final long lockTtlMs;

    public GenerationTracker() {
        this(DEFAULT_LOCK_TTL_MS);
    }

    /**
     * @param lockTtlMs срок жизни активной блокировки в мс (в тестах берут короткий,
     *                  в проде используется {@link #DEFAULT_LOCK_TTL_MS})
     */
    public GenerationTracker(long lockTtlMs) {
        this.lockTtlMs = lockTtlMs;
    }

    /**
     * Регистрирует паспорт на генерацию (ставит его в очередь). Возвращает {@code true},
     * если паспорт только что зарегистрирован. Повторный вызов, пока паспорт либо стоит в
     * очереди, либо активно генерируется (в пределах TTL), вернёт {@code false} — так
     * работает дедупликация запросов на один и тот же паспорт.
     *
     * <p>Метод синхронизирован: проверка и постановка в очередь должны быть атомарными,
     * иначе два потока одновременно оба «выиграют» блокировку одного паспорта.
     */
    public synchronized boolean tryLock(PassportKey key) {
        if (queued.contains(key)) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long activeTime = activeLocks.get(key);
        if (activeTime != null && (now - activeTime <= lockTtlMs)) {
            return false;
        }
        return queued.add(key);
    }

    /**
     * Переводит паспорт из очереди в активную генерацию (после получения слота семафора)
     * и ставит свежую метку времени для отсчёта TTL.
     */
    public void markActive(PassportKey key) {
        queued.remove(key);
        activeLocks.put(key, System.currentTimeMillis());
    }

    /** Паспорт стоит в очереди и ждёт слот? (чистое чтение, без побочных эффектов) */
    public boolean isQueued(PassportKey key) {
        return queued.contains(key);
    }

    /** Паспорт реально генерируется в данный момент? (чистое чтение, без побочных эффектов) */
    public boolean isProcessing(PassportKey key) {
        Long lockTime = activeLocks.get(key);
        if (lockTime == null) {
            return false;
        }
        return System.currentTimeMillis() - lockTime <= lockTtlMs;
    }

    /** Снимает паспорт и с очереди, и с активной генерации. */
    public void unlock(PassportKey key) {
        queued.remove(key);
        activeLocks.remove(key);
    }

    /** Ключи паспортов, ожидающих слот в очереди. */
    public Set<PassportKey> getQueuedLocks() {
        return Collections.unmodifiableSet(queued);
    }

    /** Ключи паспортов, активно генерирующихся; просроченные по TTL подчищаются. */
    public Set<PassportKey> getActiveLocks() {
        long now = System.currentTimeMillis();
        activeLocks.entrySet().removeIf(entry -> (now - entry.getValue()) > lockTtlMs);
        return Collections.unmodifiableSet(activeLocks.keySet());
    }
}
