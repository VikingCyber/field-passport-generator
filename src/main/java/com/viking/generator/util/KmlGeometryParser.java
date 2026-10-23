package com.viking.generator.util;

import com.viking.generator.model.geometry.FieldGeometry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Разбор KML-файла геозон АгроСигнала в {@link FieldGeometry}: контуры полигонов
 * переводятся из градусов WGS-84 в проекцию Web Mercator (EPSG:900913) — только для
 * рендера карты, а нетто-площадь вычисляется по сферической формуле.
 */
public class KmlGeometryParser {

    private static final Logger log = LoggerFactory.getLogger(KmlGeometryParser.class);
    /** Радиус Земли приёмов Web Mercator, м. */
    static final double R = 6378137.0;
    /** Полуширина мира Web Mercator, м (соответствует 180° долготы). */
    private static final double WORLD_HALF_WIDTH = 20037508.342789244;

    /**
     * Создаёт настроенный {@link DocumentBuilder}. {@code DocumentBuilder} не
     * потокобезопасен, а разбор KML идёт параллельно в virtual-потоках генерации,
     * поэтому билдер создаётся заново на каждый вызов {@link #parse(InputStream)}.
     */
    private static DocumentBuilder newDocumentBuilder() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Не удалось создать парсер XML", e);
        }
    }

    /** Читает файл и разбирает его в геометрию поля. */
    public Optional<FieldGeometry> parse(Path kmlFile) {
        if (kmlFile == null || !Files.exists(kmlFile)) {
            log.warn("KML-файл не найден: {}", kmlFile);
            return Optional.empty();
        }
        try (InputStream is = Files.newInputStream(kmlFile)) {
            return parse(is);
        } catch (IOException e) {
            log.warn("Не удалось прочитать KML-файл {}: {}", kmlFile, e.getMessage());
            return Optional.empty();
        }
    }

    /** Разбирает KML из строки (используется в тестах). */
    public Optional<FieldGeometry> parse(String kmlContent) {
        if (kmlContent == null || kmlContent.isBlank()) {
            return Optional.empty();
        }
        try (InputStream is = new ByteArrayInputStream(kmlContent.getBytes(StandardCharsets.UTF_8))) {
            return parse(is);
        } catch (IOException e) {
            log.warn("Не удалось прочитать KML-контент: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<FieldGeometry> parse(InputStream is) {
        try {
            Document doc = newDocumentBuilder().parse(is);
            Element root = doc.getDocumentElement();

            // Placemark может лежать на любом уровне (Document/Folder) — ищем рекурсивно.
            List<Element> placemarks = elementsByLocalName(root, "Placemark");

            for (Element placemark : placemarks) {
                List<Element> polygons = elementsByLocalName(placemark, "Polygon");
                if (polygons.isEmpty()) {
                    continue;
                }
                String name = childTextByLocalName(placemark, "name");

                List<double[][]> rings = new ArrayList<>();
                double outerAreaHa = 0.0;
                double holesAreaHa = 0.0;
                boolean foundGeometry = false;

                for (Element polygon : polygons) {
                    List<Element> outerBounds = elementsByLocalName(polygon, "outerBoundaryIs");
                    List<Element> outerRings = boundsToRings(outerBounds);
                    double polygonOuter = 0.0;
                    for (Element ringEl : outerRings) {
                        Optional<double[][]> ring = coordinatesRing(ringEl);
                        if (ring.isEmpty()) {
                            continue;
                        }
                        rings.add(toMercator(ring.get()));
                        polygonOuter += Math.abs(sphericalAreaHa(ring.get()));
                        foundGeometry = true;
                    }

                    List<Element> innerBounds = elementsByLocalName(polygon, "innerBoundaryIs");
                    double polygonHoles = 0.0;
                    for (Element ringEl : boundsToRings(innerBounds)) {
                        Optional<double[][]> ring = coordinatesRing(ringEl);
                        if (ring.isEmpty()) {
                            continue;
                        }
                        rings.add(toMercator(ring.get()));
                        polygonHoles += Math.abs(sphericalAreaHa(ring.get()));
                        foundGeometry = true;
                    }
                    outerAreaHa += polygonOuter;
                    holesAreaHa += polygonHoles;
                }

                if (!foundGeometry) {
                    continue;
                }
                double netAreaHa = Math.max(0.0, outerAreaHa - holesAreaHa);
                FieldGeometry geometry = new FieldGeometry(
                        name != null ? name : String.valueOf(placemark.hashCode()), rings, netAreaHa);
                log.debug("KML разобран: поле '{}', колец {}, нетто {} га",
                        geometry.name(), rings.size(), String.format("%.1f", netAreaHa));
                return Optional.of(geometry);
            }
            log.warn("В KML не найдено ни одного Placemark с полигоном");
            return Optional.empty();
        } catch (SAXException e) {
            log.warn("KML не является корректным XML: {}", e.getMessage());
            return Optional.empty();
        } catch (IOException e) {
            log.warn("Ошибка чтения KML: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Возвращает LinearRing для каждого из границ-элементов. */
    private List<Element> boundsToRings(List<Element> boundaries) {
        List<Element> rings = new ArrayList<>();
        for (Element boundary : boundaries) {
            rings.addAll(elementsByLocalName(boundary, "LinearRing"));
        }
        return rings;
    }

    /** Достаёт числа «lon,lat,0.0 lon,lat,0.0 …» из {@code <coordinates>}. */
    private Optional<double[][]> coordinatesRing(Element ring) {
        NodeList coordinatesNodes = ring.getElementsByTagNameNS("*", "coordinates");
        if (coordinatesNodes.getLength() == 0) {
            return Optional.empty();
        }
        String text = coordinatesNodes.item(0).getTextContent();
        List<double[]> points = new ArrayList<>();
        for (String token : text.trim().split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            String[] parts = token.split(",");
            if (parts.length < 2) {
                continue;
            }
            try {
                double lon = Double.parseDouble(parts[0].trim());
                double lat = Double.parseDouble(parts[1].trim());
                points.add(new double[] {lon, lat});
            } catch (NumberFormatException e) {
                log.warn("Некорректная координата в KML: '{}'", token);
            }
        }
        if (points.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(points.toArray(new double[0][]));
    }

    /** Переводит кольцо из WGS-84 (lon/lat) в проекцию Web Mercator (x/y). */
    private double[][] toMercator(double[][] lonLatRing) {
        double[][] ring = new double[lonLatRing.length][2];
        for (int i = 0; i < lonLatRing.length; i++) {
            ring[i] = lonLatToMercator(lonLatRing[i][0], lonLatRing[i][1]);
        }
        return ring;
    }

    /** Проекция точки из WGS-84 в EPSG:900913. */
    public static double[] lonLatToMercator(double lon, double lat) {
        double x = lon / 180.0 * WORLD_HALF_WIDTH;
        double latRad = Math.toRadians(lat);
        double y = R * Math.log(Math.tan(Math.PI / 4.0 + latRad / 2.0));
        return new double[] {x, y};
    }

    /**
     * Нетто-площадь кольца по сферической формуле для небольших полигонов:
     * интегрирование по широте с трапецией по долготе. Точность для полей
     * (десятки гектаров) достаточна для контроля и тестов.
     */
    static double sphericalAreaHa(double[][] lonLatRing) {
        int n = lonLatRing.length;
        if (n < 3) {
            return 0.0;
        }
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            double lon1 = Math.toRadians(lonLatRing[i][0]);
            double lat1 = Math.toRadians(lonLatRing[i][1]);
            double lon2 = Math.toRadians(lonLatRing[(i + 1) % n][0]);
            double lat2 = Math.toRadians(lonLatRing[(i + 1) % n][1]);
            sum += (lon2 - lon1) * (Math.sin(lat1) + Math.sin(lat2)) / 2.0;
        }
        double areaM2 = Math.abs(sum) * R * R;
        return areaM2 / 10_000.0; // в гектары
    }

    // ===== DOM helpers =====

    private List<Element> elementsByLocalName(Element root, String localName) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = root.getElementsByTagNameNS("*", localName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private List<Element> childElementsByLocalName(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && localName.equals(node.getLocalName())) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private String childTextByLocalName(Element parent, String localName) {
        for (Element child : childElementsByLocalName(parent, localName)) {
            String text = child.getTextContent();
            if (text != null) {
                return text.trim();
            }
        }
        return null;
    }
}
