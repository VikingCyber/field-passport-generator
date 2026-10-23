package com.viking.generator.model.geometry;

import java.util.List;

/**
 * Геометрия поля из KML-файла геозон АгроСигнала.
 *
 * @param name   наименование поля (Placemark &lt;name&gt;)
 * @param rings  контуры в проекции Web Mercator (EPSG:900913): внешняя граница и дыры,
 *               каждая точка — {@code [x, y]}, закрытые (первая точка повторена в конце)
 * @param netAreaHa нетто-площадь поля в гектарах (внешний контур минус дыры),
 *                  вычисленная по сферической формуле по широте/долготе
 */
public record FieldGeometry(String name, List<double[][]> rings, double netAreaHa) {

    public FieldGeometry {
        rings = List.copyOf(rings);
    }
}
