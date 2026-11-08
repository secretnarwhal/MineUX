package com.mineux.visuals.ui;

/**
 * The colour picker's sums: hue, saturation and value to and from RGB, a colour setting's text
 * ({@code #rrggbb}, or {@code #aarrggbb} for one with alpha) to and from a packed int, and where
 * on a strip a cursor is.
 * <p>
 * Everything here is a pure function of its arguments, and nothing reaches Minecraft, so the tests
 * run it bare. The int-returning statics stay pure for the obfuscator's sake too (tools/PureCheck).
 * Hue, saturation and value are each 0 to 1; a hue of 1 is red again, as 0 is.
 */
public final class ColourMath {

    private ColourMath() {}

    // ---- hsv ------------------------------------------------------------------

    /** Hue, saturation and value, each 0 to 1, as 0xRRGGBB. */
    public static int hsv(float h, float s, float v) {
        float hue = h - (float) Math.floor(h);
        float sat = clamp01(s);
        float val = clamp01(v);
        float scaled = hue * 6f;
        int sector = (int) scaled;
        if (sector > 5) sector = 5;
        float f = scaled - sector;
        float p = val * (1f - sat);
        float q = val * (1f - sat * f);
        float t = val * (1f - sat * (1f - f));
        float r = val, g = t, b = p;
        if (sector == 1) {
            r = q;
            g = val;
        } else if (sector == 2) {
            r = p;
            g = val;
            b = t;
        } else if (sector == 3) {
            r = p;
            g = q;
            b = val;
        } else if (sector == 4) {
            r = t;
            g = p;
            b = val;
        } else if (sector == 5) {
            g = p;
            b = q;
        }
        return (toByte(r) << 16) | (toByte(g) << 8) | toByte(b);
    }

    /**
     * 0xRRGGBB (any alpha above is ignored) as hue, saturation and value into {@code out[0..2]}.
     * A grey has no hue and black no saturation: those come out 0, and a picker that wants to keep
     * its own hue through them keeps it itself ({@link #keepHue}).
     */
    public static void toHsv(int rgb, float[] out) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float h = 0f;
        if (delta > 1e-6f) {
            if (max == r) h = (g - b) / delta;
            else if (max == g) h = 2f + (b - r) / delta;
            else h = 4f + (r - g) / delta;
            h /= 6f;
            if (h < 0f) h += 1f;
            if (h >= 1f) h -= 1f;
        }
        out[0] = h;
        out[1] = max <= 0f ? 0f : delta / max;
        out[2] = max;
    }

    /**
     * A colour typed or picked from a list, read into a picker's {@code hsv} without losing what
     * the colour cannot say: a grey keeps the picker's hue, black its hue and saturation, so the
     * square does not jump back to red when the colour passes through either.
     */
    public static void keepHue(int rgb, float[] hsv) {
        float h = hsv[0], s = hsv[1];
        toHsv(rgb, hsv);
        if (hsv[2] <= 0f) {
            hsv[0] = h;
            hsv[1] = s;
        } else if (hsv[1] <= 0f) {
            hsv[0] = h;
        }
    }

    // ---- text ------------------------------------------------------------------

    /**
     * A colour setting's text as 0xAARRGGBB: {@code #rgb}, {@code #rrggbb} (opaque) or
     * {@code #aarrggbb}, the {@code #} optional, any case, spaces round it ignored. -1 for anything
     * else (a word such as {@code esp} that some colour settings also take, or nothing).
     */
    public static long parse(String text) {
        if (text == null) return -1L;
        String s = text.trim();
        if (s.startsWith("#")) s = s.substring(1);
        int n = s.length();
        if (n != 3 && n != 6 && n != 8) return -1L;
        long value = 0L;
        for (int i = 0; i < n; i++) {
            int digit = Character.digit(s.charAt(i), 16);
            if (digit < 0) return -1L;
            value = (value << 4) | digit;
        }
        if (n == 3) {
            long r = (value >> 8) & 0xF, g = (value >> 4) & 0xF, b = value & 0xF;
            value = (r * 17L << 16) | (g * 17L << 8) | (b * 17L);
        }
        if (n != 8) value |= 0xFF000000L;
        return value;
    }

    /** Whether the text is a colour written with its alpha: eight digits. */
    public static boolean hasAlpha(String text) {
        if (parse(text) < 0L) return false;
        String s = text.trim();
        return (s.startsWith("#") ? s.length() - 1 : s.length()) == 8;
    }

    /** 0xAARRGGBB as a setting writes it: {@code #rrggbb}, or {@code #aarrggbb} with its alpha; lower case. */
    public static String hex(int argb, boolean alpha) {
        String digits = alpha ? Long.toHexString(0x100000000L | (argb & 0xFFFFFFFFL)).substring(1)
            : Integer.toHexString(0x1000000 | (argb & 0xFFFFFF)).substring(1);
        return "#" + digits;
    }

    // ---- channels ----------------------------------------------------------------

    /** One byte of a packed colour: shift 24 alpha, 16 red, 8 green, 0 blue. */
    public static int channel(int argb, int shift) {
        return (argb >>> shift) & 0xFF;
    }

    /** A packed colour with one byte changed, the value clamped to 0-255. */
    public static int withChannel(int argb, int shift, int value) {
        int v = Math.max(0, Math.min(255, value));
        return (argb & ~(0xFF << shift)) | (v << shift);
    }

    // ---- the mouse ----------------------------------------------------------------

    /**
     * Where along a strip a cursor is, 0 at {@code start} to 1 at {@code start + length}, held at
     * the ends however far past them it goes: a drag that leaves the square keeps the edge it
     * left by, instead of jumping.
     */
    public static double along(double cursor, double start, double length) {
        if (length <= 0.0) return 0.0;
        double t = (cursor - start) / length;
        return t < 0.0 ? 0.0 : t > 1.0 ? 1.0 : t;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }

    private static int toByte(float unit) {
        int v = Math.round(unit * 255f);
        return v < 0 ? 0 : v > 255 ? 255 : v;
    }
}
