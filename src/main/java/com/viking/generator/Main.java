package com.viking.generator;

import com.viking.generator.config.AppConfig;
import com.viking.generator.config.AppContainer;
import com.viking.generator.config.record.AppRuntimeConfig;
import com.viking.generator.config.record.LocalFilesConfig;
import com.viking.generator.config.record.StoragePathsConfig;
import com.viking.generator.data.provider.DataProvider;
import com.viking.generator.model.FieldPassport;
import com.viking.generator.service.orchestration.PassportOrchestrator;
import com.viking.generator.web.AppServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Stream;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        AppConfig appConfig = new AppConfig();
        AppContainer container = new AppContainer(appConfig);

        log.info("Initializing data cache...");
        LocalFilesConfig filesConfig = appConfig.getLocalFilesConfig();
        container.getSyncService().warmUpAll(filesConfig.notesPath(), filesConfig.fieldDataPath());

        AppRuntimeConfig runtimeConfig = appConfig.getAppRuntimeConfig();
        if ("web".equalsIgnoreCase(runtimeConfig.mode())) {
            log.info("Starting in WEB mode on port {}...", runtimeConfig.serverPort());
            AppServer server = new AppServer(container.getOrchestrator());
            server.start(runtimeConfig.serverPort());
            log.info("Application ready and serving traffic.");
            registerShutdownHook(server, appConfig);
        } else {
            log.info("Starting in CONSOLE mode...");
            runMenu(container.getDataProvider(), container.getOrchestrator());
        }

        log.info("Application Started.");

    }

    /**
     * Регистрирует shutdown hook: останавливает HTTP-сервер и подчищает временные
     * файлы (*.tmp) из рабочих директорий, чтобы после Ctrl+C или System.exit
     * не оставалось хвостов от прерванных записей.
     */
    private static void registerShutdownHook(AppServer server, AppConfig appConfig) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Перехвачен сигнал завершения JVM. Выполняю graceful shutdown...");
            try {
                server.stop();
            } catch (Exception e) {
                log.error("Ошибка при graceful shutdown HTTP-сервера", e);
            }
            cleanupTempFiles(appConfig);
            log.info("Graceful shutdown завершён.");
        }, "graceful-shutdown"));
    }

    private static void cleanupTempFiles(AppConfig appConfig) {
        StoragePathsConfig paths = appConfig.getStoragePathsConfig();
        LocalFilesConfig files = appConfig.getLocalFilesConfig();

        Set<Path> dirs = new LinkedHashSet<>();
        dirs.add(paths.outputDir());
        dirs.add(files.fieldDataPath().getParent());

        for (Path dir : dirs) {
            if (dir == null || !Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".tmp"))
                        .forEach(Main::deleteTempFileWithRetries);
            } catch (IOException e) {
                log.warn("Не удалось просканировать {} при очистке временных файлов: {}",
                        dir, e.getMessage());
            }
        }
    }

    /**
     * Удаляет временный файл с повторами (на случай кратковременной блокировки
     * файла на Windows). Использует только JDK-классы, т.к. в shutdown hook
     * класслоадер exec:java уже закрыт и кастомные классы загрузить нельзя.
     */
    private static void deleteTempFileWithRetries(Path file) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Files.deleteIfExists(file);
                return;
            } catch (IOException e) {
                if (attempt == 2) {
                    log.warn("Не удалось удалить временный файл {}: {}", file, e.getMessage());
                    return;
                }
                try {
                    Thread.sleep(50L * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static void runMenu(DataProvider provider, PassportOrchestrator orchestrator) {
        while (true) {
            printMenu();
            String choice = scanner.nextLine();

            try {
                switch (choice) {
                    case "1" -> {
                        System.out.print("Запустить принудительную перегенерацию (y/n)? ");
                        boolean force = scanner.nextLine().equalsIgnoreCase("y");
                        generateAll(provider, orchestrator, force);
                    }
                    case "2" -> generateOne(provider, orchestrator);
                    case "0" -> {
                        log.info("Завершение работы...");
                        return;
                    }
                    default -> System.out.println("⚠️ Неверный выбор, попробуйте снова.");
                }
            } catch (Exception e) {
                log.error("Произошла ошибка: {}", e.getMessage());
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n" + "=".repeat(30));
        System.out.println("ГЕНЕРАТОР ПАСПОРТОВ ПОЛЕЙ");
        System.out.println("=".repeat(30));
        System.out.println("1. Сгенерировать ВСЕ паспорта (все поля и сезоны)");
        System.out.println("2. Сгенерировать паспорта конкретного поля");
        System.out.println("0. Выход");
        System.out.print("\nВыберите опцию: ");
    }

    private static void generateAll(DataProvider provider, PassportOrchestrator orchestrator, boolean force) {
        log.info("Загрузка данных для массовой генерации...");
        List<FieldPassport> all = provider.getPassportsData();
        orchestrator.processMassGeneration(all, force);
    }

    private static void generateOne(DataProvider provider, PassportOrchestrator orchestrator) {
        System.out.print("Введите точное название поля (например, ТК02-02): ");
        String target = scanner.nextLine().trim();

        System.out.print("Принудительно перегенерировать (y/n)? ");
        boolean force = scanner.nextLine().equalsIgnoreCase("y");

        // Фильтруем все сезоны для этого конкретного поля
        List<FieldPassport> selected = provider.getPassportsData().stream()
                .filter(p -> p.generalInfo().fieldName().equalsIgnoreCase(target))
                .toList();

        if (selected.isEmpty()) {
            System.out.println("❌ Поле '" + target + "' не найдено в базе данных.");
        } else {
            log.info("Найдено сезонов для поля {}: {}. Начинаю генерацию...", target, selected.size());
            orchestrator.processMassGeneration(selected, force);
            System.out.println("✅ Паспорта для поля " + target + " успешно созданы.");
        }
    }
}