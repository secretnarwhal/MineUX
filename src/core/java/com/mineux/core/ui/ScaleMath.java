package com.mineux.core.ui;

import java.util.Locale;

/**
 * The arithmetic of the desk scale ({@link DeskScale}), with nothing of the game in it so it can
 * be tested on its own.
 * <p>
 * The desk is laid out as if the GUI scale were the desk scale {@code t}: one desk unit is
 * {@code t} framebuffer pixels, whatever the game's own GUI scale {@code s} is. It is drawn
 * through a pose scaled by {@code t / s} (a GUI unit being {@code s} pixels), so a desk unit
 * lands on exactly {@code t} whole pixels and text stays as crisp as at that GUI scale. Mouse
 * positions come in as GUI units and are mapped back to the pixel under the cursor and from
 * there to the desk unit drawn on it ({@link #toDesk}).
 */
public final class ScaleMath {

    /** The setting's value for "pick by screen size". */
    public static final String AUTO = "auto";
    /** What the desk was designed at, and what it is until set otherwise. */
    public static final int DEFAULT = 2;
    /** The largest the setting offers; auto may go higher on a very large screen. */
    public static final int MOST = 4;
    /** Auto: the desk is this many units tall, near enough, as at GUI scale 2 on 1080p. */
    static final int AUTO_ROWS = 540;
    /** The smallest desk vanilla allows a GUI scale for, which caps the scale on a small window. */
    static final int MIN_W = 320, MIN_H = 240;

    private ScaleMath() {}

    /** The setting as a number: 0 for auto, 1 to {@link #MOST}, else {@link #DEFAULT}. */
    public static int parse(String value) {
        if (value == null) return DEFAULT;
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.equals(AUTO)) return 0;
        if (v.length() != 1) return DEFAULT;
        int n = v.charAt(0) - '0';
        return n >= 1 && n <= MOST ? n : DEFAULT;
    }

    /** The setting written back as it is kept: {@code auto} or a whole number, else null for a value that is not one. */
    public static String normal(String value) {
        if (value == null) return null;
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.equals(AUTO)) return AUTO;
        int n = parse(v);
        return v.equals(String.valueOf(n)) ? v : null;
    }

    /**
     * The desk scale for a framebuffer {@code fbW} by {@code fbH} pixels: the setting's
     * ({@code setting} 1-4), or by screen height for auto (0), never so big the desk is smaller
     * than {@value #MIN_W} by {@value #MIN_H}, never under 1.
     */
    public static int pick(int setting, int fbW, int fbH) {
        int most = Math.max(1, Math.min(fbW / MIN_W, fbH / MIN_H));
        int want = setting > 0 ? setting : Math.round(fbH / (float) AUTO_ROWS);
        return Math.max(1, Math.min(most, want));
    }

    /** The desk's size along one side: {@code fb} pixels in units of {@code scale} pixels, rounded up as vanilla's GUI is. */
    public static int size(int fb, int scale) {
        int s = Math.max(1, scale);
        return (Math.max(0, fb) + s - 1) / s;
    }

    /** How much bigger a desk unit is than a GUI unit: the pose scale the desk is drawn through. */
    public static float factor(int deskScale, int guiScale) {
        return Math.max(1, deskScale) / (float) Math.max(1, guiScale);
    }

    /**
     * A position in GUI units (as the game gives the mouse) in desk units, along a side
     * {@code fb} framebuffer pixels and {@code gui} GUI units long. The exact inverse of the game's
     * own mouse sum (screen position times GUI size over window size): so the pixel the cursor is
     * on is the desk unit drawn there, even where the window is not a whole number of GUI units
     * across and the pose factor alone would be a pixel out.
     */
    public static double toDesk(double pos, int fb, int gui, int deskScale) {
        return pos * Math.max(1, fb) / ((double) Math.max(1, gui) * Math.max(1, deskScale));
    }

    /** {@link #toDesk} the other way: a desk position in GUI units. */
    public static double toGui(double desk, int fb, int gui, int deskScale) {
        return desk * Math.max(1, gui) * Math.max(1, deskScale) / Math.max(1, fb);
    }

    /** A desk position as the whole desk unit it is in (a hair of rounding error forgiven). */
    public static int deskPixel(double desk) {
        return (int) Math.floor(desk + 1e-6);
    }
}
