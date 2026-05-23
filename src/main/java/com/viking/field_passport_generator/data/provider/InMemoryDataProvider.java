package com.viking.field_passport_generator.data.provider;

import com.viking.field_passport_generator.model.FieldPassport;
import com.viking.field_passport_generator.web.dto.PassportSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryDataProvider implements DataProvider, WebDataProvider {
    private static final Logger log = LoggerFactory.getLogger(InMemoryDataProvider.class);
    private final String outputDir;
    private final String extension;

    private volatile List<FieldPassport> allPassports = new ArrayList<>();
    private final Set<PassportKey> existingPassportsCache = ConcurrentHashMap.newKeySet();
    public record PassportKey(String fieldId, int year) {}

    public InMemoryDataProvider(String outputDir, String extension) {
        this.outputDir = outputDir;
        this.extension = extension.startsWith(".") ? extension : "." + extension;
    }

    private void refreshPassportCacheFromDisk() {
        existingPassportsCache.clear();
        for (FieldPassport field : allPassports) {
            String fieldName = field.generalInfo().fieldName();
            int year = field.generalInfo().year();
            String saveFileName = fieldName.replaceAll("[^a-zA-Zа-яА-Я0-9]", "_") +
                    "_" + year + extension;
            Path pdfPath = Path.of(outputDir, saveFileName);
            if (Files.exists(pdfPath)) {
                existingPassportsCache.add(new PassportKey(field.fieldId(), field.generalInfo().year()));
            }
        }
        log.info("Passport RAM cache synchronized. Found {} verified files on disk.", existingPassportsCache.size());
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
                    boolean exists = existingPassportsCache.contains(new PassportKey(fieldId, year));

                    return new PassportSummary(
                            field.fieldId(),
                            field.generalInfo().fieldName(),
                            field.generalInfo().rotation().crop(),
                            field.generalInfo().year(),
                            field.generalInfo().fieldArea(),
                            exists
                    );
                })
                .toList();
    }

    public void registerNewPassport(String fieldId, int year) {
        if (existingPassportsCache.add(new PassportKey(fieldId, year))) {
            log.debug("Passport cache updated reactively: added [ID: {}, Year: {}]", fieldId, year);
        }
    }

    public void removePassport(String fieldId, int year) {
        if (existingPassportsCache.remove(new PassportKey(fieldId, year))) {
            log.debug("Passport cache updated reactively: removed [ID: {}, Year: {}]", fieldId, year);
        }
    }
}
