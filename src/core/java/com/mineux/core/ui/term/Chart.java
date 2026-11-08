package com.mineux.core.ui.term;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A line graph out of {@code fill}s: a series over time, for {@link LookupPane}'s rank and ELO history. */
public final class Chart {

    private Chart() {}

    /**
     * Draws {@code values}, oldest on the left, scaled to their own range.
     *
     * @param inverse true when lower is better (a ranking), so the best value is drawn at the top
     */
    public static void line(GuiGraphicsExtractor g, int x, int y, int w, int h, float[] values, int color, boolean inverse) {
        g.fill(x, y, x + w, y + h, 0x30000000);
        g.fill(x, y + h / 2, x + w, y + h / 2 + 1, 0x10FFFFFF);
        g.fill(x, y + h - 1, x + w, y + h, 0x20FFFFFF);
        if (values.length < 2 || w < 8 || h < 6) return;

        float min = values[0], max = values[0];
        for (float v : values) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        float range = Math.max(0.0001f, max - min);
        int area = Decor.fade(color, 0.16f);
        int last = Integer.MIN_VALUE;
        for (int px = 0; px < w; px++) {
            float at = px / (float) (w - 1) * (values.length - 1);
            int i = Math.min(values.length - 2, (int) at);
            float v = values[i] + (values[i + 1] - values[i]) * (at - i);
            float up = max == min ? 0.5f : (v - min) / range;
            if (inverse) up = 1f - up;
            int py = y + h - 2 - Math.round(up * (h - 4));
            int from = last == Integer.MIN_VALUE ? py : Math.min(last, py);
            int to = last == Integer.MIN_VALUE ? py : Math.max(last, py);
            g.fill(x + px, py + 1, x + px + 1, y + h - 1, area);
            g.fill(x + px, from, x + px + 1, to + 1, color);
            last = py;
        }
    }
}
