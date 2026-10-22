package com.viking.generator.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.viking.generator.util.pdf.PdfTypography;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.Image;
import org.openpdf.text.Rectangle;

/**
 * Геометрия верхнего колонтитула. Раскладка повторяет образец «26ХХХХХ Письмо(БланкЧистыйЛист)»:
 * шапка растянута на всю ширину листа и прижата к левому краю страницы ({@code positionH
 * relativeFrom="page" align="left"}). На повёрнутых (альбомных) страницах контент сдвинут вниз
 * увеличенным полем {@code LANDSCAPE_MARGIN_TOP}, поэтому шапка занимает всю ширину листа
 * (цветочки — в правом углу), а зазор HEADER_GAP перед контентом сохраняется, чтобы шапка
 * не закрашивала верхнюю границу таблиц, продолжающихся с предыдущей страницы.
 */
class PassportPageEventHandlerTest {

    private static final float EPS = 0.01f;
    /** Портретная A4 и альбомная (повёрнутая) A4 — реальные размеры страниц генерации. */
    private static final float PORTRAIT_W = 595f;
    private static final float PORTRAIT_H = 842f;
    private static final float LANDSCAPE_W = 842f;
    private static final float LANDSCAPE_H = 595f;
    /** Пропорции шапки из образца (1240×118, соотношение ≈10.5:1). */
    private static final float IMG_W = 1240f;
    private static final float IMG_H = 118f;

    @Test
    @DisplayName("Шапка на портретной странице — на всю ширину листа, прижата к левому краю, как в образце")
    void portraitHeaderSpansFullPageWidthFromLeftEdge() {
        Rectangle portrait = PassportPageEventHandler.headerPlacement(
                PORTRAIT_W, PORTRAIT_H, IMG_W, IMG_H);
        assertNotNull(portrait);
        // Образец: positionH relativeFrom="page" align="left" — прижат к левому краю страницы.
        assertEquals(0f, portrait.getLeft(), EPS, "Шапка должна быть прижата к левому краю страницы: " + portrait);
        // Ширина — вся страница (595 pt), как extent 592.7 из образца.
        assertEquals(PORTRAIT_W, portrait.getWidth(), 1f, "Шапка должна занимать всю ширину листа: " + portrait);
        // Высота не может превышать верхнее поле — иначе перекроет контент.
        assertTrue(portrait.getHeight() <= PdfTypography.MARGIN_TOP + EPS,
                "Высота шапки превышает верхнее поле: " + portrait);
    }

    @Test
    @DisplayName("На альбомной странице шапка на всю ширину листа (цветы в правом углу), зазор HEADER_GAP сохранён")
    void landscapeHeaderSpansFullPageWidthAndKeepsGap() {
        // Контент на альбомной странице сдвинут вниз полем LANDSCAPE_MARGIN_TOP.
        float landscapeContentTop = LANDSCAPE_H - PdfTypography.LANDSCAPE_MARGIN_TOP;
        Rectangle landscape = PassportPageEventHandler.headerPlacement(
                LANDSCAPE_W, LANDSCAPE_H, IMG_W, IMG_H);
        assertNotNull(landscape);
        // Шапка на всю ширину альбомного листа (842 pt) — цветочки уходят в правый угол.
        assertEquals(LANDSCAPE_W, landscape.getWidth(), 1f,
                "Альбом: шапка должна занимать всю ширину листа: " + landscape);
        assertEquals(0f, landscape.getLeft(), EPS,
                "Альбом: шапка должна быть прижата к левому краю страницы: " + landscape);
        // Регрессия: нижний край шапки должен быть не ниже contentTop + HEADER_GAP,
        // иначе на альбомной странице закрашивается верхняя граница продолжающейся таблицы.
        assertTrue(landscape.getBottom() >= landscapeContentTop + PassportPageEventHandler.HEADER_GAP - EPS,
                "Альбом: шапка не оставляет зазор HEADER_GAP перед контентом: " + landscape);
        assertTrue(landscape.getHeight() <= PdfTypography.LANDSCAPE_MARGIN_TOP
                - PassportPageEventHandler.HEADER_GAP + EPS,
                "Альбом: высота шапки превышает поле с зазором: " + landscape);
    }

