package com.viking.field_passport_generator.service.orchestration;

import com.viking.field_passport_generator.model.FieldPassport;
import com.viking.field_passport_generator.service.ImageSyncService;
import com.viking.field_passport_generator.service.PassportGeneratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class PassportOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(PassportOrchestrator.class);

    private final ImageSyncService syncService;
    private final PassportGeneratorService pdfService;
    private final Semaphore memoryGuard;

    public PassportOrchestrator(ImageSyncService syncService, PassportGeneratorService pdfService,
                                int maxConcurrentTasks) {
        this.syncService = syncService;
        this.pdfService = pdfService;
        this.memoryGuard = new Semaphore(maxConcurrentTasks);
    }

    public void processMassGeneration(List<FieldPassport> passports) {
        if (passports == null || passports.isEmpty()) {
            log.warn("Список паспортов пуст или null. Генерация пропущена.");
            return;
        }
        log.info("Начало массовой генерации. Всего задач: {}", passports.size());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (FieldPassport passport : passports) {
                executor.submit(() -> {
                    processSinglePassport(passport);
                    return null;
                });
            }
        }
        log.info("Все задачи по генерации успешно завершены.");
    }

    private void processSinglePassport(FieldPassport passport) {
        boolean acquired = false;
        try {
            memoryGuard.acquire();
            acquired = true;
            log.debug("Обработка поля {}: Загрузка данных...", passport.generalInfo().fieldName());
            syncService.prepareSinglePassport(passport);
            log.debug("Обработка поля {}: Рендеринг PDF...", passport.generalInfo().fieldName());
            pdfService.generate(passport);
        } catch (InterruptedException e) {
            log.error("Поток прерван для поля {}", passport.generalInfo().fieldName());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Критическая ошибка при обработке поля {}", passport.generalInfo().fieldName(), e);
        } finally {
            passport.clearImageData();
            if (acquired) {
                memoryGuard.release();
            }
        }
    }
}
