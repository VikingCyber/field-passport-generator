package com.viking.field_passport_generator.service.orchestration;

import com.viking.field_passport_generator.data.provider.InMemoryDataProvider;
import com.viking.field_passport_generator.model.FieldPassport;
import com.viking.field_passport_generator.service.ImageSyncService;
import com.viking.field_passport_generator.service.PassportGeneratorService;
import com.viking.field_passport_generator.web.dto.PassportSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

public class PassportOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(PassportOrchestrator.class);

    private final ImageSyncService syncService;
    private final PassportGeneratorService pdfService;
    private final InMemoryDataProvider cacheProvider;
    private final Semaphore memoryGuard;
    private final Consumer<FieldPassport> onPassportGenerated;


    public PassportOrchestrator(ImageSyncService syncService, PassportGeneratorService pdfService,
                                InMemoryDataProvider cacheProvider, int maxConcurrentTasks,
                                Consumer<FieldPassport> onPassportGenerated) {
        this.syncService = syncService;
        this.pdfService = pdfService;
        this.cacheProvider = cacheProvider;
        this.memoryGuard = new Semaphore(maxConcurrentTasks);
        this.onPassportGenerated = onPassportGenerated;
    }

    public List<PassportSummary> getPassportSummaries() {
        return cacheProvider.getPassportSummaries();
    }

    public void startMassGeneration() {
        this.processMassGeneration(cacheProvider.getPassportsData());
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
            if (acquired) {
                memoryGuard.release();
            }
        }
    }
}
