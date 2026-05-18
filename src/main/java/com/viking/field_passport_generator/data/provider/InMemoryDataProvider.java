package com.viking.field_passport_generator.data.provider;

import com.viking.field_passport_generator.model.FieldPassport;
import com.viking.field_passport_generator.web.dto.PassportSummary;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class InMemoryDataProvider implements DataProvider, WebDataProvider {
    private final String outputDir;
    private final String extension;

    public InMemoryDataProvider(String outputDir, String extension) {
        this.outputDir = outputDir;
        this.extension = extension;
    }

    private volatile List<FieldPassport> allPassports = new ArrayList<>();

    public synchronized void refreshFromFiles(FileDataProvider fileLoader) {
        this.allPassports = fileLoader.getPassportsData();
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
                    String fieldName = field.generalInfo().fieldName();
                    String year = String.valueOf(field.generalInfo().year());
                    String saveFileName = fieldName.replaceAll("[^a-zA-Zа-яА-Я0-9]", "_") + "_" + year + extension;
                    Path pdfPath = Path.of(outputDir, saveFileName);
                    boolean exists = Files.exists(pdfPath);

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
}
