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
