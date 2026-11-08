package com.mineux.core.ui.term;

import com.mineux.core.greeter.Dotfile;
import com.mineux.core.util.Words;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@code dotfiles/hypr/hyprland.conf}: the desktop's window manager, configured as Hyprland
 * is -- gaps, borders, animations and their curves, the layout, and every key the window
 * manager answers to.
 * <p>
 * Hyprland's own syntax, as far as the desktop has use for it: {@code section { key = value }}
 * blocks (and the flat {@code section:key = value}), {@code $variables}, {@code #} comments,
 * colours as {@code rgba(rrggbbaa)}, {@code rgb(rrggbb)} or {@code 0xaarrggbb} with an optional
 * angle for a gradient, and repeated {@code bezier}, {@code animation} and {@code bind} lines.
 * <p>
 * The file is watched: a save is picked up within a second, as Hyprland picks up its own. A
 * missing file is written out from the template in the jar's resources; a line that does
 * not make sense is skipped and listed by {@code hyprctl configerrors}.
 */
final class HyprConf {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/hypr");

    static final String FILE = "hypr/hyprland.conf";
    private static final String RESOURCE = "/mineux/hyprland.conf";

    static final String COLUMNS = "columns", DWINDLE = "dwindle";
    /** Gaps past this leave no room for a window at a large GUI scale. */
    private static final int MAX_GAP = 60;

    /** A bind's modifiers, as written; turned into GLFW's with the SUPER key of the moment. */
    static final int M_SUPER = 1, M_SHIFT = 2, M_CTRL = 4, M_ALT = 8;
    /** The scroll wheel, as keys: {@code mouse_down} and {@code mouse_up}. */
    static final int SCROLL_DOWN = -300, SCROLL_UP = -301;

    static final class Bind {
        final int mods;
        final int key;
        final String keys;
        final String dispatcher;
        final String arg;
        /**
         * Whether it fires again while its keys are held ({@code binde}), rather than once a
         * press ({@code bind}): SUPER+M held must not flip the window a dozen times a second.
         */
        final boolean repeat;

        Bind(int mods, int key, String keys, String dispatcher, String arg, boolean repeat) {
            this.mods = mods;
            this.key = key;
            this.keys = keys;
            this.dispatcher = dispatcher;
            this.arg = arg;
            this.repeat = repeat;
        }
    }

    static final class Anim {
        boolean on = true;
        float speed = 10f;
        String curve = "default";
        String style = "";
    }

    /** A border colour: one colour, or a gradient across the window at an angle. */
    static final class Gradient {
        final int[] colors;
        final float angle;

        Gradient(int[] colors, float angle) {
            this.colors = colors;
            this.angle = angle;
        }
    }

    // ---- the values ---------------------------------------------------------
    int gapsIn = 3, gapsOut = 6, borderSize = 1;
    Gradient activeBorder = new Gradient(new int[] {0xEEEFC648, 0xEEFF8C42}, 45f);
    Gradient inactiveBorder = new Gradient(new int[] {0x38FFFFFF}, 0f);
    Gradient groupActive = new Gradient(new int[] {0xEE5AF78E}, 0f);
    Gradient groupInactive = new Gradient(new int[] {0x405AF78E}, 0f);
    String layout = COLUMNS;
    boolean dimInactive;
    float dimStrength = 0.15f;
    float dimSpecial = 0.3f;
    boolean animations = true;
    boolean preserveSplit;
    int forceSplit;
    final Map<String, float[]> beziers = new HashMap<>();
    final Map<String, Anim> anims = new HashMap<>();
    final List<Bind> binds = new ArrayList<>();
    final List<String> errors = new ArrayList<>();

    // ---- loading ------------------------------------------------------------

    private static volatile HyprConf current;
    private static volatile long checkedAt;
    private static long modified = Long.MIN_VALUE;
    /**
     * {@code hyprctl keyword}: set on top of the file until the game closes, in the order given,
     * as key and value in turn -- a list rather than a map, since {@code bind} repeats.
     */
    private static final List<String> KEYWORDS = new ArrayList<>();

    /**
     * {@link String#hashCode} of every template shipped before this one (line ends and the ends
     * trimmed). A file still exactly as one of them was written has never been edited, and is
     * brought up to date; a file anyone has touched is left alone. <b>Whenever the resource
     * changes, add the hash of the one it replaces here</b> (six earlier templates so far, the last the
     * one before SUPER+K/L became opacity).
     */
    private static final int[] OLD_TEMPLATES = {1179599606, 1090814717, 1573159436, 1446456213, 231627537, -1976879157};

    /**
     * The self-test's configuration: what {@link #get} returns until given null, with no file
     * read or written ({@link TermProbe}). Never called in game.
     */
    static synchronized void useForTest(HyprConf conf) {
        current = conf;
        checkedAt = conf == null ? 0L : Long.MAX_VALUE / 2;
    }

    static Path path() {
        return Dotfile.directory().resolve(FILE);
    }

    /**
     * The configuration as the file has it now; checked for a save at most once a second. Read
     * many times a frame, so the usual answer takes no lock.
     */
    static HyprConf get() {
        HyprConf conf = current;
        if (conf != null && System.currentTimeMillis() - checkedAt < 1000) return conf;
        return check();
    }

    private static synchronized HyprConf check() {
        long now = System.currentTimeMillis();
        HyprConf conf = current;
        if (conf != null && now - checkedAt < 1000) return conf;
        checkedAt = now;
        long stamp = stamp();
        if (conf == null || stamp != modified) return reload();
        return conf;
    }

    static synchronized HyprConf reload() {
        Path file = path();
        String text = null;
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, template(), StandardCharsets.UTF_8);
            }
            text = Files.readString(file, StandardCharsets.UTF_8);
            if (untouchedOldTemplate(text)) {
                text = template();
                Files.writeString(file, text, StandardCharsets.UTF_8);
                LOGGER.info("{} was an unedited older template; brought up to date", file);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read {}; using the defaults", file, e);
        }
        modified = stamp();
        HyprConf conf = new HyprConf();
        conf.parse(text != null ? text : template());
        for (int i = 0; i + 1 < KEYWORDS.size(); i += 2) {
            try {
                String error = conf.apply(KEYWORDS.get(i), KEYWORDS.get(i + 1));
                if (error != null) conf.errors.add("keyword " + KEYWORDS.get(i) + ": " + error);
            } catch (RuntimeException e) {
                conf.errors.add("keyword " + KEYWORDS.get(i) + ": " + e);
            }
        }
        current = conf;
        return conf;
    }

    /** {@code hyprctl keyword}: null once set, or why not. */
    static synchronized String keyword(String key, String value) {
        String k = key.toLowerCase(Locale.ROOT);
        String error;
        try {
            error = new HyprConf().apply(k, value);
        } catch (RuntimeException e) {
            error = String.valueOf(e);
        }
        if (error != null) return error;
        KEYWORDS.add(k);
        KEYWORDS.add(value);
        reload();
        return null;
    }

    static boolean untouchedOldTemplate(String text) {
        String normal = text.replace("\r\n", "\n").strip();
        // Never the template shipped now, whatever the list says: that would rewrite it every second.
        if (normal.equals(template().replace("\r\n", "\n").strip())) return false;
        int hash = normal.hashCode();
        for (int old : OLD_TEMPLATES) if (old == hash) return true;
        return false;
    }

    private static long stamp() {
        try {
            Path file = path();
            return Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : -1L;
        } catch (IOException | RuntimeException e) {
            return -1L;
        }
    }

    static String template() {
        try (InputStream in = HyprConf.class.getResourceAsStream(RESOURCE)) {
            if (in != null) return shellNamed(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.warn("Could not read {}", RESOURCE, e);
        }
        return "";
    }

    /** The template's terminal binds under the host's shell name ({@code exec, muxsh} as MineUX ships it). */
    static String shellNamed(String text) {
        String shell = com.mineux.core.MineUX.shellName();
        return Objects.equals("muxsh", shell) ? text : text.replace("exec, muxsh", "exec, " + shell);
    }

    /** Whether {@code exec}'s argument opens a terminal by the shell's name: MineUX's, or the host's. */
    static boolean opensShell(String arg) {
        String lower = arg.toLowerCase(Locale.ROOT);
        return lower.startsWith("muxsh") || lower.startsWith(com.mineux.core.MineUX.shellName().toLowerCase(Locale.ROOT));
    }

    // ---- parsing ------------------------------------------------------------

    private static final Set<String> W_BIND = Words.of("bind binde bindr bindl bindel bindn");
    private static final Set<String> W_UNBIND = Words.of("unbind");

    /**
     * Reads the file's text into this configuration. A line it cannot use is skipped and listed
     * in {@link #errors}; nothing a hand-edited file says may throw out of here, since the
     * desktop reads the configuration every frame and an exception there would close it.
     */
    void parse(String text) {
        Map<String, String> vars = new HashMap<>();
        List<String> sections = new ArrayList<>();
        String[] lines = text.split("\n");
        for (int n = 0; n < lines.length; n++) {
            try {
                line(lines[n], n + 1, vars, sections);
            } catch (RuntimeException e) {
                errors.add("line " + (n + 1) + ": " + e);
            }
        }
    }

    private void line(String raw, int number, Map<String, String> vars, List<String> sections) {
        String line = raw;
        int hash = line.indexOf('#');
        if (hash >= 0) line = line.substring(0, hash);
        line = line.trim();
        if (line.isEmpty()) return;

        if (line.endsWith("{")) {
            sections.add(line.substring(0, line.length() - 1).trim().toLowerCase(Locale.ROOT));
            return;
        }
        if (line.equals("}")) {
            if (!sections.isEmpty()) sections.remove(sections.size() - 1);
            return;
        }

        int eq = line.indexOf('=');
        if (eq <= 0) {
            errors.add("line " + number + ": expected key = value");
            return;
        }
        String key = line.substring(0, eq).trim();
        String value = line.substring(eq + 1).trim();
        if (key.startsWith("$")) {
            vars.put(key, substitute(value, vars));
            return;
        }
        value = substitute(value, vars);
        String full = key.toLowerCase(Locale.ROOT);
        if (!sections.isEmpty()) full = String.join(":", sections) + ":" + full;
        String error = apply(full, value);
        if (error != null) errors.add("line " + number + ": " + error);
    }

    private static String substitute(String value, Map<String, String> vars) {
        if (value.indexOf('$') < 0) return value;
        // Longest names first, so $mainModShift is not read as $mainMod followed by "Shift".
        List<String> names = new ArrayList<>(vars.keySet());
        names.sort((a, b) -> b.length() - a.length());
        for (String name : names) value = value.replace(name, vars.get(name));
        return value;
    }

    /** One key; null when it took, or what was wrong with it. */
    String apply(String key, String value) {
        try {
            // The letters after "bind" are Hyprland's flags; e is the one that repeats while held.
            if (W_BIND.contains(key)) return bind(value, key.indexOf('e', 4) >= 0);
            if (W_UNBIND.contains(key)) return unbind(value);
            if (key.startsWith("general:")) return general(key.substring(8), value);
            if (key.startsWith("decoration:")) return decoration(key.substring(11), value);
            if (key.startsWith("animations:")) return animations(key.substring(11), value);
            if (key.startsWith("dwindle:")) return dwindle(key.substring(8), value);
            if (key.startsWith("group:")) return group(key.substring(6), value);
            return "unknown key " + key;
        } catch (NumberFormatException e) {
            return key + ": not a number (" + value + ")";
        }
    }

    private String general(String key, String value) {
        if (Objects.equals("gaps_in", key)) {
            gapsIn = Math.max(0, Math.min(MAX_GAP, firstInt(value)));
        } else if (Objects.equals("gaps_out", key)) {
            gapsOut = Math.max(0, Math.min(MAX_GAP, firstInt(value)));
        } else if (Objects.equals("border_size", key)) {
            borderSize = Math.max(0, Math.min(8, firstInt(value)));
        } else if (Objects.equals("col.active_border", key)) {
            Gradient g = gradient(value);
            if (g == null) return "col.active_border: expected colours like rgba(efc648ee)";
            activeBorder = g;
        } else if (Objects.equals("col.inactive_border", key)) {
            Gradient g = gradient(value);
            if (g == null) return "col.inactive_border: expected colours like rgba(ffffff38)";
            inactiveBorder = g;
        } else if (Objects.equals("layout", key)) {
            String v = value.toLowerCase(Locale.ROOT);
            if (!Objects.equals(COLUMNS, v) && !Objects.equals(DWINDLE, v)) return "layout is columns or dwindle";
            layout = v;
        } else {
            return "unknown key general:" + key;
        }
        return null;
    }

    private String decoration(String key, String value) {
        if (Objects.equals("dim_inactive", key)) {
            dimInactive = bool(value);
        } else if (Objects.equals("dim_strength", key)) {
            dimStrength = clamp01(Float.parseFloat(value));
        } else if (Objects.equals("dim_special", key)) {
            dimSpecial = clamp01(Float.parseFloat(value));
        } else {
            return "unknown key decoration:" + key;
        }
        return null;
    }

    private String dwindle(String key, String value) {
        if (Objects.equals("preserve_split", key)) {
            preserveSplit = bool(value);
        } else if (Objects.equals("force_split", key)) {
            forceSplit = Math.max(0, Math.min(2, Integer.parseInt(value.trim())));
        } else {
            return "unknown key dwindle:" + key;
        }
        return null;
    }

    private String group(String key, String value) {
        Gradient g = gradient(value);
        if (Objects.equals("col.border_active", key)) {
            if (g == null) return "col.border_active: expected colours";
            groupActive = g;
        } else if (Objects.equals("col.border_inactive", key)) {
            if (g == null) return "col.border_inactive: expected colours";
            groupInactive = g;
        } else {
            return "unknown key group:" + key;
        }
        return null;
    }

    private String animations(String key, String value) {
        if (Objects.equals("enabled", key)) {
            animations = bool(value);
            return null;
        }
        if (Objects.equals("bezier", key)) {
            String[] parts = value.split(",");
            if (parts.length != 5) return "bezier = name, x0, y0, x1, y1";
            float[] p = new float[4];
            for (int i = 0; i < 4; i++) {
                p[i] = Float.parseFloat(parts[i + 1].trim());
                if (!Float.isFinite(p[i])) return "bezier: control points are numbers";
            }
            beziers.put(parts[0].trim(), p);
            return null;
        }
        if (Objects.equals("animation", key)) {
            String[] parts = value.split(",");
            if (parts.length < 2) return "animation = name, on, speed, curve[, style]";
            Anim anim = new Anim();
            anim.on = bool(parts[1]);
            if (parts.length > 2) {
                float speed = Float.parseFloat(parts[2].trim());
                if (!Float.isFinite(speed)) return "animation: speed is a number of 100ms steps";
                anim.speed = Math.max(0.01f, Math.min(1000f, speed));
            }
            if (parts.length > 3) anim.curve = parts[3].trim();
            if (parts.length > 4) anim.style = parts[4].trim().toLowerCase(Locale.ROOT);
            anims.put(parts[0].trim(), anim);
            return null;
        }
        return "unknown key animations:" + key;
    }

    private String bind(String value, boolean repeat) {
        String[] parts = value.split(",", 4);
        if (parts.length < 3) return "bind = MODS, key, dispatcher, argument";
        int mods = mods(parts[0]);
        if (mods < 0) return "bind: unknown modifier in '" + parts[0].trim() + "'";
        int key = keyCode(parts[1].trim());
        if (key == GLFW.GLFW_KEY_UNKNOWN) return "bind: unknown key '" + parts[1].trim() + "'";
        String dispatcher = parts[2].trim().toLowerCase(Locale.ROOT);
        if (!Hypr.known(dispatcher)) return "bind: unknown dispatcher '" + dispatcher + "'";
        String arg = parts.length > 3 ? parts[3].trim() : "";
        // A later bind for the same keys replaces the earlier, so a user's line overrides a default.
        removeBind(mods, key);
        binds.add(new Bind(mods, key, keysText(parts[0], parts[1]), dispatcher, arg, repeat));
        return null;
    }

    private String unbind(String value) {
        String[] parts = value.split(",", 3);
        if (parts.length < 2) return "unbind = MODS, key";
        int mods = mods(parts[0]);
        int key = keyCode(parts[1].trim());
        if (mods < 0 || key == GLFW.GLFW_KEY_UNKNOWN) return "unbind: unknown keys";
        removeBind(mods, key);
        return null;
    }

    private void removeBind(int mods, int key) {
        binds.removeIf(b -> b.mods == mods && b.key == key);
    }

    private static String keysText(String mods, String key) {
        String m = mods.trim().replace('_', ' ').replaceAll("\\s+", "+");
        return m.isEmpty() ? key.trim() : m + "+" + key.trim();
    }

    private static final Set<String> W_SUPER = Words.of("SUPER WIN META MOD4 LOGO");
    private static final Set<String> W_SHIFT = Words.of("SHIFT");
    private static final Set<String> W_CTRL = Words.of("CTRL CONTROL");
    private static final Set<String> W_ALT = Words.of("ALT MOD1");

    /** A bind's modifiers, or -1 for one it does not know. */
    static int mods(String text) {
        int mods = 0;
        for (String word : text.trim().toUpperCase(Locale.ROOT).split("[\\s_&+]+")) {
            if (word.isEmpty()) continue;
            if (W_SUPER.contains(word)) mods |= M_SUPER;
            else if (W_SHIFT.contains(word)) mods |= M_SHIFT;
            else if (W_CTRL.contains(word)) mods |= M_CTRL;
            else if (W_ALT.contains(word)) mods |= M_ALT;
            else return -1;
        }
        return mods;
    }

    /** A key as a bind names it, or GLFW's unknown key. Keyboard keys and the scroll wheel only. */
    static int keyCode(String name) {
        String k = name.toLowerCase(Locale.ROOT);
        if (Objects.equals("mouse_down", k)) return SCROLL_DOWN;
        if (Objects.equals("mouse_up", k)) return SCROLL_UP;
        if (Objects.equals("return", k)) k = "enter";
        else if (Objects.equals("bracketleft", k)) k = "lbracket";
        else if (Objects.equals("bracketright", k)) k = "rbracket";
        else if (Objects.equals("equal", k)) k = "equals";
        else if (Objects.equals("escape", k)) return GLFW.GLFW_KEY_ESCAPE;
        else if (Objects.equals("kp_enter", k) || Objects.equals("kp_return", k)) return GLFW.GLFW_KEY_KP_ENTER;
        else if (Objects.equals("prior", k)) k = "pgup";
        else if (Objects.equals("next", k)) k = "pgdn";
        Integer code = Setting.parseKey(k);
        if (code == null || code <= Setting.MOUSE_BASE) return GLFW.GLFW_KEY_UNKNOWN;
        return code;
    }

    // ---- values -------------------------------------------------------------

    private static int firstInt(String value) {
        String[] parts = value.trim().split("[\\s,]+");
        return Integer.parseInt(parts[0]);
    }

    private static final Set<String> W_TRUE = Words.of("1 true yes on");

    private static boolean bool(String value) {
        return W_TRUE.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** Colours and an optional angle, or null for anything that is not. */
    static Gradient gradient(String value) {
        List<Integer> colors = new ArrayList<>();
        float angle = 0f;
        for (String token : tokens(value)) {
            String t = token.toLowerCase(Locale.ROOT);
            if (t.endsWith("deg")) {
                try {
                    angle = Float.parseFloat(t.substring(0, t.length() - 3));
                } catch (NumberFormatException e) {
                    return null;
                }
                continue;
            }
            Integer color = color(t);
            if (color == null) return null;
            colors.add(color);
        }
        if (colors.isEmpty()) return null;
        int[] out = new int[colors.size()];
        for (int i = 0; i < out.length; i++) out[i] = colors.get(i);
        return new Gradient(out, angle);
    }

    /** Space-separated, but not inside brackets: {@code rgba(255, 0, 0, 1)} is one. */
    private static List<String> tokens(String value) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '(') depth++;
            if (c == ')') depth--;
            if ((c == ' ' || c == '\t') && depth <= 0) {
                if (cur.length() > 0) out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    /** One colour as ARGB: {@code rgba(rrggbbaa)}, {@code rgb(rrggbb)}, their decimal forms, or {@code 0xaarrggbb}. */
    static Integer color(String t) {
        try {
            if (t.startsWith("0x")) return (int) Long.parseLong(t.substring(2), 16);
            boolean rgba = t.startsWith("rgba(");
            if (!rgba && !t.startsWith("rgb(") || !t.endsWith(")")) return null;
            String inner = t.substring(rgba ? 5 : 4, t.length() - 1).trim();
            if (inner.indexOf(',') >= 0) {
                String[] p = inner.split(",");
                if (p.length != (rgba ? 4 : 3)) return null;
                int r = Integer.parseInt(p[0].trim()), g = Integer.parseInt(p[1].trim()), b = Integer.parseInt(p[2].trim());
                int a = rgba ? Math.round(clamp01(Float.parseFloat(p[3].trim())) * 255f) : 255;
                return a << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF;
            }
            if (rgba && inner.length() == 8) {
                long v = Long.parseLong(inner, 16);
                return (int) ((v & 0xFF) << 24 | v >>> 8);
            }
            if (inner.length() == 6) return 0xFF000000 | Integer.parseInt(inner, 16);
            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- animations ---------------------------------------------------------

    /** Each animation and the one it falls back to when the file leaves it out, as Hyprland's tree has them. */
    private static final Map<String, String> PARENT = pairs(
        "windowsIn windows windowsOut windows windowsMove windows windows global fadeIn fade fadeOut fade "
            + "fadeDim fade fade global border global borderangle global workspaces global "
            + "specialWorkspace workspaces");

    private static Map<String, String> pairs(String words) {
        String[] w = words.split(" ");
        Map<String, String> out = new HashMap<>();
        for (int i = 0; i + 1 < w.length; i += 2) out.put(w[i], w[i + 1]);
        return out;
    }

    private static final Anim GLOBAL = new Anim();

    Anim anim(String name) {
        for (String n = name; n != null; n = PARENT.get(n)) {
            Anim a = anims.get(n);
            if (a != null) return a;
        }
        return GLOBAL;
    }

    /** How long an animation runs, in ms; 0 when it is off. */
    long ms(String name) {
        if (!animations) return 0L;
        Anim a = anim(name);
        return a.on ? (long) (a.speed * 100f) : 0L;
    }

    /** Where an animation is at {@code t}, 0 to 1, along its curve. */
    float curve(String name, float t) {
        return Bezier.at(bezier(anim(name).curve), t);
    }

    float[] bezier(String name) {
        float[] p = beziers.get(name);
        if (p != null) return p;
        return Objects.equals("linear", name) ? Bezier.LINEAR : Bezier.DEFAULT;
    }

    String style(String name) {
        return anim(name).style;
    }

    /** The percentage in a style such as {@code popin 80%}, as a fraction; {@code fallback} when it has none. */
    float percent(String name, float fallback) {
        String style = style(name);
        int pct = style.indexOf('%');
        if (pct < 0) return fallback;
        int start = pct;
        while (start > 0 && (Character.isDigit(style.charAt(start - 1)) || style.charAt(start - 1) == '.')) start--;
        try {
            return clamp01(Float.parseFloat(style.substring(start, pct)) / 100f);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ---- binds --------------------------------------------------------------

    /** The GLFW modifier bits a bind's modifiers stand for, SUPER being whichever key {@code super} set. */
    /** The super key is handed in, not read: a static int of primitives has to be pure (tools/PureCheck.java). */
    static int glfwMods(int mods, int superBit) {
        int out = 0;
        if ((mods & M_SUPER) != 0) out |= superBit;
        if ((mods & M_SHIFT) != 0) out |= GLFW.GLFW_MOD_SHIFT;
        if ((mods & M_CTRL) != 0) out |= GLFW.GLFW_MOD_CONTROL;
        if ((mods & M_ALT) != 0) out |= GLFW.GLFW_MOD_ALT;
        return out;
    }

    static int superBit() {
        String key = Shell.superKey();
        if (Objects.equals("ctrl", key)) return GLFW.GLFW_MOD_CONTROL;
        if (Objects.equals("win", key)) return GLFW.GLFW_MOD_SUPER;
        if (Objects.equals("shift", key)) return GLFW.GLFW_MOD_SHIFT;
        return GLFW.GLFW_MOD_ALT;
    }

    private static final int MOD_MASK = GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER;

    /** The modifiers held right now, as GLFW's bits, for input that does not carry them (the scroll wheel, polling in game). */
    /** A {@code Long}, as in {@link #leftAlt}: not a static int of primitives (tools/PureCheck.java). */
    static int heldMods(Long handle) {
        int mods = 0;
        if (down(handle, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT)) mods |= GLFW.GLFW_MOD_SHIFT;
        if (down(handle, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL)) mods |= GLFW.GLFW_MOD_CONTROL;
        // Left Alt only: see leftAlt.
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS) mods |= GLFW.GLFW_MOD_ALT;
        if (down(handle, GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER)) mods |= GLFW.GLFW_MOD_SUPER;
        return mods;
    }

    /**
     * A key or mouse event's modifiers as the window manager takes them: Alt only while the
     * left one is down. Right Alt is AltGr, and a key people bind and type with, so it is
     * neither SUPER nor Alt here and whatever is pressed with it goes on to the game or the prompt.
     */
    static int leftAlt(Long handle, int mods) {
        if ((mods & GLFW.GLFW_MOD_ALT) == 0 || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS) return mods;
        return mods & ~GLFW.GLFW_MOD_ALT;
    }

    private static boolean down(long handle, int left, int right) {
        return GLFW.glfwGetKey(handle, left) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(handle, right) == GLFW.GLFW_PRESS;
    }

    /** The keys of the first bind that opens a terminal ({@code exec, muxsh}), as written; null for none. */
    String terminalKeys() {
        for (Bind b : binds) {
            if (Objects.equals("exec", b.dispatcher) && opensShell(b.arg)) return b.keys;
        }
        return null;
    }

    /** Every key {@code hyprctl keyword} and the file take, for completion. */
    static final String KEYS = "general:gaps_in general:gaps_out general:border_size general:col.active_border "
        + "general:col.inactive_border general:layout decoration:dim_inactive decoration:dim_strength decoration:dim_special "
        + "animations:enabled animations:bezier animations:animation dwindle:preserve_split dwindle:force_split "
        + "group:col.border_active group:col.border_inactive bind unbind";

    /** The bind for a key with exactly these modifiers held, or null. */
    Bind match(int key, int glfwModifiers) {
        int held = glfwModifiers & MOD_MASK;
        for (Bind b : binds) if (b.key == key && glfwMods(b.mods, superBit()) == held) return b;
        return null;
    }

    @Override
    public String toString() {
        return "gaps " + gapsIn + "/" + gapsOut + " border " + borderSize + " " + Arrays.toString(activeBorder.colors)
            + " layout " + layout + " binds " + binds.size() + " errors " + errors.size();
    }
}
