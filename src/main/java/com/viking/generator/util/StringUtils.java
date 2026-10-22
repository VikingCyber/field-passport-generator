package com.viking.generator.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

public final class StringUtils {
    private StringUtils() { throw new UnsupportedOperationException("Utility class"); }

    public static String clean(String s) {
        if (s == null || s.isBlank() || s.equalsIgnoreCase("null")) {
            return "";
        }
        return s.trim().replaceAll("\\s+", " ");
    }

    public static String normalize(String s, String fallback) {
        String cleaned = clean(s);
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    /**
     * Заменяет символы, небезопасные для имени файла (не буквы/цифры), на подчёркивание.
     * Используется единообразно во всех местах, где имя поля превращается в имя PDF-файла.
     *
     * @param rawName исходное имя (например, название поля)
     * @return имя, пригодное для имени файла
     */
    public static String sanitizeFileName(String rawName) {
        return rawName.replaceAll("[^a-zA-Zа-яА-Я0-9]", "_");
    }

    /**
     * Убирает HTML-разметку из текста: теги отбрасываются, а переносы строк и
     * блочные элементы заменяются пробелами, чтобы слова не склеивались.
     * HTML-сущности (например {@code &nbsp;}) декодируются в символы.
     *
     * @param html исходный фрагмент, возможно с HTML-тегами (или null)
     * @return чистый текст без разметки; пустая строка для null/пробелов
     */
    public static String cleanHtml(String html) {
        if (html == null || html.isBlank()) return "";
        Document doc = Jsoup.parseBodyFragment(html);
        // <br> — перенос строки: превращаем в пробел
        for (Element br : doc.select("br")) {
            br.replaceWith(new TextNode(" "));
        }
        // Блочные элементы оборачиваем символом конца блока, чтобы их содержимое
        // не склеивалось с последующим текстом («…описание…» + «Доза: …»)
        for (Element block : doc.select(
                "div,p,li,td,th,tr,ul,ol,section,article,blockquote,h1,h2,h3,h4,h5,h6")) {
            block.appendText(" ");
        }
        // &nbsp; и прочие неразрывные пробелы декодируются jsoup в \u00A0 — сводим к обычному
        String text = doc.body().text().replace('\u00A0', ' ');
        return clean(text);
    }
}
