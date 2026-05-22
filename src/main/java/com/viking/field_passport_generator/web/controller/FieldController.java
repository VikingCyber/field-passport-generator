package com.viking.field_passport_generator.web.controller;

import com.viking.field_passport_generator.config.AppContainer;
import com.viking.field_passport_generator.model.FieldPassport;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.javalin.apibuilder.ApiBuilder.*;

public class FieldController implements EndpointGroup {
    private static final Logger log = LoggerFactory.getLogger(FieldController.class);
    private final AppContainer container;

    public FieldController(AppContainer container) {
        this.container = container;
    }

    @Override
    public void addEndpoints() {
        // Nested path matches: /api/passports
        path("passports", () -> {

            // GET /api/passports
            get(ctx -> {
                var summaries = container.getWebDataProvider().getPassportSummaries();
                ctx.json(summaries);
            });

            // POST /api/passports/generate
            post("generate", ctx -> {
                var allData = container.getDataProvider().getPassportsData();
                container.getOrchestrator().processMassGeneration(allData);
                ctx.status(202).result("Generation started");
            });

            path("{id}/{year}/pdf", () -> get(this::streamPassportPdf));
        });
    }

    private void streamPassportPdf(Context ctx) throws Exception {
        String passportId = ctx.pathParam("id");
        int targetYear = Integer.parseInt(ctx.pathParam("year"));

        // Find the exact FieldPassport object in memory
        FieldPassport targetedPassport = container.getDataProvider().getPassportsData().stream()
                .filter(p -> p.fieldId().equals(passportId))
                .filter(p -> p.generalInfo().year() == targetYear)
                .findFirst()
                .orElse(null);

        if (targetedPassport == null) {
            ctx.status(404).result("Паспорт поля не найден.");
            return;
        }

        // Determine file name based on the found object properties
        Path filePath = container.getPdfService().resolvePassportPath(targetedPassport);
        File pdfFile = filePath.toFile();

        if (!pdfFile.exists()) {
            log.info("Live rendering triggered for specific target: {}", pdfFile.getName());
            container.getPdfService().generate(targetedPassport);
        }
        ctx.contentType("application/pdf");
        ctx.result(Files.newInputStream(filePath));
    }
}
