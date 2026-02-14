package com.viking.field_passport_generator;

import java.io.FileOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FieldImageSaver {

    private static final String COOKIE = "ВСТАВЬ_СЮДА_ВСЕ_КУКИ_ИЗ_DEVTOOLS";
    private static final HttpClient client = HttpClient.newHttpClient();

    public static void main(String[] args) {
        try {
            // 1. ПС10-01 (ID 394851)
            saveFieldImage(394851, "ПС10-01");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveFieldImage(long fieldId, String fieldName) throws Exception {
        // --- ШАГ 1: GET /fullGeoZone/{id} ---
        HttpRequest coordRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://app.agrosignal.com/fullGeoZone/" + fieldId))
                .header("Cookie", COOKIE)
                .GET()
                .build();

        String geoJson = client.send(coordRequest, HttpResponse.BodyHandlers.ofString()).body();
        
        // Извлекаем координаты (простой парсинг для теста, в идеале через Jackson)
        List<String> mercatorCoords = extractAndConvertCoords(geoJson);
        String coordsArray = String.join(",", mercatorCoords);

        // --- ШАГ 2: ПОДГОТОВКА JSON ДЛЯ ПРИНТЕРА ---
        String printBody = """
        {
            "mapTitle": "%s",
            "outputFormat": "png",
            "srs": "EPSG:900913",
            "layers": [{
                "type": "Vector",
                "geoJsons": [{
                    "type": "Feature",
                    "geometry": {
                        "type": "Polygon",
                        "coordinates": [[ %s ]]
                    }
                }]
            }]
        }
        """.formatted(fieldName, coordsArray);

        // --- ШАГ 3: POST /print/pdf/create.json ---
        HttpRequest printRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://app.agrosignal.com/print/pdf/create.json"))
                .header("Content-Type", "application/json")
                .header("Cookie", COOKIE)
                .POST(HttpRequest.BodyPublishers.ofString(printBody))
                .build();

        System.out.println("Заказываем снимок для: " + fieldName);
        HttpResponse<byte[]> response = client.send(printRequest, HttpResponse.BodyHandlers.ofByteArray());

        // --- ШАГ 4: СОХРАНЕНИЕ ---
        if (response.statusCode() == 200) {
            String fileName = fieldName.replaceAll("[^a-zA-Z0-9-а-яА-Я]", "_") + ".png";
            try (FileOutputStream fos = new FileOutputStream(fileName)) {
                fos.write(response.body());
            }
            System.out.println("Успешно! Снимок сохранен в: " + Paths.get(fileName).toAbsolutePath());
        } else {
            System.out.println("Ошибка принтера: " + response.statusCode());
            System.out.println(new String(response.body()));
        }
    }

    private static List<String> extractAndConvertCoords(String json) {
        List<String> result = new ArrayList<>();
        // Регулярка для поиска пар [lon, lat]. В продакшене лучше Jackson!
        Pattern p = Pattern.compile("\\[(\\d+\\.\\d+),\\s*(\\d+\\.\\d+)\\]");
        Matcher m = p.matcher(json);

        while (m.find()) {
            double lon = Double.parseDouble(m.group(1));
            double lat = Double.parseDouble(m.group(2));

            // Конвертация в метры Меркатора
            double x = lon * 20037508.34 / 180;
            double y = Math.log(Math.tan((90 + lat) * Math.PI / 360)) / (Math.PI / 180);
            y = y * 20037508.34 / 180;

            result.add("[" + x + "," + y + "]");
        }
        return result;
    }
}