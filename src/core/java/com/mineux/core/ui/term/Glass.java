package com.mineux.core.ui.term;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Smoked glass: translucent washes inside hairlines, lit from above, one gold accent. What a
 * module's settings window ({@link ModuleWindow}) is drawn with -- the classic panel's look,
 * as flow's menu redrew it -- where a terminal is flat text on a flat sheet.
 * <p>
 * Everything drawn through here is multiplied by {@link #fade}, so a whole card, or a section
 * folding open inside one, fades as one thing.
 */
final class Glass {

    static final int GLASS_TOP = 0xD2171B25;
    static final int GLASS_BOT = 0xE6090B10;

    static final int WELL = 0x4D000000;
    static final int WELL_DEEP = 0x73000000;

    static final int LIFT = 0x14FFFFFF;
    static final int LIFT_HOT = 0x28FFFFFF;
    static final int LIFT_SOFT = 0x0EFFFFFF;

    static final int EDGE = 0x24FFFFFF;
    static final int EDGE_HOT = 0x6BFFFFFF;
    static final int EDGE_BOT = 0x3D000000;

    static final int TEXT = 0xFFF2F2F7;
    static final int TEXT_DIM = 0xFFA2A4B2;
    static final int TEXT_MUTED = 0xFF6D6F80;
    static final int INK = 0xFF15110A;

    static final int GOLD = 0xFFEFC648;
    static final int GOLD_MID = 0xFFC29B26;
    static final int GOLD_EDGE = 0x8CEFC648;
    static final int SLATE = 0xFF555A6E;
    static final int RED = 0xFFFF5C57;

    private static final int[] SHADOW_TOP = {0x1F000000, 0x11000000, 0x07000000};
    private static final int[] SHADOW_SIDE = {0x30000000, 0x1A000000, 0x0B000000};
    private static final int[] SHADOW_BOT = {0x4A000000, 0x2B000000, 0x13000000};

    /** What everything drawn through here is multiplied by. */
    static float fade = 1f;

    private Glass() {}

    // ---- colour math ----------------------------------------------------

    static int alpha(int argb, float a) {
        int aa = Math.round(Math.max(0f, Math.min(1f, a)) * 255f);
        return (aa << 24) | (argb & 0x00FFFFFF);
    }

    static int lit(int argb) {
        return alpha(argb, Math.min(1f, ((argb >>> 24) & 0xFF) / 255f * 1.85f));
    }

    static int mix(int a, int b, float t) {
        return Term.mix(a, b, t);
    }

    /** The fade is handed in, not read: a static int of primitives has to be pure (tools/PureCheck.java). */
    private static int ink(int argb, float fade) {
        return fade >= 1f ? argb : alpha(argb, ((argb >>> 24) & 0xFF) / 255f * fade);
    }

    /** One value eased towards another, the same whatever the frame rate. */
    static float step(float from, float to, float speed, float dt) {
        float k = 1f - (float) Math.exp(0.0 - (double) (speed * dt));
        float next = from + (to - from) * k;
        return Math.abs(to - next) < 0.002f ? to : next;
    }

    // ---- primitives -----------------------------------------------------

    static void fill(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        int c = ink(color, fade);
        if ((c >>> 24) == 0) return;
        g.fill(x1, y1, x2, y2, c);
    }

    static void gradient(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int top, int bottom) {
        g.fillGradient(x1, y1, x2, y2, ink(top, fade), ink(bottom, fade));
    }

    static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        int c = ink(color, fade);
        if ((c >>> 24) < 5) return;
        g.text(font, s, x, y, c, false);
    }

    /** Text at a multiple of its size, for a window's title. */
    static void bigText(GuiGraphicsExtractor g, Font font, String s, int x, int y, float scale, int color) {
        int c = ink(color, fade);
        if ((c >>> 24) < 5) return;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(font, s, 0, 0, c, false);
        g.pose().popMatrix();
    }

    /** How far a rounded corner's row sits in from the rectangle's own edge. */
    private static int inset(int radius, int row) {
        double dy = radius - row - 0.5;
        double dx = Math.sqrt(Math.max(0.0, (double) radius * radius - dy * dy));
        return (int) Math.round(radius - dx);
    }

    /** Bands that touch and never overlap, so a translucent colour composites exactly once. */
    static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        fill(g, x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            fill(g, x + in, y + i, x + w - in, y + i + 1, color);
            fill(g, x + in, y + h - 1 - i, x + w - in, y + h - i, color);
        }
    }

    static void roundGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        float band = (float) r / h;
        gradient(g, x, y + r, x + w, y + h - r, mix(top, bottom, band), mix(top, bottom, 1f - band));
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            float frac = (i + 0.5f) / h;
            fill(g, x + in, y + i, x + w - in, y + i + 1, mix(top, bottom, frac));
            fill(g, x + in, y + h - 1 - i, x + w - in, y + h - i, mix(top, bottom, 1f - frac));
        }
    }

    /** The band of light a sheet of glass carries down from its top edge. */
    private static void sheen(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius) {
        if (w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        int top = 0x15FFFFFF, bottom = 0x00FFFFFF;
        gradient(g, x, y + r, x + w, y + h, mix(top, bottom, (float) r / h), bottom);
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            fill(g, x + in, y + i, x + w - in, y + i + 1, mix(top, bottom, (i + 0.5f) / h));
        }
    }

    /** A 1px outline lit from above: bright top, mid sides, dark lip along the bottom. */
    static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int top, int side, int bottom) {
        if (w <= 1 || h <= 1) return;
        int r = Math.max(1, Math.min(radius, Math.min(w, h) / 2));
        fill(g, x + r, y, x + w - r, y + 1, top);
        fill(g, x + r, y + h - 1, x + w - r, y + h, bottom);
        fill(g, x, y + r, x + 1, y + h - r, side);
        fill(g, x + w - 1, y + r, x + w, y + h - r, side);
        if (r <= 1) return;

        int half = Math.max(1, r / 2);
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            int up = i < half ? top : side;
            int down = i < half ? bottom : side;
            fill(g, x + in, y + i, x + in + 1, y + i + 1, up);
            fill(g, x + w - in - 1, y + i, x + w - in, y + i + 1, up);
            fill(g, x + in, y + h - 1 - i, x + in + 1, y + h - i, down);
            fill(g, x + w - in - 1, y + h - 1 - i, x + w - in, y + h - i, down);
        }
    }

    static void ring(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
        bevel(g, x, y, w, h, radius, color, color, color);
    }

    // ---- surfaces -------------------------------------------------------

    /**
     * The smoked sheet everything else is drawn on: shadow, glass, sheen, hairline.
     *
     * @param accent how much of the gold the hairline carries, 0 to 1: the window with the mouse
     */
    static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, float accent) {
        for (int i = 0; i < SHADOW_TOP.length; i++) {
            int o = i + 1;
            bevel(g, x - o, y - o, w + o * 2, h + o * 2, radius + o, SHADOW_TOP[i], SHADOW_SIDE[i], SHADOW_BOT[i]);
        }
        roundGradient(g, x, y, w, h, radius, GLASS_TOP, GLASS_BOT);
        sheen(g, x + 1, y + 1, w - 2, Math.min(h - 2, 14), Math.max(0, radius - 1));
        bevel(g, x, y, w, h, radius, mix(lit(EDGE), GOLD_EDGE, accent), mix(EDGE, alpha(GOLD, 0.30f), accent), EDGE_BOT);
    }

    /** A sunk area: darkens the card, takes the shadow of its near wall, catches light on the bottom lip. */
    static void well(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int fill) {
        if (w <= 0 || h <= 0) return;
        roundRect(g, x, y, w, h, radius, fill);
        gradient(g, x + radius, y + 1, x + w - radius, y + 1 + Math.min(h - 2, 6), 0x42000000, 0x00000000);
        bevel(g, x, y, w, h, radius, 0x45000000, 0x26000000, 0x1AFFFFFF);
    }

    /** The face every control sits on: a wash of light, a specular line, a hairline carrying the state. */
    static void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int face, int edge) {
        if (w <= 0 || h <= 0) return;
        roundRect(g, x, y, w, h, radius, face);
        if (w > 8 && h > 3) fill(g, x + radius, y + 1, x + w - radius, y + 2, LIFT_SOFT);
        ring(g, x, y, w, h, radius, lit(edge));
    }

    static void pillGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int top, int bottom, int edge) {
        if (w <= 0 || h <= 0) return;
        roundGradient(g, x, y, w, h, radius, top, bottom);
        if (w > 8 && h > 3) fill(g, x + radius, y + 1, x + w - radius, y + 2, 0x33FFFFFF);
        ring(g, x, y, w, h, radius, lit(edge));
    }

    /** The edge between two sheets: a dark seam with the light catching below it. */
    static void divider(GuiGraphicsExtractor g, int x, int y, int w) {
        fill(g, x, y, x + w, y + 1, 0x33000000);
        fill(g, x, y + 1, x + w, y + 2, 0x14FFFFFF);
    }

    static void cross(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        for (int i = 0; i < size; i++) {
            fill(g, x + i, y + i, x + i + 1, y + i + 1, color);
            fill(g, x + size - 1 - i, y + i, x + size - i, y + i + 1, color);
        }
    }

    /** A chevron, 5 across: pointing right at {@code turn} 0, down at 1, and between on the way. */
    static void chevron(GuiGraphicsExtractor g, int x, int y, float turn, int color) {
        if (turn < 0.5f) {
            for (int i = 0; i < 3; i++) {
                fill(g, x + 1 + i, y + i, x + 2 + i, y + i + 1, color);
                fill(g, x + 1 + i, y + 4 - i, x + 2 + i, y + 5 - i, color);
            }
        } else {
            for (int i = 0; i < 3; i++) {
                fill(g, x + i, y + 1 + i, x + i + 1, y + 2 + i, color);
                fill(g, x + 4 - i, y + 1 + i, x + 5 - i, y + 2 + i, color);
            }
        }
    }

    /** The switch: the knob slides across and takes the accent on the way. {@code k} is 0 off, 1 on. */
    static void toggle(GuiGraphicsExtractor g, int x, int y, int w, int h, float k) {
        int r = h / 2;
        roundRect(g, x, y, w, h, r, mix(WELL_DEEP, alpha(GOLD, 0.35f), k));
        bevel(g, x, y, w, h, r, 0x45000000, 0x26000000, 0x14FFFFFF);
        int kx = x + 1 + Math.round((w - 2 - h) * k);
        roundRect(g, kx, y, h, h, r, mix(SLATE, GOLD, k));
        fill(g, kx + r - 1, y + 1, kx + h - r + 1, y + 2, 0x40FFFFFF);
    }

    static void scrollbar(GuiGraphicsExtractor g, int x, int y, int h, float total, float visible, float offset) {
        if (total <= visible || h <= 0) return;
        roundRect(g, x, y, 3, h, 1, WELL_DEEP);
        int thumb = Math.max(12, Math.round(h * visible / total));
        int ty = y + Math.round((h - thumb) * offset / (total - visible));
        roundRect(g, x, ty, 3, thumb, 1, 0x59FFFFFF);
    }

    // ---- text -----------------------------------------------------------

    /** Text broken at spaces to fit a width, at most {@code maxLines} of it; the last line ends in an ellipsis when cut. */
    static List<String> wrap(Font font, String text, int width, int maxLines) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty() || width <= 8) return out;
        StringBuilder line = new StringBuilder();
        String[] words = text.split(" ");
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            String tried = line.length() == 0 ? word : line + " " + word;
            if (font.width(tried) <= width || line.length() == 0) {
                line.setLength(0);
                line.append(tried);
                continue;
            }
            if (out.size() == maxLines - 1) {
                out.add(fit(font, line + " " + word, width));
                return out;
            }
            out.add(line.toString());
            line.setLength(0);
            line.append(word);
        }
        if (line.length() > 0) out.add(fit(font, line.toString(), width));
        return out;
    }

    /** Text cut to a width, with an ellipsis when it was. */
    static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width(".."))) + "..";
    }
}
