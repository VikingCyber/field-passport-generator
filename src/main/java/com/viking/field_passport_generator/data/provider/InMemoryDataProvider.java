package com.viking.field_passport_generator.data.provider;

import com.viking.field_passport_generator.model.FieldPassport;
import com.viking.field_passport_generator.model.common.PassportKey;
import com.viking.field_passport_generator.model.common.PassportStatus;
import com.viking.field_passport_generator.web.dto.PassportSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryDataProvider implements DataProvider, WebDataProvider {
    private static final Logger log = LoggerFactory.getLogger(InMemoryDataProvider.class);
    private final String outputDir;
    private final String extension;

    private volatile List<FieldPassport> allPassports = new ArrayList<>();
    private final Map<PassportKey, Path> existingPassportsCache = new ConcurrentHashMap<>();

    public InMemoryDataProvider(String outputDir, String extension) {
        this.outputDir = outputDir;
        this.extension = extension.startsWith(".") ? extension : "." + extension;
    }

    public Optional<Path> findPassportPath(String fieldId, int year) {
        return Optional.ofNullable(existingPassportsCache.get(new PassportKey(fieldId, year)));
    }

    public synchronized void refreshFromFiles(FileDataProvider fileLoader) {
        this.allPassports = fileLoader.getPassportsData();
        refreshPassportCacheFromDisk();
    }

    @Override
    public List<FieldPassport> getPassportsData() {
        return allPassports;
    }

    @Override
    public List<PassportSummary> getPassportSummaries() {
        List<FieldPassport> passports = getPassportsData();

        return passports.stream()
                .map(field -> {
                    String fieldId = field.fieldId();
                    int year = field.generalInfo().year();
                    boolean fileExists = existingPassportsCache.containsKey(new PassportKey(fieldId, year));
                    PassportStatus baseStatus = fileExists ? PassportStatus.READY : PassportStatus.NOT_FOUND;

                    return new PassportSummary(
                            field.fieldId(),
                            field.generalInfo().fieldName(),
                            field.generalInfo().rotation().crop(),
                            year,
                            field.generalInfo().fieldArea(),
                            baseStatus.name()
                    );
                })
                .toList();
    }

    public void registerNewPassport(FieldPassport fieldPassport) {
        Path pdfPath = buildPdfPath(fieldPassport.generalInfo().fieldName(), fieldPassport.generalInfo().year());
        existingPassportsCache.put(new PassportKey(fieldPassport.fieldId(), fieldPassport.generalInfo().year()), pdfPath);
    }

    public void removePassport(String fieldId, int year) {
        if (existingPassportsCache.remove(new PassportKey(fieldId, year)) != null) {
            log.debug("Passport cache updated reactively: removed [ID: {}, Year: {}]", fieldId, year);
        }
    }

    private void refreshPassportCacheFromDisk() {
        existingPassportsCache.clear();
        for (FieldPassport field : allPassports) {
            Path pdfPath = buildPdfPath(field.generalInfo().fieldName(), field.generalInfo().year());
            if (Files.exists(pdfPath)) {
                existingPassportsCache.put(new PassportKey(field.fieldId(), field.generalInfo().year()), pdfPath);
            }
        }
        log.info("Passport RAM cache synchronized. Found {} verified files on disk.", existingPassportsCache.size());
    }

    private Path buildPdfPath(String fieldName, int year) {
        String saveFileName = fieldName.replaceAll("[^a-zA-Zа-яА-Я0-9]", "_") +
                "_" + year + extension;
        return Path.of(outputDir, saveFileName);
    }
}
