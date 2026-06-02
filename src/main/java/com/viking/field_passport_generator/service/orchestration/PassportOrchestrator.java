package com.viking.field_passport_generator.service.orchestration;

import com.viking.field_passport_generator.data.provider.InMemoryDataProvider;
import com.viking.field_passport_generator.model.FieldPassport;
import com.viking.field_passport_generator.model.common.PassportKey;
import com.viking.field_passport_generator.model.common.PassportStatus;
import com.viking.field_passport_generator.service.GenerationTracker;
import com.viking.field_passport_generator.service.SyncService;
import com.viking.field_passport_generator.service.PassportGeneratorService;
import com.viking.field_passport_generator.web.dto.PassportSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

public class PassportOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(PassportOrchestrator.class);

    private final SyncService syncService;
    private final PassportGeneratorService pdfService;
    private final InMemoryDataProvider cacheProvider;
    private final Semaphore memoryGuard;
    private final Consumer<FieldPassport> onPassportGenerated;
    private final GenerationTracker generationTracker;


    public PassportOrchestrator(SyncService syncService, PassportGeneratorService pdfService,
                                InMemoryDataProvider cacheProvider, int maxConcurrentTasks,
                                Consumer<FieldPassport> onPassportGenerated, GenerationTracker generationTracker) {
        this.syncService = syncService;
        this.pdfService = pdfService;
        this.cacheProvider = cacheProvider;
        this.memoryGuard = new Semaphore(maxConcurrentTasks);
        this.onPassportGenerated = onPassportGenerated;
        this.generationTracker = generationTracker;
    }

    public void syncAllEcosystemData() throws Exception {
        log.info("Оркестратор получил запрос на синхронизацию, делегирую задачу в AgroDataSyncService...");
        syncService.syncAndRefreshEcosystem();
    }

    public PassportStatus getPassportStatus(String fieldId, int year) {
        if (generationTracker.isProcessing(new PassportKey(fieldId, year))) {
            return PassportStatus.PROCESSING;
        }
        return getVerifiedPassportPath(fieldId, year).isPresent() ? PassportStatus.READY : PassportStatus.NOT_FOUND;
    }

    public List<PassportSummary> getPassportSummaries() {
        List<PassportSummary> summaries = cacheProvider.getPassportSummaries();
        return summaries.stream()
                .map(s -> {
                    PassportKey key = new PassportKey(s.id(), s.year());
                    if (generationTracker.isProcessing(key)) {
                        return new PassportSummary(
                                s.id(),
                                s.fieldName(),
                                s.cropName(),
                                s.year(),
                                s.area(),
                                PassportStatus.PROCESSING.name()
                        );
                    }
                    return s;
                }).toList();
    }

    public void startMassGeneration(boolean force) {
        this.processMassGeneration(cacheProvider.getPassportsData(), force);
    }

    public Optional<Path> getVerifiedPassportPath(String fieldId, int year) {
        FieldPassport passport = cacheProvider.getPassportsData().stream()
                .filter(p -> p.fieldId().equals(fieldId))
                .filter(p -> p.generalInfo().year() == year)
                .findFirst()
                .orElse(null);
        if (passport == null) {
            log.warn("Запрошен путь к паспорту, которого нет в базе данных. ID: {}, Год: {}", fieldId, year);
            return Optional.empty();
        }
        Path filePath = pdfService.resolvePassportPath(passport);
        if (!filePath.toFile().exists()) {
            log.error("Паспорт в кэше есть, но файл отсутствует на диске. ID: {}, Файл: {}",
                    fieldId, filePath.getFileName());
            return Optional.empty();
        }
        return Optional.of(filePath);
    }

    public void processMassGeneration(List<FieldPassport> passports, boolean force) {
        if (passports == null || passports.isEmpty()) {
            log.warn("Список паспортов пуст или null. Генерация пропущена.");
            return;
        }
        log.info("Начало массовой генерации. Всего задач: {}", passports.size());

        CountDownLatch latch = new CountDownLatch(passports.size());

        for (FieldPassport passport : passports) {
            Thread.startVirtualThread(() -> {
                try {
                    processSinglePassport(passport, force);
                } catch (Exception e) {
                    log.error("Ошибка при генерации паспорта {}: {}", passport.fieldId(), e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        Thread.startVirtualThread(() -> {
            try {
                latch.await();
                log.info("✅ Все {} задач по генерации успешно завершены.", passports.size());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Ожидание завершения было прервано");
            }
        });

        log.info("Все задачи массовой генерации ЗАПУЩЕНЫ. Всего задач: {}", passports.size());
    }

    public void startSingleGeneration(String fieldId, int year, boolean force) {
        FieldPassport passport = cacheProvider.getPassportsData().stream()
                .filter(p -> p.fieldId().equals(fieldId))
                .filter(p -> p.generalInfo().year() == year)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Паспорт не найден в кэше для ID: " + fieldId + " Год: " + year
                ));
        Thread.startVirtualThread(() -> {
            log.info("⚙️ Запущена точечная асинхронная генерация для поля: {} ({})",
                    passport.generalInfo().fieldName(), year);
            processSinglePassport(passport, force);
        });
    }

    private void processSinglePassport(FieldPassport passport, boolean force) {
        PassportKey key = new PassportKey(passport.fieldId(), passport.generalInfo().year());

        if (!generationTracker.tryLock(key)) {
            log.info("Паспорт {} уже находится в процессе генерации, запрос пропущен.", passport.fieldId());
            return;
        }

        if (!force && pdfService.resolvePassportPath(passport).toFile().exists()) {
            log.info("Паспорт {} уже существует, генерация пропущена (force=false)", passport.fieldId());
            return;
        }
        boolean acquired = false;
        try {
            memoryGuard.acquire();
            acquired = true;
            generationTracker.lock(key);
            log.debug("Обработка поля {}: Загрузка данных...", passport.generalInfo().fieldName());
            syncService.prepareSinglePassport(passport);
            log.debug("Обработка поля {}: Рендеринг PDF...", passport.generalInfo().fieldName());
            pdfService.generate(passport);
            if (onPassportGenerated != null) {
                onPassportGenerated.accept(passport);
            }
        } catch (InterruptedException e) {
            log.error("Поток прерван для поля {}", passport.generalInfo().fieldName());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Критическая ошибка при обработке поля {}", passport.generalInfo().fieldName(), e);
        } finally {
            passport.clearImageData();
            generationTracker.unlock(key);
            if (acquired) {
                memoryGuard.release();
            }
        }
    }
}
