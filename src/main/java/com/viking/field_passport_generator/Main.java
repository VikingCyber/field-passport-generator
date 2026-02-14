package com.viking.field_passport_generator;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.viking.field_passport_generator.mappers.WorkDataMapper;
import com.viking.field_passport_generator.models.FieldPassport;
import com.viking.field_passport_generator.models.GeneralInfo;
import com.viking.field_passport_generator.models.OperationTableRow;
import com.viking.field_passport_generator.services.DataProvider;
import com.viking.field_passport_generator.services.FileDataProvider;
import com.viking.field_passport_generator.services.PassportGeneratorService;
import com.viking.field_passport_generator.services.PdfGeneratorService;
import com.viking.field_passport_generator.utils.PdfUIHelper;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    // private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        DataProvider dataProvider = new FileDataProvider();
        // PassportGeneratorService pdfGeneratorService = new PdfGeneratorService();
        String targetField = "ПС10-01"; 

    try {
        List<FieldPassport> allPassports = dataProvider.getPassportsData();

        // Ищем нужное поле в общем списке
        Optional<FieldPassport> fieldOpt = allPassports.stream()
            .filter(p -> p.generalInfo().fieldName().equalsIgnoreCase(targetField))
            .findFirst();

        if (fieldOpt.isEmpty()) {
            System.err.println("Поле '" + targetField + "' не найдено в загруженных данных!");
            return;
        }

        FieldPassport passport = fieldOpt.get();
        GeneralInfo info = passport.generalInfo();

        System.out.println("\n" + "=".repeat(60));
        System.out.println("ДЕТАЛЬНЫЙ ОТЧЕТ ПО ПОЛЮ: " + info.fieldName());
        System.out.println("Подразделение: " + info.department());
        System.out.println("Площадь:       " + info.fieldArea() + " Га");
        System.out.println("Культура:      " + info.rotation().crop() + " (" + info.year() + ")");
        System.out.println("-".repeat(60));

        List<OperationTableRow> rows = passport.operations();
        if (rows.isEmpty()) {
            System.out.println("РАБОТЫ НЕ НАЙДЕНЫ");
        } else {
            // Расширяем шапку: 9 колонок
            System.out.printf("%-22s | %-16s | %-16s | %-8s | %-8s | %-8s | %-10s | %-7s | %-7s%n", 
                "Объект", "Начало", "Конец", "Га(проб)", "Га(факт)", "Литры", "Время", "Га/ч", "км/ч");
            System.out.println("-".repeat(125)); // Увеличил длину разделителя

            for (OperationTableRow row : rows) {
                System.out.printf("%-22.22s | %-16s | %-16s | %-8.2f | %-8.2f | %-8.2f | %-10s | %-7.2f | %-7.2f%n",
                    row.operationName(),
                    row.start().format(DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")),
                    row.end().format(DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")),
                    row.measuredArea(), // По пробегу
                    row.actualArea(),   // Фактически
                    row.fuelCost(),     // Наши литры
                    PdfUIHelper.formatDuration(row.workDuration()),
                    row.productivity(),
                    row.averageSpeed()
                );
            }
            
            // Итоги
            double totalMeasured = rows.stream().mapToDouble(OperationTableRow::measuredArea).sum();
            double totalActual = rows.stream().mapToDouble(OperationTableRow::actualArea).sum();
            double totalFuel = rows.stream().mapToDouble(OperationTableRow::fuelCost).sum();
            
            System.out.println("-".repeat(125));
            System.out.printf("%-22s | %-16s | %-16s | %-8.2f | %-8.2f | %-8.2f | %-10s | %-7s | %-7s%n", 
                "ИТОГО:", "", "", totalMeasured, totalActual, totalFuel, "", "", "");
        }
        System.out.println("=".repeat(60));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
        // runMenu(dataProvider, pdfGeneratorService);



    // private static void runMenu(DataProvider dataProvider, PassportGeneratorService passportGeneratorService) {
    //     while (true) {
    //         printMenu();
    //         String choice = scanner.nextLine();

    //         switch (choice) {
    //             case "1" -> generateAll(dataProvider, passportGeneratorService);
    //             case "2" -> generateOne(dataProvider, passportGeneratorService);
    //             case "0" -> {
    //                 log.info("Выход из программы...");
    //                 return;
    //             }
    //             default -> System.out.println("Неверный ввод, попробуйте снова.");
    //         }
    //     }
    // }

    // private static void printMenu() {
    //     System.out.println("\n---  ГЕНЕРАТОР ПАСПОРТОВ ПОЛЕЙ ---");
    //     System.out.println("1. Сгенерировать ВСЕ паспорта");
    //     System.out.println("2. Сгенерировать паспорт для конкретного поля (все сезоны)");
    //     System.out.println("0. Выход");
    //     System.out.print("Выберите опцию: ");
    // }

    // private static void generateAll(DataProvider provider, PassportGeneratorService service) {
    //     List<FieldPassport> allPassports = provider.getPassportsData();
    //     service.generateAll(allPassports);
    // }

    // private static void generateOne(DataProvider provider, PassportGeneratorService service) {
    //     System.out.println("Введите название поля: ");
    //     String name = scanner.nextLine();

    //     List<FieldPassport> selectedPassports = provider.getPassportsData().stream()
    //         .filter(p -> p.generalInfo().fieldName().equalsIgnoreCase(name))
    //         .toList();

    //     if (selectedPassports.isEmpty()) {
    //         log.info("Поле не найдено!");
    //     } else {
    //         log.info("Найдено сезонов для поля " + name + ": " + selectedPassports.size());
    //     }

    //     service.generateAll(selectedPassports);
    // }
