package com.viking.field_passport_generator.services;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;

import org.openpdf.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.viking.field_passport_generator.models.FieldPassport;
import com.viking.field_passport_generator.utils.PdfUIHelper;

public class PdfGeneratorService implements PassportGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);
    
    @Override
    public void generate(FieldPassport passport) {
        String fieldName = passport.generalInfo().fieldName();
        String saveFileName = fieldName.replaceAll("[^a-zA-Zа-яА-Я0-9\\-]", "_") + ".pdf";
        Path outputDir = Paths.get("output");
        Path filePath = outputDir.resolve(saveFileName);

        log.info("==> Старт генерации PDF для поля: {} (Файл: {})", fieldName, saveFileName);

        Document document = PdfUIHelper.createDocument();

        try {
            if (Files.notExists(filePath)) {
                Files.createDirectories(outputDir);
                log.debug("Создана отсутствующая директория: {}", outputDir);
            }

            PdfWriter.getInstance(document, new FileOutputStream(filePath.toFile()));

            document.open();
            log.debug("PDF документ открыт");

            document.add(PdfUIHelper.createSectionTitle("Раздел 1. Общая информация."));

            document.add(PdfUIHelper.createParagraph("1.1. Подразделение: " + passport.generalInfo().department()));
            document.add(PdfUIHelper.createParagraph("1.2. Наименование: " + passport.generalInfo().fieldName()));
            document.add(PdfUIHelper.createParagraph("1.3. Текущая площадь: " + passport.generalInfo().fieldArea() + " Га"));

            document.add(PdfUIHelper.createParagraph("1.4. Севооборот в " + passport.generalInfo().year() + " году:"));

            document.add(PdfUIHelper.createBulletPoint(passport.generalInfo().rotation().crop()));
            document.add(PdfUIHelper.createBulletPoint(passport.generalInfo().rotation().variety()));
            document.add(PdfUIHelper.createBulletPoint(passport.generalInfo().rotation().reproduction()));

            log.info("Данные поля успешно добавлены в документ");

        } catch (DocumentException | IOException e) {
            log.error("Критический сбой при создании PDF: {}", e.getMessage(), e);
        } finally {
            if (document != null && document.isOpen()) {
                document.close();
                log.debug("PDF документ закрыт и сохранен на диск");
            }
        }
        
        log.info("==> Генерация завершена. Путь: {}", filePath.toAbsolutePath());
    }
}
