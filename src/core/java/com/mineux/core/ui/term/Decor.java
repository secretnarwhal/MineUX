package com.mineux.core.ui.term;

import com.mineux.core.ui.term.Term.Hit;
import com.mineux.core.ui.term.Term.Span;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Iterator;
import java.util.List;

import static com.mineux.core.ui.term.Term.*;

/**
 * What {@code hyprland.conf} decides about how a pane looks, rather than what is in it: its
 * border (a colour or a gradient, turning when {@code borderangle} loops, fading between
 * focused and not over {@code border}), the dimming of the unfocused ones, a closed pane's
 * frame shrinking away, and a group's tabs.
 * <p>
 * A gradient border costs a handful of fills a side, not one per pixel: along a straight edge
 * a linear gradient is linear too, so each stretch between two of its colours is one gradient
 * fill -- turned on its side for the top and bottom edges, since the GUI only fills them
 * top to bottom.
 */
final class Decor {

    private Decor() {}

    /** Glass, as the panes draw it, for a closing pane's frame. */
    private static final int GLASS_TOP = 0xA6121620, GLASS_BOTTOM = 0xBC07080C;

    // ---- borders --------------------------------------------------------

    /**
     * A pane's border, just outside its rectangle.
     *
     * @param lit a colour for an unfocused pane asking to be noticed (a readout's event), or 0
     */
    static void border(GuiGraphicsExtractor g, Pane pane, int x, int y, int w, int h, boolean focused, int lit) {
        HyprConf conf = HyprConf.get();
        int size = conf.borderSize;
        if (size <= 0) return;
        long now = System.currentTimeMillis();
        float alpha = pane.appear(now);

        long at = pane.borderAt;
        if (pane.borderFocused != focused) {
            pane.borderFocused = focused;
            at = at == 0L ? 1L : now;
            pane.borderAt = at;
        }
        long ms = conf.ms("border");
        float t = ms <= 0 || at <= 1L ? 1f : (now - at) / (float) ms;
        float e = t >= 1f ? 1f : conf.curve("border", t);

        HyprConf.Gradient to = colors(conf, pane, focused, lit);
        int fx = x - size, fy = y - size, fw = w + 2 * size, fh = h + 2 * size;
        if (e < 1f) {
            // Focus just changed: the old colours fade out under the new ones fading in.
            frame(g, fx, fy, fw, fh, size, colors(conf, pane, !focused, lit), angle(conf, !focused, now), alpha * (1f - e));
        }
        frame(g, fx, fy, fw, fh, size, to, angle(conf, focused, now), alpha * e);
    }

    private static HyprConf.Gradient colors(HyprConf conf, Pane pane, boolean focused, int lit) {
        if (pane.group != null) return focused ? conf.groupActive : conf.groupInactive;
        if (focused) return conf.activeBorder;
        if (lit != 0) return new HyprConf.Gradient(new int[] {lit}, 0f);
        return conf.inactiveBorder;
    }

    /** The gradient's angle this frame: turning, for the focused pane, while {@code borderangle} loops. */
    private static float angle(HyprConf conf, boolean focused, long now) {
        if (!focused) return 0f;
        long ms = conf.ms("borderangle");
        if (ms <= 0 || !conf.style("borderangle").contains("loop")) return 0f;
        return 360f * (now % ms) / ms;
    }

