package com.viking.generator.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.Rectangle;

/**
 * Геометрия верхнего колонтитула: шапка не должна заходить в область контента,
 * иначе на альбомных страницах она закрашивает верхнюю границу таблицы,
 * продолжающейся с предыдущей страницы.
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
    @DisplayName("Шапка не выходит в область контента ни на портретной, ни на альбомной странице")
    void headerStaysAboveContentOnBothOrientations() {
        float contentTop = PORTRAIT_H - PdfUIHelper.MARGIN_TOP;
        Rectangle portrait = PassportPageEventHandler.headerPlacement(
                PORTRAIT_W, PORTRAIT_H, IMG_W, IMG_H);
        assertNotNull(portrait);
        // Регрессия: нижний край шапки должен быть не ниже contentTop + HEADER_GAP,
        // иначе на альбомной странице закрашивается верхняя граница продолжающейся таблицы.
        assertTrue(portrait.getBottom() >= contentTop + PassportPageEventHandler.HEADER_GAP - EPS,
                "Портрет: шапка не оставляет зазор HEADER_GAP перед контентом: " + portrait);

        float landscapeContentTop = LANDSCAPE_H - PdfUIHelper.MARGIN_TOP;
        Rectangle landscape = PassportPageEventHandler.headerPlacement(
                LANDSCAPE_W, LANDSCAPE_H, IMG_W, IMG_H);
        assertNotNull(landscape);
        assertTrue(landscape.getBottom() >= landscapeContentTop + PassportPageEventHandler.HEADER_GAP - EPS,
                "Альбом: шапка не оставляет зазор HEADER_GAP перед контентом: " + landscape);
    }

    @Test
    @DisplayName("Шапка вписывается в высоту верхнего поля и в ширину текстовой области")
    void headerFitsMargins() {
        for (Rectangle rect : new Rectangle[]{
                PassportPageEventHandler.headerPlacement(PORTRAIT_W, PORTRAIT_H, IMG_W, IMG_H),
                PassportPageEventHandler.headerPlacement(LANDSCAPE_W, LANDSCAPE_H, IMG_W, IMG_H)}) {
            assertNotNull(rect);
            assertTrue(rect.getHeight() <= PdfUIHelper.MARGIN_TOP - PassportPageEventHandler.HEADER_GAP + EPS,
                    "Высота шапки превышает верхнее поле с зазором: " + rect);
            assertTrue(rect.getLeft() >= PdfUIHelper.MARGIN_LEFT - EPS,
                    "Шапка вылезает за левое поле: " + rect);
        }
        // Правое поле — с учётом ширины конкретной страницы (портрет 595, альбом 842).
        Rectangle landscape = PassportPageEventHandler.headerPlacement(LANDSCAPE_W, LANDSCAPE_H, IMG_W, IMG_H);
        assertNotNull(landscape);
        assertTrue(landscape.getLeft() + landscape.getWidth()
                        <= LANDSCAPE_W - PdfUIHelper.MARGIN_RIGHT + EPS,
                "Шапка вылезает за правое поле на альбомной странице: " + landscape);
    }

    @Test
    @DisplayName("Пропорции шапки сохраняются")
    void headerKeepsAspectRatio() {
        Rectangle portrait = PassportPageEventHandler.headerPlacement(PORTRAIT_W, PORTRAIT_H, IMG_W, IMG_H);
        assertNotNull(portrait);
        float expectedAspect = IMG_W / IMG_H;
        assertEquals(expectedAspect, portrait.getWidth() / portrait.getHeight(), 0.05f);
    }

    @Test
    @DisplayName("Некорректные пропорции картинки отбрасываются")
    void invalidImageDimensionsReturnNull() {
        assertNull(PassportPageEventHandler.headerPlacement(PORTRAIT_W, PORTRAIT_H, 0f, IMG_H));
        assertNull(PassportPageEventHandler.headerPlacement(PORTRAIT_W, PORTRAIT_H, IMG_W, 0f));
    }
}