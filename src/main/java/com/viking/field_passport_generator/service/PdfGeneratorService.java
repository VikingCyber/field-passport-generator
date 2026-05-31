package com.viking.field_passport_generator.service;

import com.viking.field_passport_generator.model.*;
import com.viking.field_passport_generator.model.media.ChartImage;
import com.viking.field_passport_generator.model.media.SatelliteImage;
import com.viking.field_passport_generator.model.media.NoteImage;
import com.viking.field_passport_generator.model.tables.NoteTableRow;
import com.viking.field_passport_generator.model.tables.OperationTableRow;
import com.viking.field_passport_generator.model.tables.TechJournalTableRow;
import com.viking.field_passport_generator.util.NoteImageComparators;
import com.viking.field_passport_generator.util.PdfUIHelper;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.PageSize;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PdfGeneratorService implements PassportGeneratorService {
    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);
    private static final int MAX_DELETE_ATTEMPTS = 3;
    private static final long DELETE_RETRY_DELAY_MS = 50;
    private final long minRequiredSpaceBytes;
    private final Path outputDir;

    public PdfGeneratorService(long minRequiredSpaceBytes, Path outputDir) {
        this.minRequiredSpaceBytes = minRequiredSpaceBytes;
        this.outputDir = Objects.requireNonNull(outputDir, "OutputDir must not be null");
    }


    @Override
    public void generate(FieldPassport passport) {
        Path filePath = resolvePassportPath(passport);
        String uniqueTempName = filePath.getFileName().toString().replace(".pdf", "_" + UUID.randomUUID() + ".tmp");
        Path tempPath = filePath.resolveSibling(uniqueTempName);
        log.info("==> Старт генерации PDF для поля: {} (Файл: {})", passport.generalInfo().fieldName(), filePath.getFileName());

        checkStorageSafety(outputDir);

        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            log.error("Критическая ошибка: не удалось подготовить директорию {}. Причина: {}",
                outputDir.toAbsolutePath(), e.getMessage());
            throw new RuntimeException("Не удалось создать директорию", e);
        }

        boolean isMovedSuccessfully = false;

        try (FileOutputStream fos = new FileOutputStream(tempPath.toFile());
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                Document document = PdfUIHelper.createDocument()) {

            PdfWriter writer = PdfWriter.getInstance(document, bos);

            document.open();
            log.debug("PDF документ открыт");
            fillDocument(document, passport, writer);
        } catch (DocumentException e) {
            log.error("Ошибка структуры PDF (OpenPDF) для {}: {}", passport.generalInfo().fieldName(), e.getMessage());
            throw new RuntimeException("Ошибка форматирования PDF", e);
        } catch (IOException e) {
            log.error("Ошибка ввода-вывода для {}: {}", filePath.getFileName(), e.getMessage());
            throw new RuntimeException("Ошибка файловой системы", e);
        }
        try {
            safeAtomicMove(tempPath, filePath);
            isMovedSuccessfully = true;
            log.info("==> Документ успешно зафиксирован на диске: {}", filePath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Критическая ошибка атомарной подмены файла для {}", filePath.getFileName(), e);
            throw new RuntimeException("Не удалось обновить финальный PDF файл", e);
        } finally {
            // 3. Защитник на своем месте
            if (!isMovedSuccessfully) {
                log.warn("Генерация сорвалась или прервана. Подчищаем временный файл: {}", tempPath.getFileName());
                cleanTempFile(tempPath);
            }
        }
        log.info("==> Генерация завершена. Путь: {}", filePath.toAbsolutePath());
    }

    private void safeAtomicMove(Path source, Path target) throws IOException {
        int maxAttempts = 5;
        long sleepMs = 50;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (FileSystemException e) {
                if (attempt == maxAttempts) {
                    throw new IOException("Не удалось выполнить атомарную подмену файла после " + maxAttempts + " попыток", e);
                }
                try {
                    Thread.sleep(sleepMs * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Перенос файла прерван", ie);
                }
            }
        }
    }

    private void cleanTempFile(Path tempFile) {
        int maxAttempts = MAX_DELETE_ATTEMPTS;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                if (Files.deleteIfExists(tempFile)) {
                    return; // удалили
                }
                return; // файла нет – выходим сразу
            } catch (IOException e) {
                if (i == maxAttempts - 1) {
                    log.warn("Не удалось удалить временный файл {}: {}", tempFile, e.getMessage());
                    return;
                }
                try {
                    Thread.sleep(DELETE_RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void checkStorageSafety(Path outputDir) {
        try {
            Files.createDirectories(outputDir);

            if (!Files.isWritable(outputDir)) {
                throw new IOException("Отсутствуют права на запись");
            }

            FileStore store = Files.getFileStore(outputDir);
            if (store.getUsableSpace() < minRequiredSpaceBytes) {
                throw new IOException("Недостаточно свободного места на диске");
            }
        } catch (IOException e) {
            log.error("Директория: {} не готова к работе: {}", outputDir.toAbsolutePath(), e.getMessage());
            throw new RuntimeException("Подготовка хранилища не удалась", e);
        }
    }

    private void fillDocument(Document document, FieldPassport passport, PdfWriter writer) {
        document.add(PdfUIHelper.createSectionTitle("Раздел 1. Общая информация."));

        document.add(PdfUIHelper.createParagraph("1.1. Подразделение: " + passport.generalInfo().department()));
        document.add(PdfUIHelper.createParagraph("1.2. Наименование: " + passport.generalInfo().fieldName()));
        document.add(PdfUIHelper.createParagraph("1.3. Текущая площадь: " + PdfUIHelper.formatArea(passport.generalInfo().fieldArea()) + " Га"));

        document.add(PdfUIHelper.createParagraph("1.4. Севооборот в " + passport.generalInfo().year() + " году:"));

        document.add(PdfUIHelper.createBulletPoint(passport.generalInfo().rotation().crop()));
        document.add(PdfUIHelper.createBulletPoint(passport.generalInfo().rotation().variety()));
        document.add(PdfUIHelper.createBulletPoint(passport.generalInfo().rotation().reproduction()));

        document.setPageSize(PageSize.A4.rotate());
        document.newPage();
        document.add(PdfUIHelper.createSectionTitle("Раздел 2. Выполненные работы"));
        List<OperationTableRow> operationTableRows = passport.operations();
        document.add(PdfUIHelper.createOperationsTable(operationTableRows));

        PdfPTable tmcContainer = PdfUIHelper.createTmcContainer(operationTableRows);
        document.add(tmcContainer);

        document.newPage();
        document.add(PdfUIHelper.createSectionTitle("Раздел 3. Заметки"));
        document.add(PdfUIHelper.createParagraph("3.1. Сводная информация"));
        List<NoteTableRow> noteTableRows = passport.notesSection().notes();
        PdfPTable notesTable = PdfUIHelper.createNotesTable(noteTableRows);
        document.add(notesTable);

        List<NoteImage> noteImages = passport.notesSection().images();
        Map<String, byte[]> photoMap = new LinkedHashMap<>();
        noteImages.stream()
            .sorted(NoteImageComparators.byComplexIndex())
            .forEach(img -> {
                byte[] bytes = img.getImageBytes();
                if (bytes != null) {
                    photoMap.put(img.getComplexIndex(), bytes);
                    log.debug("Добавлено фото: индекс={}, размер={} байт", img.getComplexIndex(), bytes.length);
                } else {
                    log.warn("Пустые данные для фото: индекс={}", img.getComplexIndex());
                }
            });

        document.newPage();
        document.add(PdfUIHelper.createPhotoGrid(writer, photoMap));

        // --- Section 4. Spectral indices ---
        document.newPage();
        ChartImage chartImage = passport.indexChart();
        PdfUIHelper.addChartImage(document, chartImage);

        // --- Section 6. Satellite Images ---
        document.newPage();
        document.add(PdfUIHelper.createSectionTitle("Раздел 6. Спутниковые снимки NDVI"));

        List<SatelliteImage> satelliteImages = passport.satelliteImages();

        if (satelliteImages == null || satelliteImages.isEmpty()) {
            document.add(PdfUIHelper.createParagraph("Данные спутникового мониторинга не были загружены."));
        } else {
            // Добавляем в столбик
            PdfUIHelper.addSatelliteImagesColumn(document, satelliteImages);
        }

        // --- Section 7 ---
        document.newPage();
        document.add(PdfUIHelper.createSectionTitle("Раздел 7. Работающие механизаторы, техника, агрегаты."));
        List<TechJournalTableRow> techJournalTableRows = passport.resources();
        PdfPTable techJournalTable = PdfUIHelper.createTechJournalTable(techJournalTableRows);
        document.add(techJournalTable);


        log.info("Данные поля успешно добавлены в документ");
    }

    @Override
    public void generateAll(List<FieldPassport> passports) {
        if (passports == null || passports.isEmpty()) {
            log.warn("Список полей пуст, генерировать нечего.");
            return;
        }

        long startTime = System.currentTimeMillis();
        int total = passports.size();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger processedCount = new AtomicInteger(0);

        log.info("==> Начало массовой генерации (всего полей: {})", passports.size());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> futures = passports.stream().map(p -> executor.submit(() -> {
                try {
                    generate(p);
                    successCount.incrementAndGet();
                    return true;
                } catch (Exception e) {
                    log.error("Generation failed: ", e);
                    return false;
                } finally {
                    int current = processedCount.incrementAndGet();
                    if (current % 50 == 0 || current == total) {
                        log.info("Прогресс: {}/{} документов готово.", current, total);
                    }
                }
            }))
            .toList();

            for (Future<Boolean> f : futures) {
                try {
                    f.get();
                } catch (InterruptedException | ExecutionException e) {
                    log.error("Поток завершился с критической ошибкой: {}", e.getCause().getMessage());
                }
            }
            long duration = System.currentTimeMillis() - startTime;
            log.info("==> Массовая генерация завершена! Время: {} мс. Успех {}/{}",
                duration, successCount.get(), total);
        }
    }

    @Override
    public Path resolvePassportPath(FieldPassport passport) {
        String fieldName = passport.generalInfo().fieldName();
        String year = String.valueOf(passport.generalInfo().year());
        String saveFileName = fieldName.replaceAll("[^a-zA-Zа-яА-Я0-9]", "_") + "_" + year + ".pdf";
        return this.outputDir.resolve(saveFileName);
    }
}