    /** A frame {@code t} thick inside the box, in the gradient's colours, its alpha scaled by {@code alpha}. */
    static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, int t, HyprConf.Gradient grad, float spin, float alpha) {
        if (alpha <= 0.01f || w <= 2 * t || h <= 2 * t) return;
        int[] c = grad.colors;
        if (c.length == 1) {
            int col = fade(c[0], alpha);
            g.fill(x, y, x + w, y + t, col);
            g.fill(x, y + h - t, x + w, y + h, col);
            g.fill(x, y + t, x + t, y + h - t, col);
            g.fill(x + w - t, y + t, x + w, y + h - t, col);
            return;
        }
        double rad = Math.toRadians(grad.angle + spin);
        float dx = (float) Math.cos(rad), dy = (float) -Math.sin(rad);
        float cx = x + w / 2f, cy = y + h / 2f;
        float span = Math.max(1f, Math.abs(dx) * w + Math.abs(dy) * h);

        // Each edge: where along the gradient its two ends are.
        float top = y + t / 2f, bottom = y + h - t / 2f, left = x + t / 2f, right = x + w - t / 2f;
        edge(g, true, x, x + w, y, t, at(x, top, cx, cy, dx, dy, span), at(x + w, top, cx, cy, dx, dy, span), c, alpha);
        edge(g, true, x, x + w, y + h - t, t, at(x, bottom, cx, cy, dx, dy, span), at(x + w, bottom, cx, cy, dx, dy, span), c, alpha);
        edge(g, false, y + t, y + h - t, x, t, at(left, y + t, cx, cy, dx, dy, span), at(left, y + h - t, cx, cy, dx, dy, span), c, alpha);
        edge(g, false, y + t, y + h - t, x + w - t, t, at(right, y + t, cx, cy, dx, dy, span), at(right, y + h - t, cx, cy, dx, dy, span), c, alpha);
    }

    private static float at(float px, float py, float cx, float cy, float dx, float dy, float span) {
        return ((px - cx) * dx + (py - cy) * dy) / span + 0.5f;
    }

    /**
     * One edge from {@code a} to {@code b} (along x when {@code horizontal}), {@code t} thick at
     * {@code across}, its ends at {@code t0} and {@code t1} along the gradient: one gradient fill
     * per stretch between two of the colours.
     */
    private static void edge(GuiGraphicsExtractor g, boolean horizontal, int a, int b, int across, int t,
                             float t0, float t1, int[] colors, float alpha) {
        if (b <= a) return;
        int stops = colors.length - 1;
        float from = t0;
        int start = a;
        boolean up = t1 >= t0;
        // The stops the edge passes, in the order it passes them.
        for (int i = 1; i < stops; i++) {
            int k = up ? i : stops - i;
            float stop = k / (float) stops;
            if (stop <= Math.min(t0, t1) || stop >= Math.max(t0, t1)) continue;
            int pos = a + Math.round((stop - t0) / (t1 - t0) * (b - a));
            if (pos <= start || pos >= b) continue;
            fillEdge(g, horizontal, start, pos, across, t, fade(colorAt(colors, from), alpha), fade(colorAt(colors, stop), alpha));
            start = pos;
            from = stop;
        }
        fillEdge(g, horizontal, start, b, across, t, fade(colorAt(colors, from), alpha), fade(colorAt(colors, t1), alpha));
    }

    private static void fillEdge(GuiGraphicsExtractor g, boolean horizontal, int a, int b, int across, int t, int c0, int c1) {
        if (!horizontal) {
            g.fillGradient(across, a, across + t, b, c0, c1);
            return;
        }
        if (c0 == c1) {
            g.fill(a, across, b, across + t, c0);
            return;
        }
        // Turned a quarter: the fill's top-to-bottom runs left to right along the edge.
        g.pose().pushMatrix();
        g.pose().translate(a, across + t);
        g.pose().rotate((float) (-Math.PI / 2));
        g.fillGradient(0, 0, t, b - a, c0, c1);
        g.pose().popMatrix();
    }

    static int colorAt(int[] colors, float t) {
        float f = Math.max(0f, Math.min(1f, t)) * (colors.length - 1);
        int i = Math.min(colors.length - 2, (int) f);
        return mix(colors[i], colors[i + 1], f - i);
    }

    static int mix(int a, int b, float f) {
        int aa = a >>> 24, ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
        return Math.round(aa + (ba - aa) * f) << 24 | Math.round(ar + (br - ar) * f) << 16
            | Math.round(ag + (bg - ag) * f) << 8 | Math.round(ab + (bb - ab) * f);
    }

    /** A colour with its alpha scaled. */
    static int fade(int color, float alpha) {
        if (alpha >= 1f) return color;
        int a = Math.round((color >>> 24) * Math.max(0f, alpha));
        return a << 24 | color & 0xFFFFFF;
    }

    // ---- dimming ----------------------------------------------------------

    /** {@code dim_inactive} and {@code dim_special}: black over a box, {@code strength} of the way. */
    static void dim(GuiGraphicsExtractor g, int x, int y, int w, int h, float strength) {
        if (strength <= 0.005f) return;
        g.fill(x, y, x + w, y + h, Math.round(Math.min(1f, strength) * 255f) << 24);
    }

    // ---- closing ----------------------------------------------------------

    /**
     * Closed panes' frames, shrinking ({@code windowsOut}'s popin) and fading ({@code fadeOut})
     * where they were; each goes once both are done.
     */
    static void afters(GuiGraphicsExtractor g, Desk desk, long now) {
        if (desk.afters.isEmpty()) return;
        HyprConf conf = HyprConf.get();
        long moveMs = conf.ms("windowsOut"), fadeMs = conf.ms("fadeOut");
        float pct = conf.style("windowsOut").startsWith("popin") ? conf.percent("windowsOut", 0.8f) : 1f;
        for (Iterator<Desk.After> it = desk.afters.iterator(); it.hasNext(); ) {
            Desk.After after = it.next();
            long age = now - after.at;
            if (age >= Math.max(moveMs, fadeMs)) {
                it.remove();
                continue;
            }
            float move = moveMs <= 0 ? 1f : conf.curve("windowsOut", Math.min(1f, age / (float) moveMs));
            float alpha = fadeMs <= 0 ? 0f : 1f - conf.curve("fadeOut", Math.min(1f, age / (float) fadeMs));
            float scale = 1f - (1f - pct) * move;
            float w = after.w * scale, h = after.h * scale;
            int x = Math.round(after.x + (after.w - w) / 2f), y = Math.round(after.y + (after.h - h) / 2f);
            int iw = Math.round(w), ih = Math.round(h);
            g.fillGradient(x, y, x + iw, y + ih, fade(GLASS_TOP, alpha), fade(GLASS_BOTTOM, alpha));
            int size = conf.borderSize;
            if (size > 0) frame(g, x - size, y - size, iw + 2 * size, ih + 2 * size, size, conf.inactiveBorder, 0f, alpha);
        }
    }

    // ---- groups -----------------------------------------------------------

    /** A group's tabs, in the strip over its showing pane; a click on one shows it. */
    static void groupBar(GuiGraphicsExtractor g, Font font, Desk desk, Group group, boolean focused,
                         int mouseX, int mouseY, List<Hit> hits) {
        Pane active = group.active;
        if (active == null || group.members.isEmpty()) return;
        int x = active.ix(), w = active.iw();
        int y = active.iy() - Group.BAR_H;
        int n = group.members.size();
        float tabW = w / (float) n;
        HyprConf conf = HyprConf.get();
        int accent = (focused ? conf.groupActive : conf.groupInactive).colors[0] | 0xFF000000;

        for (int i = 0; i < n; i++) {
            Pane tab = group.members.get(i);
            int x0 = x + Math.round(i * tabW), x1 = x + Math.round((i + 1) * tabW) - (i < n - 1 ? 1 : 0);
            boolean showing = tab == active;
            boolean hot = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + Group.BAR_H - 1;
            g.fill(x0, y, x1, y + Group.BAR_H - 1, showing ? 0xC0151A14 : hot ? 0x70202428 : 0x80090A0D);
            g.fill(x0, y + Group.BAR_H - 3, x1, y + Group.BAR_H - 1, showing ? accent : fade(accent, 0.35f));
            String title = font.plainSubstrByWidth(tab.title(), Math.max(0, x1 - x0 - 6));
            g.text(font, title, x0 + 3, y + 1, showing ? FG : hot ? DIM : FAINT, false);

            Span span = new Span(title, FG);
            span.hint = "show " + tab.title();
            span.action = () -> {
                desk.show(group, tab);
                desk.focus(tab);
            };
            hits.add(new Hit(x0, y, x1, y + Group.BAR_H - 1, null, span, -1, null, ACT_NONE));
        }
    }

    /** The group whose tab strip is under a point, among a desk's showing panes; null for none. */
    static Group groupBarAt(Desk desk, double mx, double my) {
        for (Pane p : desk.windows) {
            if (p.group == null || p.group.active != p) continue;
            if (mx >= p.x && mx < p.x + p.w && my >= p.y - Group.BAR_H && my < p.y) return p.group;
        }
        return null;
    }
}
