package com.viking.field_passport_generator;

import com.viking.field_passport_generator.config.AppConfig;
import com.viking.field_passport_generator.config.AppContainer;
import com.viking.field_passport_generator.data.provider.DataProvider;
import com.viking.field_passport_generator.model.FieldPassport;
import com.viking.field_passport_generator.service.PassportGeneratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Scanner;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        AppConfig appConfig = new AppConfig();
        AppContainer container = new AppContainer(appConfig);
        log.info("Application Started.");
        container.getSyncService().warmUp("data/notesData.json");

        runMenu(container.getDataProvider(), container.getPassportGeneratorService());
    }

    private static void runMenu(DataProvider provider, PassportGeneratorService service) {
        while (true) {
            printMenu();
            String choice = scanner.nextLine();

            try {
                switch (choice) {
                    case "1" -> generateAll(provider, service);
                    case "2" -> generateOne(provider, service);
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

    private static void generateAll(DataProvider provider, PassportGeneratorService service) {
        log.info("Загрузка данных для массовой генерации...");
        List<FieldPassport> all = provider.getPassportsData();
        service.generateAll(all);
    }

    private static void generateOne(DataProvider provider, PassportGeneratorService service) {
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
            service.generateAll(selected); // Генерируем PDF для каждого найденного сезона
            System.out.println("✅ Паспорта для поля " + target + " успешно созданы.");
        }
    }
}