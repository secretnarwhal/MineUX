package com.mineux.core.ui.term;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import static com.mineux.core.ui.term.Term.*;

/**
 * The debug panels' instruments, drawn with plain fills: a step's progress bar, the whole
 * sequence as one segmented bar, and a small bar graph of a step's recent delays.
 */
final class Meter {

    static final int TRACK = 0x30FFFFFF;

    private Meter() {}

    /** A bar {@code progress} of the way full, on a faint track. */
    static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float progress, int fill, float alpha) {
        if (w <= 0) return;
        g.fill(x, y, x + w, y + h, Decor.fade(TRACK, alpha));
        int filled = Math.round(w * Math.max(0f, Math.min(1f, progress)));
        if (filled > 0) g.fill(x, y, x + filled, y + h, Decor.fade(fill, alpha));
    }

    /** A step's colour right now: waiting gold, a gauge cyan, green just after it fired, red just after a skip. */
    static int color(Trace.Step s, long now) {
        if (now - s.skippedAt < Trace.FLASH_MS * 2 && s.skippedAt > s.firedAt) return RED;
        if (s.waiting) return GOLD;
        if (s.gauge >= 0f) return CYAN;
        if (now - s.firedAt < Trace.FLASH_MS) return GREEN;
        return FAINT;
    }

    /**
     * The sequence: one segment per step, a pixel apart. Steps before the one in progress are
     * full, that one fills as its own bar does, the ones after it are empty.
     */
    static void sequence(GuiGraphicsExtractor g, int x, int y, int w, int h, Trace.Run run, long now, float alpha) {
        int n = run.steps.size();
        if (n == 0 || w < n * 3) return;
        int current = -1;
        for (int i = 0; i < n && current < 0; i++) {
            Trace.Step s = run.steps.get(i);
            if (s.waiting || s.gauge >= 0f) current = i;
        }
        float seg = (w - (n - 1)) / (float) n;
        for (int i = 0; i < n; i++) {
            int x0 = x + Math.round(i * (seg + 1));
            int x1 = x + Math.round(i * (seg + 1) + seg);
            Trace.Step s = run.steps.get(i);
            float p = current < 0 ? s.progress(now) : i < current ? 1f : i == current ? s.progress(now) : 0f;
            int fill = current < 0 ? color(s, now) : i < current ? GREEN : color(s, now);
            bar(g, x0, y, x1 - x0, h, p, fill, alpha);
        }
    }

    /**
     * The step's last {@link Trace#HISTORY} values as bars, scaled to the largest of them,
     * the newest brightest. Nothing is drawn for a step that has not fired yet.
     */
    static void graph(GuiGraphicsExtractor g, int x, int y, int w, int h, Trace.Step s, float alpha) {
        if (w < 6 || s.count == 0) return;
        g.fill(x, y + h - 1, x + w, y + h, Decor.fade(0x18FFFFFF, alpha));
        float max = 1f;
        for (int i = 0; i < s.count; i++) max = Math.max(max, s.sample(i));
        int slots = Trace.HISTORY;
        float step = w / (float) slots;
        int barW = Math.max(1, Math.round(step) - 1);
        int first = slots - s.count;
        for (int i = 0; i < s.count; i++) {
            float v = s.sample(i);
            int bh = Math.max(1, Math.round(v / max * (h - 1)));
            int bx = x + Math.round((first + i) * step);
            boolean newest = i == s.count - 1;
            int color = newest ? GOLD : Decor.mix(0x60B8962E, 0xC0EFC648, i / (float) Math.max(1, s.count - 1));
            g.fill(bx, y + h - 1 - bh, bx + barW, y + h - 1, Decor.fade(color, alpha));
        }
    }
}
