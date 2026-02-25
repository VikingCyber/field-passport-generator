package com.viking.field_passport_generator.services;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.viking.field_passport_generator.models.OperationTableRow;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;

import org.openpdf.text.PageSize;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.viking.field_passport_generator.models.FieldPassport;
import com.viking.field_passport_generator.utils.PdfUIHelper;

public class PdfGeneratorService implements PassportGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);
    private static final long MIN_REQUIRED_SPACE_BYTES = 5 * 1024 * 1024; // 5 МБ
    
    @Override
    public void generate(FieldPassport passport) {
        String fieldName = passport.generalInfo().fieldName();
        String year = String.valueOf(passport.generalInfo().year());
        String saveFileName = fieldName.replaceAll("[^a-zA-Zа-яА-Я0-9]", "_") + "_" + year + ".pdf";
        Path outputDir = Path.of("output");
        Path filePath = outputDir.resolve(saveFileName);

        log.info("==> Старт генерации PDF для поля: {} (Файл: {})", fieldName, saveFileName);
        log.debug("Генерация файла: {} для года {}", saveFileName, passport.generalInfo().year());

        checkStorageSafety(outputDir);

        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            log.error("Критическая ошибка: не удалось подготовить директорию {}. Причина: {}",
                outputDir.toAbsolutePath(), e.getMessage());
            throw new RuntimeException("Не удалось создать директорию", e);
        }


        try (FileOutputStream fos = new FileOutputStream(filePath.toFile());
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                Document document = PdfUIHelper.createDocument()) {

            PdfWriter.getInstance(document, bos);

            document.open();
            log.debug("PDF документ открыт");
            fillDocument(document, passport);

        } catch (DocumentException e) {
            log.error("Ошибка структуры PDF (iText) для {}: {}", fieldName, e.getMessage());
            throw new RuntimeException("Ошибка форматирования PDF", e);
        } catch (IOException e) {
            log.error("Ошибка ввода-вывода для {}: {}", saveFileName, e.getMessage());
            throw new RuntimeException("Ошибка файловой системы", e);
        }
        
        log.info("==> Генерация завершена. Путь: {}", filePath.toAbsolutePath());
    }

    private void checkStorageSafety(Path outputDir) {
        try {
            Files.createDirectories(outputDir);

            if (!Files.isWritable(outputDir)) {
                throw new IOException("Отсутствуют права на запись");
            }
            FileStore store = Files.getFileStore(outputDir);
            if (store.getUsableSpace() < MIN_REQUIRED_SPACE_BYTES) {
                throw new IOException("Недостаточно свободного места на диске");
            }
        } catch (IOException e) {
            log.error("Директория: {} не готова к работе: {}", outputDir.toAbsolutePath(), e.getMessage());
            throw new RuntimeException("Подготовка хранилища не удалась", e);
        }
    }

    private void fillDocument(Document document, FieldPassport passport) {
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
        List<OperationTableRow> rows = passport.operations();
        document.add(PdfUIHelper.createOperationsTable(rows));

        PdfPTable tmcContainer = PdfUIHelper.createTmcContainer(rows);
        if (tmcContainer != null) {
            document.add(tmcContainer);
        }

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
}
