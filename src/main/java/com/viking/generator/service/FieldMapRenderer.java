package com.viking.generator.service;

import com.viking.generator.model.geometry.FieldGeometry;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Рисовальщик «снимка поля с карты»: OSM-тайлы подложкой + контур поля (EPSG:900913).
 * Порт алгоритма из прототипа {@code OsmStitch}: автоуровень зума, точное центрирование
 * по bbox контура, кадр 1600×767, подпись по центру.
 */
public class FieldMapRenderer {

    private static final Logger log = LoggerFactory.getLogger(FieldMapRenderer.class);

    static final double R = 6378137.0;
    static final double RES0 = 156543.03392;
    static final int TILE = 256;
    static final double WORLD_HALF_WIDTH = 20037508.342789244;

    static final int CANVAS_WIDTH = 1600;
    static final int CANVAS_HEIGHT = 767;
    static final double PAD = 1.7;
    static final int MAX_ZOOM = 19;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) EdgePassportRender/1.0";

    private final TileFetcher tileFetcher;

    /** Загрузчик одного тайла подложки: {@code (z, x, y)} → PNG-байты или {@code null}. */
    public interface TileFetcher {
        byte[] fetch(int z, int x, int y) throws IOException;
    }

    public FieldMapRenderer(TileFetcher tileFetcher) {
        this.tileFetcher = tileFetcher;
    }

    /**
     * Рендерит снимок поля в PNG заданного кадра и возвращает его байты.
     *
     * @param geometry геометрия поля (кольца в EPSG:900913)
     * @return PNG-байты кадра 1600×767
     * @throws IOException если геометрия пуста или подготовка канваса невозможна
     */
    public byte[] renderPng(FieldGeometry geometry) throws IOException {
        if (geometry == null || geometry.rings() == null || geometry.rings().isEmpty()) {
            throw new IOException("Нет геометрии для рендера снимка поля");
        }
        String label = geometry.name() != null ? geometry.name() : "Поле";
        return renderPng(geometry.rings(), label);
    }

    byte[] renderPng(List<double[][]> rings, String label) throws IOException {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (double[][] ring : rings) {
            for (double[] c : ring) {
                minX = Math.min(minX, c[0]);
                maxX = Math.max(maxX, c[0]);
                minY = Math.min(minY, c[1]);
                maxY = Math.max(maxY, c[1]);
            }
        }
        if (minX > maxX || minY > maxY) {
            throw new IOException("Пустой bbox геометрии поля");
        }

        double cx = (minX + maxX) / 2.0;
        double cy = (minY + maxY) / 2.0;
        double fW = maxX - minX;
        double fH = maxY - minY;

        double neededRes = Math.max(fW * PAD / CANVAS_WIDTH, fH * PAD / CANVAS_HEIGHT);
        int z = 0;
        double res = RES0;
        while (res > neededRes && z < MAX_ZOOM) {
            res /= 2;
            z++;
        }

        double extW = CANVAS_WIDTH * res;
        double extH = CANVAS_HEIGHT * res;
        double wn = cx - extW / 2;
        double en = cx + extW / 2;
        double sn = cy - extH / 2;
        double nn = cy + extH / 2;

        int[] t0 = toTile(wn, nn, z);
        int[] t1 = toTile(en, sn, z);
        double nwX = tileToX(t0[0], z);
        double nwY = tileToY(t0[1], z);
        int cw = (t1[0] - t0[0] + 1) * TILE;
        int ch = (t1[1] - t0[1] + 1) * TILE;

        int vx = (int) Math.round((wn - nwX) / res);
        if (vx < 0) {
            vx = 0;
        }
        int vy = (int) Math.round((nwY - nn) / res);
        if (vy < 0) {
            vy = 0;
        }

        BufferedImage canvas = new BufferedImage(Math.max(1, cw), Math.max(1, ch),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setComposite(AlphaComposite.Src);

        BufferedImage[] tiles = loadTiles(z, t0, t1);
        int width = t1[0] - t0[0] + 1;
        int height = t1[1] - t0[1] + 1;
        for (int ty = 0; ty < height; ty++) {
            for (int tx = 0; tx < width; tx++) {
                BufferedImage tile = tiles[ty * width + tx];
                if (tile == null) {
                    g.setColor(new Color(234, 237, 240));
                    g.fillRect(tx * TILE, ty * TILE, TILE, TILE);
                } else {
                    g.drawImage(tile, tx * TILE, ty * TILE, TILE, TILE, null);
                }
            }
        }

        // Контур поля: единый Path2D с WIND_EVEN_ODD, чтобы дыры вырезались.
        Path2D.Double fieldPath = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        for (double[][] ring : rings) {
            if (ring.length == 0) {
                continue;
            }
            Path2D.Double ringPath = new Path2D.Double();
            int px = (int) Math.round((ring[0][0] - nwX) / res);
            int py = (int) Math.round((nwY - ring[0][1]) / res);
            ringPath.moveTo(px, py);
            for (int i = 1; i < ring.length; i++) {
                px = (int) Math.round((ring[i][0] - nwX) / res);
                py = (int) Math.round((nwY - ring[i][1]) / res);
                ringPath.lineTo(px, py);
            }
            ringPath.closePath();
            fieldPath.append(ringPath, false);
        }

        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(255, 224, 130, 90));
        g.fill(fieldPath);
        g.setColor(new Color(198, 40, 40));
        g.setStroke(new BasicStroke(6f));
        g.draw(fieldPath);
        g.setColor(new Color(120, 15, 15));
        g.setStroke(new BasicStroke(3f));
        g.draw(fieldPath);

        if (label != null && !label.isBlank()) {
            drawLabel(g, label, cx, cy, nwX, nwY, res);
        }
        g.dispose();

        int cropW = Math.min(CANVAS_WIDTH, cw - vx);
        int cropH = Math.min(CANVAS_HEIGHT, ch - vy);
        cropW = Math.max(1, cropW);
        cropH = Math.max(1, cropH);
        BufferedImage viewport = canvas.getSubimage(vx, vy, cropW, cropH);
        BufferedImage out = new BufferedImage(cropW, cropH, BufferedImage.TYPE_INT_RGB);
        Graphics2D outG = out.createGraphics();
        outG.drawImage(viewport, 0, 0, null);
        outG.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(out, "png", baos);
        if (log.isDebugEnabled()) {
            log.debug("Снимок поля отрендерен: {}×{}, zoom {}, колец {}", cropW, cropH, z,
                    rings.size());
        }
        return baos.toByteArray();
    }

    private void drawLabel(Graphics2D g, String label, double cx, double cy,
            double nwX, double nwY, double res) {
        g.setFont(new Font("Arial", Font.BOLD, 28));
        FontMetrics fm = g.getFontMetrics();
        int lx = (int) Math.round((cx - nwX) / res) - fm.stringWidth(label) / 2;
        int ly = (int) Math.round((nwY - cy) / res) + fm.getAscent() / 2;
        int padX = 12;
        int padY = 6;
        g.setColor(new Color(255, 255, 255, 200));
        g.fillRoundRect(lx - padX, ly - fm.getAscent() - padY,
                fm.stringWidth(label) + 2 * padX, fm.getHeight() + 2 * padY, 10, 10);
        g.setColor(new Color(60, 10, 10));
        g.drawString(label, lx, ly);
    }

    /** Загружает тайлы подложки параллельно (пул 8 потоков), пропуская недоступные. */
    private BufferedImage[] loadTiles(int z, int[] t0, int[] t1) {
        int width = t1[0] - t0[0] + 1;
        int height = t1[1] - t0[1] + 1;
        int total = width * height;
        BufferedImage[] tiles = new BufferedImage[total];
        AtomicInteger failed = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(
                Math.max(1, Math.min(8, total)));
        List<Future<?>> futures = new ArrayList<>();
        for (int ty = t0[1]; ty <= t1[1]; ty++) {
            final int row = ty;
            for (int tx = t0[0]; tx <= t1[0]; tx++) {
                final int col = tx;
                final int index = (ty - t0[1]) * width + (tx - t0[0]);
                futures.add(executor.submit(() -> {
                    try {
                        byte[] data = tileFetcher.fetch(z, col, row);
                        if (data != null) {
                            BufferedImage tile =
                                    ImageIO.read(new ByteArrayInputStream(data));
                            tiles[index] = tile;
                        }
                        if (tiles[index] == null) {
                            failed.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failed.incrementAndGet();
                    }
                }));
            }
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failed.incrementAndGet();
            } catch (ExecutionException e) {
                failed.incrementAndGet();
            }
        }
        executor.shutdownNow();

        int missed = failed.get();
        if (missed > 0) {
            log.warn("Не удалось загрузить {} из {} тайлов подложки", missed, total);
        }
        return tiles;
    }

    // ===== Web Mercator tile math (порт OsmStitch) =====
    static double toLon(double xm) {
        return xm / WORLD_HALF_WIDTH * 180.0;
    }

    static double toLat(double ym) {
        return Math.toDegrees(2 * Math.atan(Math.exp(ym / R)) - Math.PI / 2);
    }

    static int[] toTile(double xe, double ye, int z) {
        double n = Math.pow(2, z);
        double lon = toLon(xe);
        double lat = toLat(ye);
        double latR = Math.toRadians(lat);
        int xt = (int) Math.floor((lon + 180.0) / 360.0 * n);
        int yt = (int) Math.floor(
                (1.0 - Math.log(Math.tan(latR) + 1.0 / Math.cos(latR)) / Math.PI) / 2.0 * n);
        return new int[] {xt, yt};
    }

    static double tileToX(int tax, int z) {
        double n = Math.pow(2, z);
        return tax / n * (2 * WORLD_HALF_WIDTH) - WORLD_HALF_WIDTH;
    }

    static double tileToY(int tay, int z) {
        double n = Math.pow(2, z);
        double latR = Math.atan(Math.sinh(Math.PI * (1.0 - 2.0 * tay / n)));
        return R * Math.log(Math.tan(Math.PI / 4.0 + latR / 2.0));
    }

    /** Стандартный загрузчик OSM-тайлов (tile.openstreetmap.org). */
    public static class OsmTileFetcher implements TileFetcher {
        private static final String TILE_URL = "https://tile.openstreetmap.org/%d/%d/%d.png";

        private final HttpClient client;

        public OsmTileFetcher() {
            this.client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }

        @Override
        public byte[] fetch(int z, int x, int y) throws IOException {
            URI uri = URI.create(String.format(TILE_URL, z, x, y));
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            try {
                HttpResponse<byte[]> response =
                        client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200) {
                    throw new IOException("OSM тайл вернул HTTP " + response.statusCode()
                            + " для " + uri);
                }
                return response.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Прервана загрузка тайла " + uri, e);
            }
        }
    }
}
