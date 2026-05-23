package com.viking.field_passport_generator.web;

import com.viking.field_passport_generator.config.AppContainer;
import com.viking.field_passport_generator.service.orchestration.PassportOrchestrator;
import com.viking.field_passport_generator.web.controller.FieldController;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

import static io.javalin.apibuilder.ApiBuilder.path;

public class AppServer {
    private final FieldController fieldController;

    public AppServer(PassportOrchestrator orchestrator) {
        this.fieldController = new FieldController(orchestrator);
    }

    public void start(int port) {
        Javalin.create(config -> {
            config.router.ignoreTrailingSlashes = true;
            config.router.caseInsensitiveRoutes = true;

            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
            });

            config.routes.apiBuilder(() -> path("/api", fieldController));

        }).start(port);
    }
}
