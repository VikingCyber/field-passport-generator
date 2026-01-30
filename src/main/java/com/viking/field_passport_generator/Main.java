package com.viking.field_passport_generator;

import java.util.List;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.viking.field_passport_generator.models.FieldPassport;
import com.viking.field_passport_generator.services.DataProvider;
import com.viking.field_passport_generator.services.FileDataProvider;
import com.viking.field_passport_generator.services.PassportGeneratorService;
import com.viking.field_passport_generator.services.PdfGeneratorService;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        DataProvider dataProvider = new FileDataProvider();
        PassportGeneratorService pdfGeneratorService = new PdfGeneratorService();

        runMenu(dataProvider, pdfGeneratorService);
    }

    private static void runMenu(DataProvider dataProvider, PassportGeneratorService passportGeneratorService) {
        while (true) {
            printMenu();
            String choice = scanner.nextLine();

            switch (choice) {
                case "1" -> generateAll(dataProvider, passportGeneratorService);
                case "2" -> generateOne(dataProvider, passportGeneratorService);
                case "0" -> {
                    log.info("Выход из программы...");
                    return;
                }
                default -> System.out.println("Неверный ввод, попробуйте снова.");
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n---  ГЕНЕРАТОР ПАСПОРТОВ ПОЛЕЙ ---");
        System.out.println("1. Сгенерировать ВСЕ паспорта");
        System.out.println("2. Сгенерировать паспорт для конкретного поля (все сезоны)");
        System.out.println("0. Выход");
        System.out.print("Выберите опцию: ");
    }

    private static void generateAll(DataProvider provider, PassportGeneratorService service) {
        List<FieldPassport> allPassports = provider.getPassportsData();
        service.generateAll(allPassports);
    }

    private static void generateOne(DataProvider provider, PassportGeneratorService service) {
        System.out.println("Введите название поля: ");
        String name = scanner.nextLine();

        List<FieldPassport> selectedPassports = provider.getPassportsData().stream()
            .filter(p -> p.generalInfo().fieldName().equalsIgnoreCase(name))
            .toList();

        if (selectedPassports.isEmpty()) {
            log.info("Поле не найдено!");
        } else {
            log.info("Найдено сезонов для поля " + name + ": " + selectedPassports.size());
        }

        service.generateAll(selectedPassports);
    }
}
