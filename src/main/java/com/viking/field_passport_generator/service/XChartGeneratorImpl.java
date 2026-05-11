package com.viking.field_passport_generator.service;

import com.viking.field_passport_generator.config.ChartConfig;
import com.viking.field_passport_generator.data.dto.chart.ChartPoint;
import com.viking.field_passport_generator.model.ChartImage;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.XYStyler;
import org.knowm.xchart.style.markers.SeriesMarkers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.*;
import java.util.List;

public class XChartGeneratorImpl implements ChartGenerator {
    private static final Logger log = LoggerFactory.getLogger(XChartGeneratorImpl.class);

    private static final String SERIES_NDVI_MEAN = "NDVI средний";
    private static final String SERIES_NDVI_MAX = "NDVI макс.";
    private static final String SERIES_NDVI_MIN = "NDVI мин.";
    private static final String SERIES_NDWI_MEAN = "NDWI (влага) средний";
    private static final String SERIES_MSI_MEAN = "MSI (стресс) средний";
    private static final String SERIES_GLI_MEAN = "GLI (зелёный лист) средний";

    private static final Color COLOR_NDVI_MEAN = new Color(31, 119, 180); // Темно-синий
    private static final Color COLOR_NDVI_MAX = new Color(140, 86, 75);  // Коричневый
    private static final Color COLOR_NDVI_MIN = new Color(188, 189, 34);  // Оливковый
    private static final Color COLOR_NDWI_MEAN = new Color(0, 0, 255);    // Ярко-синий
    private static final Color COLOR_MSI_MEAN = new Color(255, 0, 0);    // Красный
    private static final Color COLOR_GLI_MEAN = new Color(0, 255, 0);

    private final int width;
    private final int height;
    private final Font customFont;
    private final ZoneId timezone;

    public XChartGeneratorImpl(ChartConfig config) {
        this.width = config.width();
        this.height = config.height();
        this.customFont = loadFontFromResource(config.fontPath());
        this.timezone = config.timezone();
    }

    @Override
    public Optional<byte[]> generateCombinedChart(ChartImage chart) {
        XYChart xyChart = new XYChartBuilder()
                .width(width)
                .height(height)
                .title(chart.getTitle())
                .yAxisTitle("Спектральные индексы")
                .build();

        XYStyler styler = xyChart.getStyler();
        styler.setLocale(Locale.forLanguageTag("ru"));
        styler.setDatePattern("d MMM");
        styler.setXAxisTickMarkSpacingHint(100);
        styler.setYAxisTickMarkSpacingHint(50);
        styler.setPlotContentSize(0.98);

        styler.setChartTitleFont(customFont.deriveFont(Font.PLAIN, 22f));
        styler.setChartTitlePadding(10);
        styler.setAxisTickLabelsFont(customFont.deriveFont(Font.PLAIN, 14f));
        styler.setLegendFont(customFont.deriveFont(Font.PLAIN, 12f));
        styler.setAxisTitleFont(customFont.deriveFont(Font.PLAIN, 14f));
        styler.setAxisTitlePadding(20);

        styler.setChartBackgroundColor(Color.WHITE);
        styler.setPlotGridLinesVisible(true);
        styler.setPlotGridVerticalLinesVisible(false);
        styler.setPlotGridHorizontalLinesVisible(true);
        styler.setPlotGridLinesStroke(new BasicStroke(1.0f));
        styler.setPlotGridLinesColor(new Color(230, 230, 230));
        styler.setPlotGridLinesColor(new Color(240, 240, 240));
        styler.setLegendPosition(Styler.LegendPosition.InsideNE);
        styler.setLegendLayout(Styler.LegendLayout.Vertical);
        styler.setMarkerSize(0);

        List<Date> xData = new ArrayList<>();
        List<Double> ndviMean = new ArrayList<>();
        List<Double> ndviMin = new ArrayList<>();
        List<Double> ndviMax = new ArrayList<>();
        List<Double> ndwiMean = new ArrayList<>();
        List<Double> msiMean = new ArrayList<>();
        List<Double> gliMean = new ArrayList<>();

        for (ChartPoint p : chart.getPoints()) {
            xData.add(p.toDate(timezone));
            ndviMean.add(p.ndviMean());
            ndviMin.add(p.ndviMin());
            ndviMax.add(p.ndviMax());
            ndwiMean.add(p.ndwiMean());
            msiMean.add(p.msiMean());
            gliMean.add(p.gliMean());
        }

        if (xData.isEmpty()) {
            log.warn("Не удалось сгенерировать график для '{}': список точек пуст. Год: {}",
                    chart.getTitle(), chart.getYear());
            return Optional.empty();
        }

        double minX = (double) xData.getFirst().getTime();
        double maxX = (double) xData.getLast().getTime();

        styler.setXAxisMin(minX);
        styler.setXAxisMax(maxX);

        addSeries(xyChart, SERIES_NDVI_MEAN, xData, ndviMean, COLOR_NDVI_MEAN);
        addSeries(xyChart, SERIES_NDVI_MAX, xData, ndviMax, COLOR_NDVI_MAX);
        addSeries(xyChart, SERIES_NDVI_MIN, xData, ndviMin, COLOR_NDVI_MIN);
        addSeries(xyChart, SERIES_NDWI_MEAN, xData, ndwiMean, COLOR_NDWI_MEAN);
        addSeries(xyChart, SERIES_MSI_MEAN, xData, msiMean, COLOR_MSI_MEAN);
        addSeries(xyChart, SERIES_GLI_MEAN, xData, gliMean, COLOR_GLI_MEAN);

        try {
            byte[] bytes = BitmapEncoder.getBitmapBytes(xyChart, org.knowm.xchart.BitmapEncoder.BitmapFormat.PNG);
            return Optional.ofNullable(bytes);
        } catch (IOException e) {
            log.error("Error generating chart: {}. Reason: {}", chart.getTitle(), e.getMessage());
            return Optional.empty();
        }
    }

    private Font loadFontFromResource(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                return getDefaultFallbackFont("Resource not found: " + resourcePath);
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, is);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font;
        } catch (FontFormatException e) {
            return getDefaultFallbackFont("Invalid font format: " + resourcePath);
        } catch (IOException e) {
            return getDefaultFallbackFont("I/O error while reading font: " + resourcePath);
        }
    }

    private Font getDefaultFallbackFont(String reason) {
        log.warn(reason);
        return new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    }

    private void addSeries(XYChart chart, String name, List<Date> x, List<Double> y, Color color) {
        if (y.isEmpty() || y.stream().allMatch(Objects::isNull)) {
            log.warn("The Y axis is empty");
            return;
        }
        XYSeries series = chart.addSeries(name, x, y);
        series.setLineColor(color);
        series.setLineWidth(2.0f);
        series.setMarker(SeriesMarkers.NONE);

    }
}
