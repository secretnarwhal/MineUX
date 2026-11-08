package com.mineux.core.ui.term;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import static com.mineux.core.ui.term.Term.*;

/**
 * The classic panel's timing slider ({@code RangeSlider}), cut down to sit on one line of a
 * terminal listing beside the value it sets: a groove, the window between the two handles
 * filled in, and a handle at each end -- one handle for a single number.
 * <p>
 * It behaves the same way. The track is cubed, so the first couple of hundred milliseconds,
 * where every real setting lives, get most of its length; a drag rounds to the setting's
 * step, stops a handle {@code gap} short of the other rather than pushing it, is applied live,
 * and is saved once when the button comes up ({@link TerminalScreen#mouseReleased}).
 */
final class Slider {

    static final int LOW = 0, HIGH = 1;

    private static final double CURVE = 3.0;
    private static final int GRIP_W = 3;
    private static final int TRACK_H = 4;

    private Slider() {}

    /** Draws it in {@code [x0, x1)} on a line whose text starts at {@code y}. */
    static void draw(GuiGraphicsExtractor g, Setting s, int x0, int x1, int y, int hot) {
        int ty = y + 2;
        g.fill(x0, ty, x1, ty + TRACK_H, 0x50000000);
        g.fill(x0, ty, x1, ty + 1, 0x30000000);
        g.fill(x0, ty + TRACK_H - 1, x1, ty + TRACK_H, 0x18FFFFFF);

        double lo = s.low(), hi = s.high();
        if (Double.isNaN(lo) || Double.isNaN(hi)) return;
        int loX = positionOf(s, x0, x1, lo);
        int hiX = positionOf(s, x0, x1, hi);
        boolean pair = s.field2 != null;

        // The window: what the module draws its delays from.
        int from = pair ? loX : x0 + GRIP_W / 2;
        if (hiX > from) g.fill(from, ty + 1, hiX, ty + TRACK_H - 1, 0x80EFC648);

        if (pair) grip(g, loX, ty, hot == LOW);
        grip(g, hiX, ty, hot == HIGH);
    }

    private static void grip(GuiGraphicsExtractor g, int centre, int trackY, boolean lit) {
        int gx = centre - GRIP_W / 2;
        g.fill(gx - 1, trackY - 3, gx + GRIP_W + 1, trackY + TRACK_H + 3, lit ? 0xFFFFF1B8 : 0xC0000000);
        g.fill(gx, trackY - 2, gx + GRIP_W, trackY + TRACK_H + 2, GOLD);
    }

    /** Which handle a press at {@code mx} takes: the nearer, or the one it would move towards on a tie. */
    static int nearest(Setting s, int x0, int x1, double mx) {
        if (s.field2 == null) return HIGH;
        int loX = positionOf(s, x0, x1, s.low());
        int hiX = positionOf(s, x0, x1, s.high());
        double toLo = Math.abs(mx - loX), toHi = Math.abs(mx - hiX);
        if (toLo < toHi) return LOW;
        if (toHi < toLo) return HIGH;
        return mx < loX ? LOW : HIGH;
    }

    /** The held handle moved to the cursor, snapped to the step, and kept its gap from the other. */
    static void drag(Setting s, int x0, int x1, int held, double mx) {
        double floor = s.min, ceiling = s.max;
        double picked = snap(s, valueAt(s, x0, x1, mx));
        double lo = s.low(), hi = s.high();
        if (Double.isNaN(lo) || Double.isNaN(hi)) return;

        if (s.field2 == null) {
            s.write(clamp(picked, floor, ceiling), 0);
        } else if (held == HIGH) {
            s.write(lo, clamp(picked, Math.min(ceiling, lo + s.gap), ceiling));
        } else {
            s.write(clamp(picked, floor, Math.max(floor, hi - s.gap)), hi);
        }
    }

    // ---- the track ------------------------------------------------------

    private static int trackX(int x0) {
        return x0 + GRIP_W / 2 + 1;
    }

    private static int trackW(int x0, int x1) {
        return Math.max(1, x1 - x0 - GRIP_W - 2);
    }

    /**
     * Cubed for a timing ({@code slider} in modules.txt); straight for any other number, which a
     * settings window ({@link ModuleWindow}) gives a slider too.
     */
    private static double curve(Setting s) {
        return s.slider ? CURVE : 1.0;
    }

    private static double valueAt(Setting s, int x0, int x1, double mx) {
        double t = (mx - trackX(x0)) / trackW(x0, x1);
        t = Math.max(0.0, Math.min(1.0, t));
        return s.min + (s.max - s.min) * Math.pow(t, curve(s));
    }

    /** Where a value sits on a track drawn in {@code [x0, x1)}: the centre of its handle. */
    static int positionOf(Setting s, int x0, int x1, double value) {
        if (!(s.max > s.min)) return trackX(x0);
        double f = (value - s.min) / (s.max - s.min);
        f = Math.max(0.0, Math.min(1.0, f));
        return trackX(x0) + (int) Math.round(Math.pow(f, 1.0 / curve(s)) * trackW(x0, x1));
    }

    private static double snap(Setting s, double raw) {
        double step = s.dragStep();
        return Math.round(raw / step) * step;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
