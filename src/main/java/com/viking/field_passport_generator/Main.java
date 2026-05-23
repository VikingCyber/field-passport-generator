package com.viking.field_passport_generator;

import com.viking.field_passport_generator.config.AppConfig;
import com.viking.field_passport_generator.config.AppContainer;
import com.viking.field_passport_generator.config.record.AppRuntimeConfig;
import com.viking.field_passport_generator.config.record.LocalFilesConfig;
import com.viking.field_passport_generator.data.provider.DataProvider;
import com.viking.field_passport_generator.model.FieldPassport;
import com.viking.field_passport_generator.service.orchestration.PassportOrchestrator;
import com.viking.field_passport_generator.web.AppServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        AppConfig appConfig = new AppConfig();
        AppContainer container = new AppContainer(appConfig);

        log.info("--- ДИАГНОСТИКА ПУТЕЙ ---");

// 1. Где лежат входящие данные (Data)
        log.info("Входные JSON (data/): {}", appConfig.getLocalFilesConfig().fieldDataPath().toAbsolutePath());

// 2. Где лежит Кэш (Archive)
        log.info("Кэш (Read-Only): {}", Path.of("cache/images").toAbsolutePath());

// 3. Где лежат рабочие папки (Write)
        log.info("Рабочая папка NOTES: {}", Path.of("notes").toAbsolutePath());
        log.info("Рабочая папка CHARTS: {}", Path.of("charts").toAbsolutePath());

        log.info("--------------------------");

        log.info("Initializing data cache...");
        LocalFilesConfig filesConfig = appConfig.getLocalFilesConfig();
        String notesPath = String.valueOf(filesConfig.notesPath());
        String filePath;
        container.getSyncService().warmUpAll("data/notesData.json", "data/fieldData.json");

        AppRuntimeConfig runtimeConfig = appConfig.getAppRuntimeConfig();
        if ("web".equalsIgnoreCase(runtimeConfig.mode())) {
            log.info("Starting in WEB mode on port {}...", runtimeConfig.serverPort());
            AppServer server = new AppServer(container.getOrchestrator());
            server.start(runtimeConfig.serverPort());
            log.info("Application ready and serving traffic.");
        } else {
            log.info("Starting in CONSOLE mode...");
            runMenu(container.getDataProvider(), container.getOrchestrator());
        }

        log.info("Application Started.");

    }

    private static void runMenu(DataProvider provider, PassportOrchestrator orchestrator) {
        while (true) {
            printMenu();
            String choice = scanner.nextLine();

            try {
                switch (choice) {
                    case "1" -> generateAll(provider, orchestrator);
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

    private static void generateAll(DataProvider provider, PassportOrchestrator orchestrator) {
        log.info("Загрузка данных для массовой генерации...");
        List<FieldPassport> all = provider.getPassportsData();
        orchestrator.processMassGeneration(all);
    }

    private static void generateOne(DataProvider provider, PassportOrchestrator orchestrator) {
        System.out.print("Введите точное название поля (например, ТК02-02): ");
        String target = scanner.nextLine().trim();

        // Фильтруем все сезоны для этого конкретного поля
        List<FieldPassport> selected = provider.getPassportsData().stream()
                .filter(p -> p.generalInfo().fieldName().equalsIgnoreCase(target))
                .toList();

        if (selected.isEmpty()) {
            System.out.println("❌ Поле '" + target + "' не найдено в базе данных.");
        } else {
            log.info("Найдено сезонов для поля {}: {}. Начинаю генерацию...", target, selected.size());
            orchestrator.processMassGeneration(selected);
            System.out.println("✅ Паспорта для поля " + target + " успешно созданы.");
        }
    }
}