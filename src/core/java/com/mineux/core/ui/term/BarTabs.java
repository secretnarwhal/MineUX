package com.mineux.core.ui.term;

import net.minecraft.client.gui.Font;

/**
 * The workstation tabs' widths when they do not all fit in the bar: like a browser's tabs, the
 * widest give way first, all shrinking to one shared width (never below the slot number alone),
 * and their names are cut to fit with "..". Tabs that are narrower than that width keep theirs.
 */
final class BarTabs {

    private static final String CUT = "..";

    private BarTabs() {}

    /**
     * Each tab's width: its natural width, or less so that together they take at most
     * {@code room}. Never below {@code min[i]}, the tab with its number only.
     */
    static int[] widths(int[] natural, int[] min, int room) {
        int[] out = natural.clone();
        if (total(natural) <= room) return out;
        int lo = 0;
        int hi = 0;
        for (int w : natural) hi = Math.max(hi, w);
        // The widest shared cap that still fits.
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (cappedTotal(natural, min, mid) <= room) lo = mid;
            else hi = mid - 1;
        }
        for (int i = 0; i < out.length; i++) out[i] = Math.max(min[i], Math.min(natural[i], lo));
        return out;
    }

    private static int total(int[] widths) {
        int sum = 0;
        for (int w : widths) sum += w;
        return sum;
    }

    private static int cappedTotal(int[] natural, int[] min, int cap) {
        int sum = 0;
        for (int i = 0; i < natural.length; i++) sum += Math.max(min[i], Math.min(natural[i], cap));
        return sum;
    }

    /** A tab's label: {@code " 2:name "}, its name cut with ".." to fit {@code width}, or {@code " 2 "} at the least. */
    static String label(Font font, String slot, String name, int width) {
        String full = " " + slot + ":" + name + " ";
        if (font.width(full) <= width) return full;
        String head = " " + slot + ":";
        for (int keep = name.length() - 1; keep > 0; keep--) {
            String cut = head + name.substring(0, keep) + CUT + " ";
            if (font.width(cut) <= width) return cut;
        }
        return " " + slot + " ";
    }

    /** The narrowest a tab may get: its number alone. */
    static String bare(String slot) {
        return " " + slot + " ";
    }
}
