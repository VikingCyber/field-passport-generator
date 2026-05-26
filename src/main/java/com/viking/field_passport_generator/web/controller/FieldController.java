package com.viking.field_passport_generator.web.controller;

import com.viking.field_passport_generator.model.common.PassportStatus;
import com.viking.field_passport_generator.service.orchestration.PassportOrchestrator;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.*;

public class FieldController implements EndpointGroup {
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
                boolean force = ctx.queryParamAsClass("force", Boolean.class).getOrDefault(false);
                orchestrator.startMassGeneration(force);
                ctx.status(202).result("Generation started force" + force + ")");
            });

            path("{id}/{year}", () -> {
                get("status", this::getPassportsStatus);

                get("pdf", this::streamPassportPdf);

                post("generate", ctx -> {
                    String id = ctx.pathParam("id");
                    int year = ctx.pathParamAsClass("year", Integer.class).get();
                    boolean force = ctx.queryParamAsClass("force", Boolean.class).getOrDefault(true);
                    orchestrator.startSingleGeneration(id, year, force);
                    ctx.status(202).result("Generation task accepted");
                });
            });
        });
    }

    private void getPassportsStatus(Context ctx) {
        String passportId = ctx.pathParam("id");
        int targetYear = ctx.pathParamAsClass("year", Integer.class)
                .getOrThrow(value -> new BadRequestResponse("Неферный формат года"));
        PassportStatus status = orchestrator.getPassportStatus(passportId, targetYear);
        ctx.json(Map.of("status", status.name()));
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
