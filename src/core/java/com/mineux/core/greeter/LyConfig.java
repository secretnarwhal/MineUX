package com.mineux.core.greeter;

import java.util.Locale;
import java.util.Map;

/**
 * The greeter's dotfile, {@code dotfiles/ly/config.ini}, after ly's own
 * {@code /etc/ly/config.ini}: which animation plays behind the box, the colour of the stream
 * and the tint it leaves, the window, and which MineUX server to sign in to.
 * <p>
 * Read fresh every time the greeter opens, so an edit -- by hand or with the terminal's
 * {@code dotfiles} command -- shows the next time it does.
 */
public final class LyConfig {

    public static final String STREAM = "stream";
    public static final String DOOM = "doom";
    public static final String ASCII = "ascii";
    public static final String NONE = "none";
    public static final String MAXIMIZE = "maximize";
    public static final String KEEP = "keep";

    public static final int DEFAULT_COLOR = 0xF2B825;
    public static final float DEFAULT_TINT = 0.22f;
    public static final float DEFAULT_X = 0.18f;

    static final String TEMPLATE = """
        # the MineUX greeter, after ly's /etc/ly/config.ini
        # edit by hand, or in the terminal: dotfiles ly <key>=<value>

        # what plays behind the login box: stream (the pour), doom (the pixel fire),
        # ascii (a wall of fire in text) or none
        animation = stream

        # the ascii fire's colours: classic, blue, lava, matrix or gold
        ascii_palette = classic

        # the colour of the stream, and of the tint it leaves on the screen (#RRGGBB)
        stream_color = #F2B825

        # how strong the tint that stays is, 0 (none) to 1 (solid)
        tint_opacity = 0.22

        # where across the screen the stream falls, 0 (left) to 1 (right)
        stream_x = 0.18

        # the game is left as it is (keep), or goes windowed and maximised (maximize)
        window = keep

        # the MineUX server's API address, e.g. https://mineux.com/api; empty for the built-in one
        # (the system property -Dmineux.api wins over this)
        api =
        """;

    /** Every key, then what it takes, in pairs (flat: no arrays of arrays), for the terminal to list. */
    public static final String[] KEYS = {
        "animation", "stream, doom, ascii or none",
        "ascii_palette", "classic, blue, lava, matrix or gold",
        "stream_color", "#RRGGBB",
        "tint_opacity", "0 to 1",
        "stream_x", "0 (left) to 1 (right)",
        "window", "keep or maximize",
        "api", "https://host/api, or empty for the built-in server",
    };

    public final String animation;
    public final int streamColor;
    public final float tintOpacity;
    public final float streamX;
    public final String window;
    public final String asciiPalette;
    /** The API base the dotfile asks for; empty for the built-in one. */
    public final String api;

    private LyConfig(String animation, int streamColor, float tintOpacity, float streamX, String window, String asciiPalette, String api) {
        this.animation = animation;
        this.streamColor = streamColor;
        this.tintOpacity = tintOpacity;
        this.streamX = streamX;
        this.window = window;
        this.asciiPalette = asciiPalette;
        this.api = api;
    }

    /** {@code config/mineux/dotfiles/ly/config.ini}. */
    public static Dotfile file() {
        return Dotfile.of("ly/config.ini", TEMPLATE);
    }

    /** The host's file as it is now. */
    public static LyConfig load() {
        return load(file());
    }

    /** The file as it is now; anything missing or unreadable falls back to its default. */
    public static LyConfig load(Dotfile file) {
        Map<String, String> values = file.read();
        String animation = animation(values.get("animation"));
        if (animation == null) animation = STREAM;
        String palette = lower(values.get("ascii_palette"), "classic");
        if (!AsciiFire.isPalette(palette)) palette = "classic";
        Integer color = parseColor(values.get("stream_color"));
        Float tint = parseFraction(values.get("tint_opacity"));
        Float x = parseFraction(values.get("stream_x"));
        // Anything but maximize is keep.
        String asked = window(values.get("window"));
        String window = asked != null && asked.equals(MAXIMIZE) ? MAXIMIZE : KEEP;
        String api = values.get("api");
        return new LyConfig(animation, color == null ? DEFAULT_COLOR : color, tint == null ? DEFAULT_TINT : tint,
            x == null ? DEFAULT_X : x, window, palette, api == null ? "" : api.trim());
    }

