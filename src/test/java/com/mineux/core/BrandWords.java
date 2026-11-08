package com.mineux.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Words of another brand that no MineUX file or output may carry, for the tests that check the
 * rebrand. They come from the system property {@code mineux.brandWords} (comma-separated; a word
 * starting with {@code !} is an allowed one that contains a banned word), which the build sets
 * from {@code tools/brand_words.txt} when that file is there. Without it the list is empty and
 * nothing is found.
 */
public final class BrandWords {

    private BrandWords() {}

    private static final List<String> BANNED = new ArrayList<>();
    private static final List<String> ALLOWED = new ArrayList<>();

    static {
        String words = System.getProperty("mineux.brandWords", "");
        for (String word : words.split(",")) {
            String w = word.trim().toLowerCase(Locale.ROOT);
            if (w.isEmpty()) continue;
            if (w.startsWith("!")) ALLOWED.add(w.substring(1));
            else BANNED.add(w);
        }
    }

    /** The first banned word in {@code text} (case ignored, allowed words skipped), or null. */
    public static String foundIn(String text) {
        if (text == null || BANNED.isEmpty()) return null;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String ok : ALLOWED) lower = lower.replace(ok, " ");
        for (String word : BANNED) {
            if (lower.contains(word)) return word;
        }
        return null;
    }
}
