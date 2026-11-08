package com.mineux.visuals.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The visuals' surface: dark transparent glass (the same look as the desktop's).
 * <p>
 * Every surface in the panel is a tint over whatever is already behind it rather than a
 * colour of its own. The card is a sheet of smoked glass over a blurred world, a control
 * is that sheet catching more light, a well is it catching less, and the depth comes from
 * hairlines along the edges rather than from opaque boxes stacked on each other. Only the
 * accents and the text are solid, because those are the two things that have to survive
 * whatever the world behind them happens to be.
 * <p>
 * Two rules follow from that and run through every primitive here.
 * <p>
 * <b>Fills must not overlap.</b> Two passes of the same translucent colour is a different,
 * denser colour. The rounded rect used to be two rects laid across each other, which was
 * free while every colour in the panel was opaque and is a visible ring around every panel
 * the moment one is not; it is three non-overlapping bands now. For the same reason a
 * border is drawn as a {@link #ring} rather than as a filled rect with the face painted
 * back over the middle of it.
 * <p>
 * <b>Light comes from above.</b> A card's edge is three colours -- a lit top, mid sides,
 * and a dark lip along the bottom -- and every raised face carries a specular line just
 * inside its top edge. A well inverts it: the light lands inside the far wall, so it is
 * dark along the top and catches along the bottom. That one convention is what makes a
 * flat stack of translucent rectangles read as glass rather than as tinted paper.
 * <p>
 * Controls are the exception, and {@link #pill} is where they part company with it. The
 * hairline round a card is only saying where the card ends, so it can afford to be three
 * colours; the hairline round a control is carrying its state -- on, off, risky, hovered,
 * disabled -- and a state that holds its colour along the top, half of it down the sides,
 * and none of it across the bottom is a state you have to go looking for. Gold that stops
 * three quarters of the way round reads as a button drawn wrong rather than as one that is
 * lit. So a control is ringed in one flat colour the whole way round and takes its light
 * from the specular line inside its top edge instead, which is the half of the convention
 * that was doing the work anyway.
 * <p>
 * Text is drawn without a drop shadow throughout -- see {@link #text}.
 */
public final class Theme {

    private Theme() {}

    // ---- backdrop ----------------------------------------------------

    /**
     * Over the blurred world. Light enough at the top that the blur still reads as colour
     * rather than as grey: the panel is see-through, and a scrim dense enough to hide what
     * is behind it would be arguing with the rest of the theme.
     */
    public static final int SCRIM_TOP = 0x8A0A0C13;
    public static final int SCRIM_BOT = 0xCC03040A;

    // ---- glass -------------------------------------------------------

    /** The card, lit from above: thinner and cooler at the top, denser at the bottom. */
    public static final int GLASS_TOP = 0xC6171B25;
    public static final int GLASS_BOT = 0xDE090B10;

    /** The same sheet as one flat value, where a gradient would only be noise. */
    public static final int GLASS = 0xDE12161F;

    /**
     * The open tab: the card, one step further into the light.
     * <p>
     * It has to carry against a well rather than against the card, which is why this is
     * stronger than {@link #LIFT} -- an open tab sits in the one sunk strip on the panel
     * and has to read as the pane below coming up through it.
     */
    public static final int GLASS_LIT = 0x2EFFFFFF;

    /** A sunk area. Darkens what is behind it rather than painting its own colour on. */
    public static final int WELL = 0x4D000000;
    public static final int WELL_DEEP = 0x73000000;

    /** A control's face: the card catching more light. */
    public static final int LIFT = 0x14FFFFFF;
    public static final int LIFT_HOT = 0x28FFFFFF;

    /** The specular line just inside a raised top edge, and the faintest wash there is. */
    public static final int LIFT_SOFT = 0x0EFFFFFF;

    // ---- hairlines ---------------------------------------------------

    /**
     * A resting edge. Round a card {@link #bevel} brightens this for the top and pairs it
     * with {@link #EDGE_BOT} underneath; round a control {@link #pill} takes it {@link #lit}
     * the whole way round, so a control says the same thing on all four of its sides.
     */
    public static final int EDGE = 0x24FFFFFF;

    /** A quieter edge, for the divisions inside a card rather than around one. */
    public static final int EDGE_SOFT = 0x12FFFFFF;

    /** Under the cursor, or holding focus. */
    public static final int EDGE_HOT = 0x6BFFFFFF;

    /** The lip a surface presents away from the light. Dark, never white. */
    public static final int EDGE_BOT = 0x3D000000;

    // ---- ink ---------------------------------------------------------

    public static final int TEXT = 0xFFF2F2F7;
    public static final int TEXT_DIM = 0xFFA2A4B2;
    public static final int TEXT_MUTED = 0xFF6D6F80;

    /** On a gold or a red face, where the ink has to be the dark half of the contrast. */
    public static final int INK = 0xFF15110A;

    // ---- accents -----------------------------------------------------

    /** The house colour. */
    public static final int GOLD = 0xFFEFC648;
    public static final int GOLD_MID = 0xFFC29B26;
    public static final int GOLD_DEEP = 0xFF6E5711;
    public static final int GOLD_FAINT = 0x2EEFC648;
    public static final int GOLD_EDGE = 0x8CEFC648;

    public static final int GREEN = 0xFF56D98D;
    public static final int GREEN_MID = 0xFF34AE66;
    public static final int GREEN_DEEP = 0xFF1B6C41;

    public static final int RED = 0xFFF26254;
    public static final int RED_MID = 0xFFC33F33;
    public static final int RED_DEEP = 0xFF75241D;

    /**
     * The unaccented tone. Translucent, unlike the other three: slate is what a control
     * wears when it has nothing to say, and the glass showing through is the point of it.
     */
    public static final int SLATE = 0xB2555A6E;
    public static final int SLATE_MID = 0xAA3C4152;
    public static final int SLATE_DEEP = 0xB2262A36;

    // ---- colour math -------------------------------------------------

    public static int alpha(int argb, float a) {
        int base = argb & 0x00FFFFFF;
        int aa = Math.round(Math.max(0f, Math.min(1f, a)) * 255f);
        return (aa << 24) | base;
    }

    /**
     * The same colour at a fraction of the opacity it already had.
     * <p>
     * {@link #alpha} sets an opacity; this scales one, which is what a section fading in
     * needs -- the colours it is made of already carry opacities of their own, and a fade
     * has to take all of them down together rather than flatten them to one value.
     */
    public static int fade(int argb, float f) {
        return alpha(argb, ((argb >>> 24) & 0xFF) / 255f * f);
    }

    /** The one-line "skip the call if it would be a no-op" every widget's own {@code ink} did by hand. */
    public static int faded(int argb, float fade) {
        return fade >= 1f ? argb : fade(argb, fade);
    }

    /** The same colour, more opaque, for the edge a surface presents to the light. */
    public static int lit(int argb) {
        return alpha(argb, Math.min(1f, ((argb >>> 24) & 0xFF) / 255f * 1.85f));
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (int) (((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * t);
        int rr = (int) (((a >>> 16) & 0xFF) + (((b >>> 16) & 0xFF) - ((a >>> 16) & 0xFF)) * t);
        int gg = (int) (((a >>> 8) & 0xFF) + (((b >>> 8) & 0xFF) - ((a >>> 8) & 0xFF)) * t);
        int bb = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (aa << 24) | (rr << 16) | (gg << 8) | bb;
    }

    public static int lighten(int argb, float amount) {
        return mix(argb, 0xFFFFFFFF, amount);
    }

    public static int darken(int argb, float amount) {
        return mix(argb, 0xFF000000, amount);
    }

    /**
     * Lighter at the opacity it already had.
     * <p>
     * {@link #lighten} mixes toward opaque white, which drags a translucent colour toward
     * solid on the way. That is right for an accent and wrong for anything made of glass,
     * where the whole point of the colour is how much of the world it lets through.
     */
    public static int tint(int argb, float amount) {
        return (argb & 0xFF000000) | (lighten(argb, amount) & 0x00FFFFFF);
    }

    /** Darker at the opacity it already had. The counterpart to {@link #tint}. */
    public static int shade(int argb, float amount) {
        return (argb & 0xFF000000) | (darken(argb, amount) & 0x00FFFFFF);
    }

    /** Hue in [0, 360), saturation and value in [0, 1]. */
    public record Hsv(float h, float s, float v) {}

    /** Standard RGB-to-HSV conversion. r/g/b in [0, 1]. */
    public static Hsv rgbToHsv(float r, float g, float b) {
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        float h;
        if (delta < 1e-6f) {
            h = 0f;
        } else if (max == r) {
            h = 60f * (((g - b) / delta) % 6f);
        } else if (max == g) {
            h = 60f * (((b - r) / delta) + 2f);
        } else {
            h = 60f * (((r - g) / delta) + 4f);
        }
        if (h < 0f) h += 360f;

        float s = max <= 0f ? 0f : delta / max;
        return new Hsv(h, s, max);
    }

    /** The inverse of {@link #rgbToHsv}. Returns opaque 0xFFRRGGBB. */
    public static int hsvToRgb(float h, float s, float v) {
        h = ((h % 360f) + 360f) % 360f;
        s = Math.max(0f, Math.min(1f, s));
        v = Math.max(0f, Math.min(1f, v));

        float c = v * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = v - c;

        float r, g, b;
        if (h < 60f)       { r = c; g = x; b = 0f; }
        else if (h < 120f) { r = x; g = c; b = 0f; }
        else if (h < 180f) { r = 0f; g = c; b = x; }
        else if (h < 240f) { r = 0f; g = x; b = c; }
        else if (h < 300f) { r = x; g = 0f; b = c; }
        else               { r = c; g = 0f; b = x; }

        return 0xFF000000
            | (clampByte(Math.round((r + m) * 255f)) << 16)
            | (clampByte(Math.round((g + m) * 255f)) << 8)
            | clampByte(Math.round((b + m) * 255f));
    }

    private static int clampByte(int v) {
        return Math.max(0, Math.min(255, v));
    }

    // ---- text --------------------------------------------------------

    /**
     * Every string in this panel, drawn without a drop shadow.
     * <p>
     * {@code GuiGraphicsExtractor#text} takes the shadow flag, and the five-argument form
     * everything reaches for first hard-codes it to true. A shadow is the vanilla HUD's
     * answer to text over an arbitrary world, and it derives its colour by scaling the
     * text's own down: for the light-on-dark labels here that is a faint smear a pixel
     * down and right, and for the dark ink on a gold chip or a slot number it is a second,
     * nearly-black copy of the glyph offset from the first. That is what turned the slot
     * row and the primary buttons to mush.
     * <p>
     * Nothing in this panel needs one. The card is dense enough to carry text on its own,
     * which is a thing the glass has to be worth anyway, so the shadow was buying contrast
     * that was already paid for and costing legibility to do it.
     */
    public static void text(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color) {
        g.text(font, text, x, y, color, false);
    }

    public static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        g.text(font, text, x, y, color, false);
    }

    /**
     * The same, centred on {@code centreX}.
     * <p>
     * Written out rather than deferred to {@code centeredText}, which has no shadow flag
     * of its own and hands the five-argument {@code text} the string it centred.
     */
    public static void centeredText(GuiGraphicsExtractor g, Font font, Component text,
                                    int centreX, int y, int color) {
        g.text(font, text, centreX - font.width(text) / 2, y, color, false);
    }

    public static void centeredText(GuiGraphicsExtractor g, Font font, String text,
                                    int centreX, int y, int color) {
        g.text(font, text, centreX - font.width(text) / 2, y, color, false);
    }

    // ---- primitives --------------------------------------------------

    /**
     * Solid rect with 1px clipped corners, which reads as a subtle radius.
     * <p>
     * Three bands that touch and never overlap, so a translucent colour composites exactly
     * once over whatever is behind it.
     */
    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        roundRect(g, x, y, w, h, 1, color);
    }

    /**
     * The same shape at a real radius, for the one surface in the panel worth a visible
     * curve rather than the usual 1px clip -- the outer card, now that its corners double
     * as resize handles. A middle band the full width, then one row per side of the corner,
     * each narrowed by however far the circle of that radius has come in by that row: the
     * same scanline approach {@link #disc} uses, so rows never overlap.
     */
    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;

        radius = Math.max(0, Math.min(radius, Math.min(w, h) / 2));

        if (radius <= 1) {
            if (w <= 2 || h <= 2) {
                g.fill(x, y, x + w, y + h, color);
                return;
            }

            g.fill(x + 1, y, x + w - 1, y + 1, color);
            g.fill(x, y + 1, x + w, y + h - 1, color);
            g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
            return;
        }

        g.fill(x, y + radius, x + w, y + h - radius, color);

        for (int i = 0; i < radius; i++) {
            int inset = cornerInset(radius, i);
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    /**
     * How far a rounded corner's row {@code row} (0 at the very tip) sits in from the
     * rectangle's own edge, for a corner of the given {@code radius}. Shared by every
     * radius-aware primitive below, so a card's fill, its hairline and a control's own
     * curve all draw the same circle.
     */
    private static int cornerInset(int radius, int row) {
        double dy = radius - row - 0.5;
        double dx = Math.sqrt(Math.max(0.0, (double) radius * radius - dy * dy));
        return (int) Math.round(radius - dx);
    }

    /**
     * {@link #roundRect} with only its top two corners curved, the bottom two left square.
     * <p>
     * For a wash nested inside the top of a rounded card -- the header band, the sheen off
     * its top edge -- which shares the card's own top curve but ends partway down the card
     * at an edge nothing else is rounded against. Rounding that edge too would be a second,
     * unrelated curve with nothing to answer to.
     */
    public static void roundRectTop(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;

        radius = Math.max(0, Math.min(radius, Math.min(w, h) / 2));

        if (radius <= 1) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }

        g.fill(x, y + radius, x + w, y + h, color);

        for (int i = 0; i < radius; i++) {
            int inset = cornerInset(radius, i);
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
        }
    }

    /**
     * The same shape under a vertical gradient.
     * <p>
     * The two clipped rows are drawn flat at the colour the gradient holds where they sit,
     * so the three bands line up into one continuous ramp rather than restarting at each
     * one's own top.
     */
    public static void roundGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int top, int bottom) {
        roundGradient(g, x, y, w, h, 1, top, bottom);
    }

    /** {@link #roundGradient} at a real radius, on the same terms {@link #roundRect} takes one. */
    public static void roundGradient(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                     int radius, int top, int bottom) {
        if (w <= 0 || h <= 0) return;

        radius = Math.max(0, Math.min(radius, Math.min(w, h) / 2));

        if (radius <= 1) {
            if (w <= 2 || h <= 2) {
                g.fillGradient(x, y, x + w, y + h, top, bottom);
                return;
            }

            float step = 1f / h;

            g.fill(x + 1, y, x + w - 1, y + 1, mix(top, bottom, step * 0.5f));
            g.fillGradient(x, y + 1, x + w, y + h - 1, mix(top, bottom, step), mix(top, bottom, 1f - step));
            g.fill(x + 1, y + h - 1, x + w - 1, y + h, mix(top, bottom, 1f - step * 0.5f));
            return;
        }

        float topBandFrac = (float) radius / h;
        g.fillGradient(x, y + radius, x + w, y + h - radius,
            mix(top, bottom, topBandFrac), mix(top, bottom, 1f - topBandFrac));

        for (int i = 0; i < radius; i++) {
            int inset = cornerInset(radius, i);
            float frac = (i + 0.5f) / h;

            g.fill(x + inset, y + i, x + w - inset, y + i + 1, mix(top, bottom, frac));
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, mix(top, bottom, 1f - frac));
        }
    }

    /** {@link #roundGradient} with only its top two corners curved, on {@link #roundRectTop}'s own terms. */
    public static void roundGradientTop(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                        int radius, int top, int bottom) {
        if (w <= 0 || h <= 0) return;

        radius = Math.max(0, Math.min(radius, Math.min(w, h) / 2));

        if (radius <= 1) {
            g.fillGradient(x, y, x + w, y + h, top, bottom);
            return;
        }

        float topBandFrac = (float) radius / h;
        g.fillGradient(x, y + radius, x + w, y + h, mix(top, bottom, topBandFrac), bottom);

        for (int i = 0; i < radius; i++) {
            int inset = cornerInset(radius, i);
            float frac = (i + 0.5f) / h;

            g.fill(x + inset, y + i, x + w - inset, y + i + 1, mix(top, bottom, frac));
        }
    }

    /** A 1px outline on the rect's own edge, corners clipped. Never touches the interior. */
    public static void ring(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        ring(g, x, y, w, h, 1, color);
    }

    /**
     * The same outline, curved round a real radius: straight runs between the corners, then
     * one outer pixel per row of the corner -- a pixelled arc rather than the true curve
     * {@link #roundRect}'s fill takes, because an outline is one pixel wide everywhere else
     * on this theme and a smoothed one would be the only line in the panel that was not.
     */
    public static void ring(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 1 || h <= 1) return;

        radius = Math.max(0, Math.min(radius, Math.min(w, h) / 2));

        if (radius <= 1) {
            g.fill(x + 1, y, x + w - 1, y + 1, color);
            g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
            g.fill(x, y + 1, x + 1, y + h - 1, color);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
            return;
        }

        g.fill(x + radius, y, x + w - radius, y + 1, color);
        g.fill(x + radius, y + h - 1, x + w - radius, y + h, color);
        g.fill(x, y + radius, x + 1, y + h - radius, color);
        g.fill(x + w - 1, y + radius, x + w, y + h - radius, color);

        for (int i = 0; i < radius; i++) {
            int inset = cornerInset(radius, i);

            g.fill(x + inset, y + i, x + inset + 1, y + i + 1, color);
            g.fill(x + w - inset - 1, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + inset + 1, y + h - i, color);
            g.fill(x + w - inset - 1, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    /**
     * A ring lit from above: a bright top, mid sides, and a dark lip along the bottom.
     * <p>
     * The one thing that separates a raised surface from a sunk one here. Both are a
     * translucent wash inside a hairline; which way round the hairline runs is the whole
     * of the difference.
     */
    public static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h,
                             int top, int side, int bottom) {
        if (w <= 1 || h <= 1) return;

        g.fill(x + 1, y, x + w - 1, y + 1, top);
        g.fill(x, y + 1, x + 1, y + h - 1, side);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, side);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, bottom);
    }

    /**
     * {@link #bevel} round a real radius, following {@link #ring}'s own pixelled arc: the
     * corner's near half reads as whichever straight edge it is closer to, top or bottom
     * rather than the side, since that is the edge a hand-drawn corner would have carried
     * the colour from anyway.
     */
    public static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius,
                             int top, int side, int bottom) {
        if (w <= 1 || h <= 1) return;

        radius = Math.max(0, Math.min(radius, Math.min(w, h) / 2));

        if (radius <= 1) {
            bevel(g, x, y, w, h, top, side, bottom);
            return;
        }

        g.fill(x + radius, y, x + w - radius, y + 1, top);
        g.fill(x + radius, y + h - 1, x + w - radius, y + h, bottom);
        g.fill(x, y + radius, x + 1, y + h - radius, side);
        g.fill(x + w - 1, y + radius, x + w, y + h - radius, side);

        int half = Math.max(1, radius / 2);

        for (int i = 0; i < radius; i++) {
            int inset = cornerInset(radius, i);
            int edgeColor = i < half ? top : side;
            int bottomEdgeColor = i < half ? bottom : side;

            g.fill(x + inset, y + i, x + inset + 1, y + i + 1, edgeColor);
            g.fill(x + w - inset - 1, y + i, x + w - inset, y + i + 1, edgeColor);
            g.fill(x + inset, y + h - 1 - i, x + inset + 1, y + h - i, bottomEdgeColor);
            g.fill(x + w - inset - 1, y + h - 1 - i, x + w - inset, y + h - i, bottomEdgeColor);
        }
    }

    /**
     * The band of light a sheet of glass carries down from its top edge, fading out.
     * <p>
     * Deliberately weak and short. A sheen with any real weight to it stops being an edge
     * catching the light and becomes a lighter slab sitting on the card, which is the
     * failure mode this whole theme is one step away from at all times: the eye reads a
     * flat pale rectangle as paper, and no amount of blur behind it argues that back.
     */
    public static void sheen(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        sheen(g, x, y, w, h, 0);
    }

    /**
     * The same, with its top corners rounded off to match whatever it sits inside -- a
     * sheen drawn square inside a rounded card would carry its own wash a couple of px past
     * the glass it is meant to be lighting, into the gap the round corner leaves behind it.
     */
    public static void sheen(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius) {
        if (w <= 0 || h <= 0) return;
        roundGradientTop(g, x, y, w, h, radius, 0x15FFFFFF, 0x00FFFFFF);
    }

    /** The shadow the near wall of a sunk area casts down its own back, fading out. */
    public static void innerShade(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        g.fillGradient(x, y, x + w, y + h, 0x42000000, 0x00000000);
    }

    /** Alpha per ring, outermost last. Split three ways so the light stays overhead. */
    private static final int[] SHADOW_TOP = { 0x1F000000, 0x11000000, 0x07000000 };
    private static final int[] SHADOW_SIDE = { 0x30000000, 0x1A000000, 0x0B000000 };
    private static final int[] SHADOW_BOT = { 0x4A000000, 0x2B000000, 0x13000000 };

    /**
     * Three nested rings falling away from a card's edge.
     * <p>
     * Rings rather than filled rects, so nothing is drawn under the card itself and the
     * shadow never darkens the glass it belongs to. Heavier along the bottom than the top,
     * for the same reason every bevel in here is -- which is exactly why a square corner on
     * this one shows: the bottom rings are the darkest colour here, and a square corner on a
     * rounded card is a patch of it sitting past the curve rather than a faint mismatch like
     * the same square corner is on {@link #sheen}.
     */
    public static void dropShadow(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        dropShadow(g, x, y, w, h, 0);
    }

    /** The same, each ring's own corner grown outward by however far it already sits from the card. */
    public static void dropShadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius) {
        for (int i = 0; i < SHADOW_TOP.length; i++) {
            int o = i + 1;
            int ringRadius = radius <= 1 ? 0 : radius + o;

            bevel(g, x - o, y - o, w + o * 2, h + o * 2, ringRadius,
                SHADOW_TOP[i], SHADOW_SIDE[i], SHADOW_BOT[i]);
        }
    }

    // ---- surfaces ----------------------------------------------------

    /**
     * A card: the smoked sheet everything else in a screen is drawn on. Shadow, glass,
     * the sheen off its top edge, and the hairline round it.
     */
    public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        card(g, x, y, w, h, 1);
    }

    /** The same card at a real corner radius, for the one window in the client you resize by hand. */
    public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius) {
        dropShadow(g, x, y, w, h, radius);
        roundGradient(g, x, y, w, h, radius, GLASS_TOP, GLASS_BOT);
        sheen(g, x + 1, y + 1, w - 2, Math.min(h - 2, 14), Math.max(0, radius - 1));
        bevel(g, x, y, w, h, radius, lit(EDGE), EDGE, EDGE_BOT);
    }

    /**
     * The little grab mark at a resizable card's own corner: a short echo of the corner's
     * own curve, one circle inside it rather than a shape laid over the top. It shares the
     * curve's own centre and only pulls the radius in, so it reads as part of the corner
     * bending a second time rather than as a decoration sitting near it. Brighter and a
     * shade longer under the cursor or mid-drag, the same way a control's own hairline lifts
     * on hover.
     *
     * @param cx,cy      the centre the corner's own rounding sweeps round -- the same point
     *                   the classic panel already offsets each corner by its radius to reach
     * @param midDeg     where the corner's own curve is furthest from both straight edges --
     *                   45 for the bottom-right corner, and round the compass from there
     */
    public static void cornerHandle(GuiGraphicsExtractor g, double cx, double cy, double midDeg, boolean hot) {
        int color = alpha(0xFFE7E8F0, hot ? 0.9f : 0.5f);
        double radius = hot ? 5.5 : 4.5;
        double span = hot ? 34 : 26;

        arc(g, cx, cy, radius, midDeg - span / 2, midDeg + span / 2, color);
        arc(g, cx, cy, radius - 1, midDeg - span / 2, midDeg + span / 2, color);
    }

    /**
     * A band across the top of a card, for the part of it that is not any one pane's.
     * <p>
     * Barely a wash. The card already carries its own sheen down from its top edge and the
     * header sits inside it, so a band with any weight of its own lands on top of that and
     * comes out as a grey slab -- which is the one thing a sheet of glass must not do at
     * the end you are meant to be able to see through. The divider under it is what
     * separates the header from the pane; the light is only there to say which way up the
     * card is.
     */
    public static void headerBand(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        headerBand(g, x, y, w, h, 0);
    }

    /**
     * The same, with its top corners rounded to match the card it sits inside -- otherwise
     * this wash, faint as it is, still squares off a corner the glass beneath it has already
     * rounded away, which is exactly the seam a header is meant to be invisible against.
     */
    public static void headerBand(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius) {
        roundRectTop(g, x, y, w, h, radius, 0x0AFFFFFF);
        divider(g, x, y + h - 1, w);
    }

    /**
     * A sunk area: a list, a tab band, a search field. Darkens the card, takes the shadow
     * of its own near wall across the top, and catches the light along the bottom lip.
     */
    public static void well(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        well(g, x, y, w, h, WELL);
    }

    public static void well(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill) {
        well(g, x, y, w, h, fill, 1f);
    }

    /**
     * The same, at a fraction of its own opacity -- for a well inside a control or a section
     * that fades with something else rather than always being fully there.
     */
    public static void well(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, float fade) {
        if (w <= 0 || h <= 0) return;

        roundRect(g, x, y, w, h, faded(fill, fade));
        g.fillGradient(x + 1, y + 1, x + w - 1, y + 1 + Math.min(h - 2, 6),
            faded(0x42000000, fade), faded(0x00000000, fade));
        bevel(g, x, y, w, h, faded(0x45000000, fade), faded(0x26000000, fade), faded(0x1AFFFFFF, fade));
    }

    /**
     * The face every control in the panel sits on: a wash of light over the card, a
     * specular line just inside its top edge, and a hairline carrying the state.
     *
     * @param face the wash, usually {@link #LIFT} or {@link #LIFT_HOT}
     * @param edge the state's colour. It runs the whole way round at {@link #lit} strength,
     *             so a caller passes the resting value it always passed and gets the full
     *             ring at the weight the top edge alone used to carry
     */
    public static void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, int face, int edge) {
        if (w <= 0 || h <= 0) return;

        roundRect(g, x, y, w, h, face);
        if (w > 4 && h > 3) g.fill(x + 2, y + 1, x + w - 2, y + 2, LIFT_SOFT);
        ring(g, x, y, w, h, lit(edge));
    }

    /** The same under a vertical gradient, for a face that carries an accent of its own. */
    public static void pillGradient(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                    int top, int bottom, int edge) {
        if (w <= 0 || h <= 0) return;

        roundGradient(g, x, y, w, h, top, bottom);
        if (w > 4 && h > 3) g.fill(x + 2, y + 1, x + w - 2, y + 2, 0x33FFFFFF);
        ring(g, x, y, w, h, lit(edge));
    }

    /**
     * A hairline across a card, drawn as the edge between two sheets rather than as a
     * line: a dark seam with the light catching on the sheet below it.
     */
    public static void divider(GuiGraphicsExtractor g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, 0x33000000);
        g.fill(x, y + 1, x + w, y + 2, 0x14FFFFFF);
    }

    /** The 2px mark that opens a section heading, with the light on its top pixel. */
    public static void accentBar(GuiGraphicsExtractor g, int x, int y, int h, int color) {
        roundRect(g, x, y, 2, h, color);
        if (h > 2) g.fill(x, y, x + 2, y + 1, lighten(color, 0.45f));
    }

    /**
     * An 8x8 magnifier drawn from fills, so it cannot turn into a missing-glyph box
     * the way an exotic unicode character can on a stripped-down font. Hollow, rather
     * than a disc with a lighter disc punched out of it: the lens is glass too.
     */
    public static void magnifier(GuiGraphicsExtractor g, int x, int y, int color) {
        ring(g, x, y, 6, 6, color);
        g.fill(x + 5, y + 5, x + 7, y + 7, color);
    }

    /**
     * A small X drawn from fills rather than a font glyph. A single character centres on
     * its advance width, not its ink, so it lands visibly off-centre in a square this
     * tight; a shape we place ourselves lands exactly where we put it.
     */
    public static void crossMark(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        for (int i = 0; i < size; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, color);
            g.fill(x + size - 1 - i, y + i, x + size - i, y + i + 1, color);
        }
    }

    /**
     * A small pushpin drawn from fills.
     */
    public static void pinMark(GuiGraphicsExtractor g, int x, int y, int size, int color) {
        g.fill(x + 2, y, x + 6, y + 2, color);
        g.fill(x + 3, y + 2, x + 5, y + 3, color);
        g.fill(x + 1, y + 3, x + 7, y + 5, color);
        g.fill(x + 3, y + 5, x + 5, y + 7, color);
        g.fill(x + 3, y + 7, x + 4, y + 8, color);
    }

    /**
     * A filled circle, drawn as one fill per row.
     * <p>
     * There is no circle in the graphics API and no texture worth shipping for this, so it
     * is scanlines: for each row the chord half-width is {@code sqrt(r^2 - dy^2)}, and the
     * row is one rect. Rows never overlap, which matters more than it sounds -- a
     * translucent colour has to composite exactly once or the middle of the disc comes out
     * darker than its edge.
     * <p>
     * Sampled at each row's centre rather than its top, so a disc is symmetric about its own
     * centre instead of leaning half a pixel up.
     */
    public static void disc(GuiGraphicsExtractor g, double cx, double cy, double r, int color) {
        if (r <= 0) return;

        int top = (int) Math.floor(cy - r);
        int bottom = (int) Math.ceil(cy + r);

        for (int y = top; y < bottom; y++) {
            double dy = y + 0.5 - cy;
            double half = r * r - dy * dy;
            if (half <= 0) continue;

            half = Math.sqrt(half);

            int left = (int) Math.round(cx - half);
            int right = (int) Math.round(cx + half);

            if (right > left) g.fill(left, y, right, y + 1, color);
        }
    }

    /**
     * A ring of {@code thickness} px, drawn the same way and in the same one pass.
     * <p>
     * Two rects per row rather than a disc with a smaller disc over it: punching a hole with
     * a second fill only works on an opaque colour, and every colour in this theme is
     * translucent.
     */
    public static void ringCircle(GuiGraphicsExtractor g, double cx, double cy, double r,
                                  double thickness, int color) {

        if (r <= 0 || thickness <= 0) return;

        double inner = Math.max(0.0, r - thickness);
        int top = (int) Math.floor(cy - r);
        int bottom = (int) Math.ceil(cy + r);

        for (int y = top; y < bottom; y++) {
            double dy = y + 0.5 - cy;

            double outer = r * r - dy * dy;
            if (outer <= 0) continue;
            outer = Math.sqrt(outer);

            double hole = inner * inner - dy * dy;

            int left = (int) Math.round(cx - outer);
            int right = (int) Math.round(cx + outer);

            if (hole <= 0) {
                if (right > left) g.fill(left, y, right, y + 1, color);
                continue;
            }

            hole = Math.sqrt(hole);

            int holeLeft = (int) Math.round(cx - hole);
            int holeRight = (int) Math.round(cx + hole);

            if (holeLeft > left) g.fill(left, y, holeLeft, y + 1, color);
            if (right > holeRight) g.fill(holeRight, y, right, y + 1, color);
        }
    }

    /**
     * A 1px line between two points, as fills.
     * <p>
     * Bresenham, because the alternative for a cursor trace is a run of rects with gaps in
     * it wherever the path moved faster than one pixel a sample -- which is most of a reach,
     * and exactly the fast part somebody is trying to look at.
     */
    public static void line(GuiGraphicsExtractor g, double x0, double y0, double x1, double y1,
                            int color) {

        int x = (int) Math.round(x0);
        int y = (int) Math.round(y0);
        int endX = (int) Math.round(x1);
        int endY = (int) Math.round(y1);

        int dx = Math.abs(endX - x);
        int dy = -Math.abs(endY - y);
        int stepX = x < endX ? 1 : -1;
        int stepY = y < endY ? 1 : -1;
        int error = dx + dy;

        // Bounded rather than trusted to terminate: the points come from a cursor trace and
        // a NaN or a wild coordinate would otherwise be an infinite loop inside a render pass.
        for (int guard = 0; guard < 4096; guard++) {
            g.fill(x, y, x + 1, y + 1, color);

            if (x == endX && y == endY) return;

            int doubled = error * 2;

            if (doubled >= dy) {
                error += dy;
                x += stepX;
            }

            if (doubled <= dx) {
                error += dx;
                y += stepY;
            }
        }
    }

    /**
     * A short arc, as a handful of {@link #line} segments chorded between points sampled
     * evenly along it. Straight enough over the few degrees anything in this panel asks for
     * that the facets do not read as facets, without a curve primitive of its own to draw one
     * properly.
     *
     * @param fromDeg,toDeg the span, in degrees, 0 pointing right and increasing clockwise
     */
    public static void arc(GuiGraphicsExtractor g, double cx, double cy, double r,
                           double fromDeg, double toDeg, int color) {
        int steps = 6;

        double prevX = cx + r * Math.cos(Math.toRadians(fromDeg));
        double prevY = cy + r * Math.sin(Math.toRadians(fromDeg));

        for (int i = 1; i <= steps; i++) {
            double deg = fromDeg + (toDeg - fromDeg) * i / steps;
            double x = cx + r * Math.cos(Math.toRadians(deg));
            double y = cy + r * Math.sin(Math.toRadians(deg));

            line(g, prevX, prevY, x, y, color);
            prevX = x;
            prevY = y;
        }
    }

    /** Vertical scrollbar track and thumb. Draws nothing when everything fits. */
    public static void scrollbar(GuiGraphicsExtractor g, int x, int y, int h,
                                 int total, int visible, int offset) {
        if (total <= visible || h <= 0) return;

        roundRect(g, x, y, 3, h, WELL_DEEP);

        int thumbH = Math.max(12, h * visible / total);
        int span = h - thumbH;
        int maxOffset = total - visible;
        int thumbY = y + (maxOffset <= 0 ? 0 : span * offset / maxOffset);

        roundRect(g, x, thumbY, 3, thumbH, 0x59FFFFFF);
        g.fill(x, thumbY, x + 3, thumbY + 1, 0x8CFFFFFF);
    }

    // ---- slot chips --------------------------------------------------

    /** The three states a slot chip can be in. */
    public enum Tone {
        /** Has a rule and is currently satisfied. */
        GOLD(Theme.GOLD, GOLD_MID, GOLD_DEEP, INK),
        /** Has a rule and is waiting to be refilled. */
        RED(Theme.RED, RED_MID, RED_DEEP, INK),
        /** No rule. Glass, so an unused slot reads as empty rather than as switched off. */
        SLATE(Theme.SLATE, SLATE_MID, SLATE_DEEP, TEXT_DIM);

        public final int light;
        public final int mid;
        public final int deep;

        /** What a label on the chip's own face has to be drawn in to survive it. */
        public final int ink;

        Tone(int light, int mid, int deep, int ink) {
            this.light = light;
            this.mid = mid;
            this.deep = deep;
            this.ink = ink;
        }
    }

    /**
     * A slot chip drawn as a shallow prism: a lit top face on a darker front wall.
     * A selected chip sinks toward its baseline while keeping the same footprint, so a
     * row of chips never shifts as states change.
     */
    public static void slotChip(GuiGraphicsExtractor g, int x, int y, int w, int capH, int depth,
                                boolean pressed, Tone tone, boolean glow) {
        int baseline = y + capH + depth;
        int capTop = pressed ? y + depth - 1 : y;
        int capBottom = capTop + capH;

        // Contact shadow. Tight to the baseline, because the chip is a few pixels off the
        // card rather than floating over it.
        roundRect(g, x - 1, baseline - 1, w + 2, 2, 0x4A000000);

        // The front wall, from the bottom of the cap down to the baseline. Drawn as one
        // ramp rather than a fill with a gradient laid back over it: the tones are glass
        // now, and a second pass over the same pixels would be a second wall.
        int wallH = baseline - capBottom + 2;

        if (wallH > 0) {
            roundGradient(g, x, capBottom - 2, w, wallH, shade(tone.deep, 0.10f), shade(tone.deep, 0.45f));
        }

        // The cap, inside its own hairline.
        roundRect(g, x, capTop, w, capH, shade(tone.deep, 0.25f));
        roundGradient(g, x + 1, capTop + 1, w - 2, capH - 2,
            pressed ? tone.mid : tone.light,
            pressed ? shade(tone.mid, 0.28f) : tone.mid);

        if (capH > 3 && w > 4) {
            g.fill(x + 2, capTop + 1, x + w - 2, capTop + 2, tint(tone.light, pressed ? 0.12f : 0.42f));
        }

        if (glow) {
            ring(g, x - 1, capTop - 1, w + 2, (baseline - capTop) + 1, alpha(tone.light, 0.55f));
        }
    }

    /** Y offset of a chip's top face, so callers can centre a label on it. */
    public static int chipFaceTop(int y, int depth, boolean pressed) {
        return pressed ? y + depth - 1 : y;
    }

    /** A tiny "!" mark, built from fills like the rest of the icon set. Stem then dot. */
    public static void bang(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x, y, x + 2, y + 5, color);
        g.fill(x, y + 6, x + 2, y + 8, color);
    }

    /**
     * The old client mark: a tall gold bar beside a shorter one, reading as a
     * downward stream.
     */
    public static void logoMark(GuiGraphicsExtractor g, int x, int y, int h) {
        roundRect(g, x, y, 3, h, GOLD);
        roundRect(g, x + 5, y + h / 3, 3, h - h / 3, GOLD_MID);
        g.fill(x, y, x + 3, y + 2, lighten(GOLD, 0.4f));
        g.fill(x + 5, y + h / 3, x + 8, y + h / 3 + 1, lighten(GOLD_MID, 0.35f));
    }

    public static int logoWidth() {
        return 8;
    }
}
