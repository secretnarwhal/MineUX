package com.mineux.screens.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/* Dark glass look shared by the Windows screens
 *
 * Surfaces are translucent tints over the blurred world instead of opaque boxes: a card is smoked glass,
 * a control is that glass catching a little more light and a well is it catching less. Only text and
 * accents are solid. Everything is built from axis-aligned fills, so it looks the same at any GUI scale.
 *
 * Translucent fills must never overlap, two layers of the same tint are a visibly different color.
 */
public class GlassTheme {

    // Darkens the world behind the card
    public static final int SCRIM_TOP = 0x8A0A0C13;
    public static final int SCRIM_BOTTOM = 0xCC03040A;

    public static final int GLASS = 0xDE12161F;

    // Recessed areas like lists and slider tracks
    public static final int WELL = 0x4D000000;

    // Raised areas like buttons
    public static final int LIFT = 0x14FFFFFF;
    public static final int LIFT_HOT = 0x28FFFFFF;
    public static final int LIFT_SOFT = 0x0EFFFFFF;

    public static final int EDGE = 0x24FFFFFF;
    public static final int EDGE_SOFT = 0x12FFFFFF;
    public static final int EDGE_HOT = 0x6BFFFFFF;
    public static final int EDGE_DARK = 0x3D000000;

    public static final int TEXT = 0xFFF2F2F7;
    public static final int TEXT_DIM = 0xFFA2A4B2;
    public static final int TEXT_MUTED = 0xFF6D6F80;

    public static final int ACCENT = 0xFFEFC648;
    public static final int ACCENT_FAINT = 0x2EEFC648;
    public static final int ACCENT_EDGE = 0x8CEFC648;

    public static final int GREEN = 0xFF56D98D;
    public static final int RED = 0xFFF26254;
    public static final int RED_FAINT = 0x2EF26254;
    public static final int RED_EDGE = 0x8CF26254;

    // How far row `i` of a rounded corner with the given radius is inset from the side
    private static int cornerInset(int radius, int i) {
        double dy = radius - i - 0.5;
        return (int) Math.round(radius - Math.sqrt(radius * radius - dy * dy));
    }

    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;

        radius = Math.max(0, Math.min(radius, Math.min(w, h) / 2));

        g.fill(x, y + radius, x + w, y + h - radius, color);

        for (int i = 0; i < radius; i++) {
            int inset = cornerInset(radius, i);
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    // One pixel wide outline of a rounded rectangle
    public static void ring(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 1 || h <= 1) return;

        radius = Math.max(1, Math.min(radius, Math.min(w, h) / 2));

        g.fill(x + radius, y, x + w - radius, y + 1, color);
        g.fill(x + radius, y + h - 1, x + w - radius, y + h, color);
        g.fill(x, y + radius, x + 1, y + h - radius, color);
        g.fill(x + w - 1, y + radius, x + w, y + h - radius, color);

        for (int i = 0; i < radius; i++) {
            // Each corner row runs from its own inset up to where the row above it started, keeping the
            // outline closed. The first row connects to the straight edge instead.
            int inset = cornerInset(radius, i);
            int end = i == 0 ? radius : Math.max(inset + 1, cornerInset(radius, i - 1));

            g.fill(x + inset, y + i, x + end, y + i + 1, color);
            g.fill(x + w - end, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + end, y + h - i, color);
            g.fill(x + w - end, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    // The sheet of glass everything else sits on, with a soft shadow around it
    public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        ring(g, x - 2, y - 2, w + 4, h + 4, 6, 0x14000000);
        ring(g, x - 1, y - 1, w + 2, h + 2, 5, 0x30000000);

        roundRect(g, x + 1, y + 1, w - 2, h - 2, 3, GLASS);
        ring(g, x, y, w, h, 4, EDGE);
    }

    public static void well(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        roundRect(g, x + 1, y + 1, w - 2, h - 2, 1, WELL);
        ring(g, x, y, w, h, 2, EDGE_SOFT);
    }

    public static void divider(GuiGraphicsExtractor g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, EDGE_DARK);
        g.fill(x, y + 1, x + w, y + 2, EDGE_SOFT);
    }

    public static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        g.text(font, text, x, y, color, false);
    }

    // Small caps style section heading
    public static void heading(GuiGraphicsExtractor g, Font font, String text, int x, int y) {
        text(g, font, text.toUpperCase(), x, y, TEXT_MUTED);
    }

    // Shorten text to fit a width, ending in "..." when something was cut off
    public static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) return text;

        String ellipsis = "...";
        int available = width - font.width(ellipsis);
        if (available <= 0) return "";

        return font.plainSubstrByWidth(text, available) + ellipsis;
    }

}
