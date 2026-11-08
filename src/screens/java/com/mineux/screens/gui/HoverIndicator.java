package com.mineux.screens.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Marks around the crosshair that tell what clicking the screen being looked at does. */
public final class HoverIndicator {

    public static final int NONE = 0;
    /** Clicking switches to the window. */
    public static final int SWITCH = 1;
    /** Clicking picks the screen up. */
    public static final int MOVE = 2;

    public static int kind = NONE;

    private static final int COLOR = 0xFFFFFFFF;
    /** Distance of the marks from the centre of the crosshair. */
    private static final int REACH = 8;

    private HoverIndicator() {}

    public static void extract(GuiGraphicsExtractor g) {
        if (kind == NONE) return;
        int x = g.guiWidth() / 2;
        int y = g.guiHeight() / 2;
        if (kind == SWITCH) {
            // A bracket in each corner, framing the crosshair like a window
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    int cornerX = x + sx * REACH;
                    int cornerY = y + sy * REACH;
                    line(g, cornerX, cornerY, cornerX - sx * 3, cornerY);
                    line(g, cornerX, cornerY, cornerX, cornerY - sy * 3);
                }
            }
            return;
        }
        // An arrow head on each side, pointing outwards
        for (int side = -1; side <= 1; side += 2) {
            int tip = side * (REACH + 2);
            int base = side * REACH;
            line(g, x + tip, y, x + tip, y);
            line(g, x + base, y - 2, x + base, y + 2);
            line(g, x, y + tip, x, y + tip);
            line(g, x - 2, y + base, x + 2, y + base);
        }
    }

    /** A one pixel wide horizontal or vertical line, both end points included. */
    private static void line(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2) {
        g.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, COLOR);
    }
}
