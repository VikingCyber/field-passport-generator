package com.viking.field_passport_generator.service;

import com.viking.field_passport_generator.model.common.PassportKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GenerationTracker {
    private static final Logger log = LoggerFactory.getLogger(GenerationTracker.class);
    private final Map<PassportKey, Long> activeLocks = new ConcurrentHashMap<>();
    private static final long LOCK_TTL_MS = 600_000L;

    public boolean tryLock(PassportKey key) {
        long now = System.currentTimeMillis();
        Long currentVal = activeLocks.compute(key, (k, v) -> {
            if (v != null && (now - v <= LOCK_TTL_MS)) {
                return v;
            }
            return now;
        });
        return currentVal == now;
    }

    public void lock(PassportKey key) {
        activeLocks.put(key, System.currentTimeMillis());
        log.debug("Регистрация генерации: [{}] заблокирован", key);
    }

    public void unlock(PassportKey key) {
        activeLocks.remove(key);
        log.debug("Генерация завершена [{}] разблокирован", key);
    }

    public boolean isProcessing(PassportKey key) {
        Long lockTime = activeLocks.get(key);
        if (lockTime == null) {
            return false;
        }

        if (System.currentTimeMillis() - lockTime > LOCK_TTL_MS) {
            activeLocks.remove(key);
            log.warn("Блокировка для [{}] снята автоматически по таймауту (TTL expired)", key);
            return false;
        }
        return true;
    }

    public Set<PassportKey> getActiveLocks() {
        long now = System.currentTimeMillis();
        activeLocks.entrySet().removeIf(entry -> (now - entry.getValue()) > LOCK_TTL_MS);
        return Collections.unmodifiableSet(activeLocks.keySet());
    }
}
