package com.viking.field_passport_generator.web.controller;

import com.viking.field_passport_generator.config.AppContainer;
import com.viking.field_passport_generator.data.provider.InMemoryDataProvider;
import com.viking.field_passport_generator.model.FieldPassport;
import com.viking.field_passport_generator.service.PassportGeneratorService;
import com.viking.field_passport_generator.service.orchestration.PassportOrchestrator;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.*;

public class FieldController implements EndpointGroup {
    private static final Logger log = LoggerFactory.getLogger(FieldController.class);
    private final PassportOrchestrator orchestrator;

    public FieldController(PassportOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void addEndpoints() {
        // Nested path matches: /api/passports
        path("passports", () -> {

            // GET /api/passports
            get(ctx -> ctx.json(orchestrator.getPassportSummaries()));

            // POST /api/passports/generate
            post("generate", ctx -> {
                orchestrator.startMassGeneration();
                ctx.status(202).result("Generation started");
            });

            path("{id}/{year}/pdf", () -> get(this::streamPassportPdf));
        });
    }

    private void streamPassportPdf(Context ctx) throws Exception {
        String passportId = ctx.pathParam("id");
        int targetYear = ctx.pathParamAsClass("year", Integer.class)
                .getOrThrow(value -> new BadRequestResponse("Неверный формат года"));

        Optional<Path> pdfPath = orchestrator.getVerifiedPassportPath(passportId, targetYear);

        if (pdfPath.isEmpty()) {
            ctx.status(404).result("Паспорт поля не найден.");
            return;
        }
        ctx.contentType("application/pdf");
        ctx.result(Files.newInputStream(pdfPath.get()));
    }
}