    @Test
    @DisplayName("Шапка не выходит за пределы страницы ни на одной ориентации")
    void headerStaysInsidePageOnBothOrientations() {
        for (Rectangle rect : new Rectangle[]{
                PassportPageEventHandler.headerPlacement(PORTRAIT_W, PORTRAIT_H, IMG_W, IMG_H),
                PassportPageEventHandler.headerPlacement(LANDSCAPE_W, LANDSCAPE_H, IMG_W, IMG_H)}) {
            assertNotNull(rect);
            assertTrue(rect.getLeft() >= -EPS, "Шапка вылезает за левый край страницы: " + rect);
            assertTrue(rect.getLeft() + rect.getWidth() <= Math.max(PORTRAIT_W, LANDSCAPE_W) + EPS,
                    "Шапка шире страницы: " + rect);
            // Верх шапки — ровно у верхнего края страницы (как в образце: extent впритык к полю).
            float top = rect.getBottom() + rect.getHeight();
            assertTrue(Math.abs(top - PORTRAIT_H) < EPS || Math.abs(top - LANDSCAPE_H) < EPS,
                    "Верх шапки не совпадает с верхом страницы: " + rect);
        }
    }

    @Test
    @DisplayName("Пропорции шапки сохраняются")
    void headerKeepsAspectRatio() {
        for (Rectangle rect : new Rectangle[]{
                PassportPageEventHandler.headerPlacement(PORTRAIT_W, PORTRAIT_H, IMG_W, IMG_H),
                PassportPageEventHandler.headerPlacement(LANDSCAPE_W, LANDSCAPE_H, IMG_W, IMG_H)}) {
            assertNotNull(rect);
            float expectedAspect = IMG_W / IMG_H;
            assertEquals(expectedAspect, rect.getWidth() / rect.getHeight(), 0.05f);
        }
    }

    @Test
    @DisplayName("Обе версии шапки загружаются и имеют одинаковые пропорции (геометрия размещения не меняется)")
    void bothHeaderVersionsLoadWithSameAspect() {
        Image portrait = PassportPageEventHandler.loadImage(PassportPageEventHandler.HEADER_RESOURCE);
        Image landscape = PassportPageEventHandler.loadImage(PassportPageEventHandler.HEADER_LANDSCAPE_RESOURCE);
        String msg = "Версия шапки не загрузилась из classpath";
        assertNotNull(portrait, msg + ": " + PassportPageEventHandler.HEADER_RESOURCE);
        assertNotNull(landscape, msg + ": " + PassportPageEventHandler.HEADER_LANDSCAPE_RESOURCE);
        // Обе версии рисуются в один и тот же прямоугольник — их пропорции должны совпадать,
        // иначе headerPlacement (вычисляемый по размерам картинки) даст разную раскладку.
        float aspectPortrait = portrait.getWidth() / portrait.getHeight();
        float aspectLandscape = landscape.getWidth() / landscape.getHeight();
        assertEquals(aspectPortrait, aspectLandscape, 0.005f,
                "Пропорции портретной и альбомной версий шапки должны совпадать");
    }

    @Test
    @DisplayName("На альбомной странице рисуется альбомная версия шапки, на портретной — портретная")
    void headerVersionMatchesPageOrientation() {
        PassportPageEventHandler handler = new PassportPageEventHandler();
        Image portrait = handler.headerImageFor(false);
        Image landscape = handler.headerImageFor(true);
        assertNotNull(portrait);
        assertNotNull(landscape);
        // Версии разные: альбомная сделана с уменьшенной надписью.
        assertNotSame(portrait, landscape, "Портретная и альбомная версии шапки должны быть разными изображениями");
    }

    @Test
    @DisplayName("Некорректные пропорции картинки отбрасываются")
    void invalidImageDimensionsReturnNull() {
        assertNull(PassportPageEventHandler.headerPlacement(PORTRAIT_W, PORTRAIT_H, 0f, IMG_H));
        assertNull(PassportPageEventHandler.headerPlacement(PORTRAIT_W, PORTRAIT_H, IMG_W, 0f));
    }
}