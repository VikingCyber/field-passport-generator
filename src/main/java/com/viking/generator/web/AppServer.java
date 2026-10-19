package com.viking.generator.web;

import com.viking.generator.service.orchestration.PassportOrchestrator;
import com.viking.generator.web.controller.FieldController;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;

public class AppServer {
    private static final Logger log = LoggerFactory.getLogger(AppServer.class);

    /** Пауза перед реальной остановкой, чтобы HTTP-ответ клиенту успел уйти. */
    private static final long SHUTDOWN_DELAY_MS = 500;

    private final Javalin app;

    public AppServer(PassportOrchestrator orchestrator) {
        FieldController fieldController = new FieldController(orchestrator);

        this.app = Javalin.create(config -> {
            config.router.ignoreTrailingSlashes = true;
            config.router.caseInsensitiveRoutes = true;

            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
            });

            config.routes.apiBuilder(() -> path("/api", () -> {
                fieldController.addEndpoints();

                post("shutdown", ctx -> {
                    log.info("Получен запрос на graceful shutdown через REST API.");
                    ctx.status(200).result("Server is shutting down...");
                    // Отвечаем клиенту, затем останавливаем сервер с небольшой задержкой.
                    Thread.startVirtualThread(() -> {
                        try {
                            Thread.sleep(SHUTDOWN_DELAY_MS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        stop();
                    });
                });
            }));
        });
    }

    public void start(int port) {
        log.info("Запуск HTTP-сервера на порту {}...", port);
        this.app.start(port);
        log.info("HTTP-сервер запущен.");
    }

    public void stop() {
        log.info("Останавливаю HTTP-сервер...");
        try {
            this.app.stop();
            log.info("HTTP-сервер остановлен корректно.");
        } catch (Exception e) {
            log.error("Ошибка при остановке HTTP-сервера", e);
        }
    }
}