    /** Checks and writes one key of the host's file; see {@link #set(Dotfile, String, String)}. */
    public static String set(String key, String value) {
        return set(file(), key, value);
    }

    /**
     * Checks and writes one key.
     *
     * @return why the value was refused, or null once it is written
     */
    public static String set(Dotfile file, String key, String value) {
        String k = canonicalKey(key);
        String v = check(k, value.trim());
        if (v == null) return "ly has no key '" + key + "'";
        if (v.startsWith("!")) return v.substring(1);
        file.write(k, v);
        return null;
    }

    /** The value as it is written, "!" and why it is refused, or null for a key ly lacks. */
    private static String check(String k, String v) {
        if (k.equals("animation")) {
            String a = animation(v);
            return a == null ? "!animation is stream, doom, ascii or none" : a;
        }
        if (k.equals("stream_color")) {
            Integer color = parseColor(v);
            return color == null ? "!stream_color is a colour like #F2B825" : String.format(Locale.ROOT, "#%06X", color);
        }
        if (k.equals("ascii_palette")) {
            String p = v.toLowerCase(Locale.ROOT);
            return AsciiFire.isPalette(p) ? p : "!ascii_palette is " + String.join(", ", AsciiFire.PALETTE_NAMES);
        }
        if (k.equals("window")) {
            String w = window(v);
            return w == null ? "!window is keep or maximize" : w;
        }
        if (k.equals("tint_opacity") || k.equals("stream_x")) {
            Float f = parseFraction(v);
            return f == null ? "!" + k + " is a number from 0 to 1" : String.valueOf(f);
        }
        if (k.equals("api")) {
            if (v.isEmpty()) return v;
            String lower = v.toLowerCase(Locale.ROOT);
            return lower.startsWith("https://") || lower.startsWith("http://") ? v : "!api is an address like https://mineux.com/api";
        }
        return null;
    }

    private static String animation(String text) {
        if (text == null) return null;
        String v = text.trim().toLowerCase(Locale.ROOT);
        if (v.equals("fire")) return DOOM;
        if (v.equals(STREAM) || v.equals(DOOM) || v.equals(ASCII) || v.equals(NONE)) return v;
        return null;
    }

    private static String window(String text) {
        if (text == null) return null;
        String v = text.trim().toLowerCase(Locale.ROOT);
        if (v.startsWith("maximi")) return MAXIMIZE;
        return v.equals(KEEP) ? KEEP : null;
    }

    private static String lower(String text, String fallback) {
        return text == null ? fallback : text.trim().toLowerCase(Locale.ROOT);
    }

    /** The key as the file spells it; {@code color} and {@code colour} are stream_color. */
    public static String canonicalKey(String key) {
        String k = key.trim().toLowerCase(Locale.ROOT);
        return k.equals("color") || k.equals("colour") ? "stream_color" : k;
    }

    /** {@code #RRGGBB}, {@code RRGGBB} or {@code 0xRRGGBB}; null for anything else. */
    public static Integer parseColor(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.startsWith("#")) t = t.substring(1);
        else if (t.toLowerCase(Locale.ROOT).startsWith("0x")) t = t.substring(2);
        if (t.length() != 6) return null;
        try {
            return Integer.parseInt(t, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Float parseFraction(String text) {
        if (text == null) return null;
        try {
            float f = Float.parseFloat(text.trim());
            return Float.isFinite(f) ? Math.max(0f, Math.min(1f, f)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
