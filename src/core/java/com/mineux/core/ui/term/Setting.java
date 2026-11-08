package com.mineux.core.ui.term;

import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import com.mineux.core.util.Words;

/**
 * One value the terminal can read and write: a named public field on a module's config.
 * <p>
 * Reached by reflection on the field's own name rather than through a getter and setter
 * pair per setting. That is safe here and only here because the obfuscator never
 * renames a config's field -- Gson needs every config's field names to match its JSON keys -- so the
 * name written below is the name the obfuscated class still carries. Whatever the field is
 * clamped to is the config's own business: every write goes through {@link Node#commit},
 * which runs the config's {@code ensureDefaults} before saving, so a value out of range
 * comes back as the nearest one the module will accept, exactly as a hand-edited file would.
 */
public final class Setting {

    static final int BOOL = 0, INT = 1, DOUBLE = 2, FLOAT = 3, LONG = 4, STRING = 5, KEY = 6, RANGE = 7;

    /** {@code MeleeConfig.MOUSE_BASE}: a mouse button is stored as this minus its index. */
    static final int MOUSE_BASE = -100;

    final Node node;
    final String key;
    final String field;
    final String desc;
    final Supplier<Object> owner;

    String[] aliases = new String[0];

    /** What a settings window calls it, when that is more than the one word typed at the prompt; null for the key. */
    String label;
    String[] options;
    /**
     * Choices still taken when typed in full, or when the config already has one, but no longer offered: out of the
     * completions, the hint and a settings window's buttons ({@code hidden=} in modules.txt). Kept so an old value still
     * works -- a brain retired from the list still plays when named.
     */
    String[] hidden = new String[0];
    double min = Double.NaN, max = Double.NaN;
    boolean isKey;

    /** Out of the way: under its module's closed "advanced" section in a settings window, and out of a plain ls. */
    boolean advanced;

    /** The upper field of a min/max pair, written together as {@code 40-60}; null otherwise. */
    String field2;

    /**
     * Listed with a {@link Slider} beside the value, over {@link #min} to {@link #max}: the
     * timings, as the classic panel had them. {@code gap} is the narrowest a pair's window may
     * be dragged to, {@code step} what a drag rounds to.
     */
    boolean slider;
    double gap, step = 5;

    /**
     * The same number in another unit, for a setting that is one of several views of one field:
     * shown as the stored value times {@code scale} (degrees a tick as degrees a second), or, with
     * {@code over} given, as {@code over} divided by it (degrees a tick as the milliseconds a turn
     * of so many degrees takes -- where a pair's ends change places, the faster being the shorter).
     * {@link #min}, {@link #max}, {@link #gap} and {@link #step} are in the unit shown.
     */
    double scale = 1.0, over = Double.NaN;

    /**
     * For a switch that is asked about before it goes on ({@link Prompt}): what the question says
     * under it, made as it is asked. Null for every other setting.
     */
    Supplier<String> warning;

    private Field cached, cached2;
    private Class<?> cachedOn;

    Setting(Node node, String key, String field, String desc, Supplier<Object> owner) {
        this.node = node;
        this.key = key;
        this.field = field;
        this.desc = desc;
        this.owner = owner;
    }

    /** The name a settings window shows on its row. */
    String shown() {
        return label != null ? label : key;
    }

    Setting alias(String... names) {
        this.aliases = names;
        return this;
    }

    Setting range(double min, double max) {
        this.min = min;
        this.max = max;
        return this;
    }

    Setting choices(String... options) {
        this.options = options;
        return this;
    }

    Setting hidden(String... options) {
        this.hidden = options;
        return this;
    }

    /** Whether the value now is one of the {@link #hidden} choices: shown beside the offered ones, so it can be seen and left. */
    String hiddenNow() {
        if (options == null || kind() != STRING) return null;
        String current = slug(String.valueOf(raw()));
        for (String option : options) if (slug(option).equals(current)) return null;
        for (String option : hidden) if (slug(option).equals(current)) return option;
        return null;
    }

    Setting pair(String upperField) {
        this.field2 = upperField;
        return this;
    }

    Setting keybind() {
        this.isKey = true;
        return this;
    }

    boolean matches(String name) {
        if (key.equalsIgnoreCase(name)) return true;
        for (String alias : aliases) if (alias.equalsIgnoreCase(name)) return true;
        return false;
    }

    // ---- reflection -----------------------------------------------------

    private Field field() throws ReflectiveOperationException {
        Object target = owner.get();
        Class<?> type = target.getClass();

        if (cached == null || cachedOn != type) {
            cached = type.getField(field);
            cached.setAccessible(true);
            cached2 = field2 == null ? null : type.getField(field2);
            if (cached2 != null) cached2.setAccessible(true);
            cachedOn = type;
        }

        return cached;
    }

    private Field upper() throws ReflectiveOperationException {
        field();
        return cached2;
    }

    int kind() {
        if (isKey) return KEY;
        if (field2 != null) return RANGE;

        try {
            Class<?> type = field().getType();
            if (type == boolean.class || type == Boolean.class) return BOOL;
            if (type == int.class || type == Integer.class) return INT;
            if (type == double.class || type == Double.class) return DOUBLE;
            if (type == float.class || type == Float.class) return FLOAT;
            if (type == long.class || type == Long.class) return LONG;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }

        return STRING;
    }

    Object raw() {
        try {
            return field().get(owner.get());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    boolean bool() {
        Object value = raw();
        return value != null && Boolean.TRUE.equals(value);
    }

    /** How the value reads in a listing: {@code on}/{@code off}, a trimmed number, a key name. */
    String display() {
        Object value = raw();
        if (value == null) return "?";

        if (derived()) {
            double lo = low(), hi = high();
            if (Double.isNaN(lo) || Double.isNaN(hi)) return "?";
            return field2 == null ? number(Math.rint(lo)) : number(Math.rint(lo)) + "-" + number(Math.rint(hi));
        }

        if (field2 != null) {
            try {
                Object upper = upper().get(owner.get());
                return number(((Number) value).doubleValue()) + "-" + number(((Number) upper).doubleValue());
            } catch (ReflectiveOperationException | RuntimeException e) {
                return "?";
            }
        }

        return switch (kind()) {
            case BOOL -> Boolean.TRUE.equals(value) ? "on" : "off";
            case KEY -> keyName(((Number) value).intValue());
            case DOUBLE, FLOAT -> number(((Number) value).doubleValue());
            case STRING -> options != null ? slug(String.valueOf(value)) : String.valueOf(value);
            default -> String.valueOf(value);
        };
    }

    /** The accepted input, shown as a completion hint: {@code <1-10>}, {@code on|off}, the choices. */
    String hint() {
        return switch (kind()) {
            case BOOL -> "on|off";
            case KEY -> "<key|mouse4|none>";
            case STRING -> options != null ? String.join("|", slugs()) : "<text>";
            case RANGE -> "<min-max>" + (!Double.isNaN(min) ? " in " + number(min) + "-" + number(max) : "");
            default -> !Double.isNaN(min) ? "<" + number(min) + "-" + number(max) + ">" : "<number>";
        };
    }

    String[] slugs() {
        if (options == null) return new String[0];
        String[] out = new String[options.length];
        for (int i = 0; i < options.length; i++) out[i] = slug(options[i]);
        return out;
    }

    /** Whether writing this would switch on something that is asked about first ({@link #warning}). */
    boolean asks(String input) {
        if (warning == null || input == null || kind() != BOOL || bool()) return false;
        return Boolean.TRUE.equals(parseBool(input.trim(), false));
    }

    /** The question a {@link #warning} goes under: its name in a settings window, asked. */
    String question() {
        return shown() + "?";
    }

    /**
     * Writes a value typed at the prompt, then commits the whole config.
     *
     * @return null when it took, or a one-line reason when it did not
     */
    String set(String input) {
        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) return key + ": needs a value (" + hint() + ")";

        try {
            Field f = field();
            Object target = owner.get();

            int kind = kind();
            if (derived() && (kind == RANGE || kind == INT || kind == LONG || kind == DOUBLE || kind == FLOAT)) {
                // Typed in the unit shown, and stored in the field's own.
                double[] pair = parseRange(text);
                if (!(Math.min(pair[0], pair[1]) > 0.0)) return key + ": has to be over 0";
                write(Math.min(pair[0], pair[1]), Math.max(pair[0], pair[1]));
                node.commit();
                return null;
            }

            switch (kind) {
                case BOOL -> {
                    Boolean parsed = parseBool(text, bool());
                    if (parsed == null) return key + ": expected on|off, got '" + text + "'";
                    f.set(target, parsed);
                }
                case INT, LONG, DOUBLE, FLOAT -> writeNumber(f, target, parseNumber(text));
                case RANGE -> {
                    double[] pair = parseRange(text);
                    writeNumber(f, target, Math.min(pair[0], pair[1]));
                    writeNumber(upper(), target, Math.max(pair[0], pair[1]));
                }
                case KEY -> {
                    Integer code = parseKey(text);
                    if (code == null) return key + ": unknown key '" + text + "'";
                    f.set(target, code);
                }
                default -> {
                    if (options != null) {
                        String option = matchOption(text);
                        if (option == null) return key + ": expected " + String.join("|", slugs());
                        f.set(target, option);
                    } else {
                        f.set(target, text);
                    }
                }
            }
        } catch (NumberFormatException e) {
            return key + ": '" + text + "' is not a number";
        } catch (ReflectiveOperationException | RuntimeException e) {
            return key + ": could not be written (" + e.getClass().getSimpleName() + ")";
        }

        node.commit();
        return null;
    }

    /**
     * Writes text straight into the live config without saving, as {@link #write} does for a
     * slider: a colour picker writes every frame of a drag, and {@link Node#commit} runs once
     * when it lets go. Only for a text field; false when it could not be written.
     */
    boolean put(String text) {
        if (kind() != STRING) return false;
        try {
            field().set(owner.get(), text);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Whether the number is shown in a unit other than the field's own. */
    boolean derived() {
        return !Double.isNaN(over) || scale != 1.0;
    }

    /** The value, or a pair's lower end, as a number in the unit shown; NaN when it cannot be read. */
    double low() {
        // The shortest time is the fastest turn: the other end of the pair.
        if (!Double.isNaN(over)) return over / (field2 == null ? storedLow() : storedHigh());
        return storedLow() * scale;
    }

    /** A pair's upper end; the value itself for a single number. */
    double high() {
        if (field2 == null) return low();
        if (!Double.isNaN(over)) return over / storedLow();
        return storedHigh() * scale;
    }

    private double storedLow() {
        Object value = raw();
        return Number.class.isInstance(value) ? ((Number) value).doubleValue() : Double.NaN;
    }

    private double storedHigh() {
        try {
            Object value = upper().get(owner.get());
            return Number.class.isInstance(value) ? ((Number) value).doubleValue() : Double.NaN;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Double.NaN;
        }
    }

    /**
     * Writes a slider's position straight into the live config, without saving: a drag
     * writes every frame, and {@link Node#commit} runs once when it lets go.
     */
    void write(double lowValue, double highValue) {
        double lowStored = lowValue / scale, highStored = highValue / scale;
        if (!Double.isNaN(over)) {
            lowStored = over / (field2 == null ? lowValue : highValue);
            highStored = over / lowValue;
        }
        try {
            Object target = owner.get();
            writeNumber(field(), target, lowStored);
            if (field2 != null) writeNumber(upper(), target, highStored);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    /** Binds a key a settings window caught, and commits; {@code GLFW_KEY_UNKNOWN} unbinds. */
    void writeKey(int code) {
        try {
            field().set(owner.get(), Integer.valueOf(code));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return;
        }
        node.commit();
    }

    /** Whether the number is a whole one: an int or a long field. */
    boolean whole() {
        try {
            Class<?> type = field().getType();
            return type == int.class || type == Integer.class || type == long.class || type == Long.class;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /**
     * What a drag rounds to. A timing's own {@link #step}; for any other number about a
     * hundredth of its range, as a round figure, and never finer than a listing shows.
     */
    double dragStep() {
        if (slider) return step > 0 ? step : 1;
        if (Double.isNaN(min) || !(max > min)) return 1;
        double raw = (max - min) / 100.0;
        double pow = Math.pow(10, Math.floor(Math.log10(raw)));
        double lead = raw / pow;
        double nice = (lead < 1.5 ? 1 : lead < 3.5 ? 2 : lead < 7.5 ? 5 : 10) * pow;
        return Math.max(whole() ? 1 : 0.01, nice);
    }

    private static void writeNumber(Field f, Object target, double value) throws IllegalAccessException {
        Class<?> type = f.getType();
        if (type == int.class || type == Integer.class) f.set(target, (int) Math.round(value));
        else if (type == long.class || type == Long.class) f.set(target, Math.round(value));
        else if (type == float.class || type == Float.class) f.set(target, (float) value);
        else f.set(target, value);
    }

    /** {@code 40-60}, {@code 40..60}, {@code 40,60}, or one number for both ends. */
    private static double[] parseRange(String text) {
        String t = text.replace("..", "-").replace(",", "-").replace("~", "-");
        int dash = t.indexOf('-', 1);
        if (dash < 0) {
            double v = parseNumber(t);
            return new double[] { v, v };
        }
        return new double[] { parseNumber(t.substring(0, dash)), parseNumber(t.substring(dash + 1)) };
    }

    String matchOption(String text) {
        String wanted = slug(text);
        for (String option : options) if (slug(option).equals(wanted)) return option;
        // A hidden choice only typed in full: a prefix finds the offered ones alone.
        for (String option : hidden) if (slug(option).equals(wanted)) return option;

        String found = null;
        for (String option : options) {
            if (slug(option).startsWith(wanted)) {
                if (found != null) return null;
                found = option;
            }
        }

        return found;
    }

    // ---- parsing and naming ---------------------------------------------

    /** {@code "Right-click use cancel"} as it is typed: {@code right-click-use-cancel}. */
    static String slug(String option) {
        return option.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    public static Boolean parseBool(String text, boolean current) {
        // Word sets, not a switch on the text: see Words for why this code has none.
        String word = text.toLowerCase(Locale.ROOT);
        if (BOOL_ON.contains(word)) return Boolean.TRUE;
        if (BOOL_OFF.contains(word)) return Boolean.FALSE;
        if (BOOL_FLIP.contains(word)) return !current;
        return null;
    }

    private static final Set<String> BOOL_ON = Words.of("on true yes 1 enable enabled");
    private static final Set<String> BOOL_OFF = Words.of("off false no 0 disable disabled");
    private static final Set<String> BOOL_FLIP = Words.of("toggle flip !");

    private static double parseNumber(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        if (t.endsWith("ms") || t.endsWith("px")) t = t.substring(0, t.length() - 2);
        if (t.endsWith("%")) t = t.substring(0, t.length() - 1);
        double value = Double.parseDouble(t.trim());
        if (Double.isNaN(value) || Double.isInfinite(value)) throw new NumberFormatException();
        return value;
    }

    static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e9) return String.valueOf((long) value);
        String s = String.format(Locale.ROOT, "%.2f", value);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static String keyName(int code) {
        if (code == GLFW.GLFW_KEY_UNKNOWN) return "none";
        if (code <= MOUSE_BASE) return "mouse" + (MOUSE_BASE - code + 1);
        if (code >= GLFW.GLFW_KEY_A && code <= GLFW.GLFW_KEY_Z) return String.valueOf((char) ('a' + code - GLFW.GLFW_KEY_A));
        if (code >= GLFW.GLFW_KEY_0 && code <= GLFW.GLFW_KEY_9) return String.valueOf((char) ('0' + code - GLFW.GLFW_KEY_0));
        if (code >= GLFW.GLFW_KEY_F1 && code <= GLFW.GLFW_KEY_F25) return "f" + (code - GLFW.GLFW_KEY_F1 + 1);
        for (int i = 0; i < NAMED_CODES.length; i++) if (NAMED_CODES[i] == code) return NAMED[i];
        return "key" + code;
    }

    public static Integer parseKey(String text) {
        String t = text.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        if (t.equals("none") || t.equals("unbound") || t.equals("unbind")) return GLFW.GLFW_KEY_UNKNOWN;

        if (t.startsWith("mouse") || t.startsWith("mb")) {
            try {
                int n = Integer.parseInt(t.substring(t.startsWith("mouse") ? 5 : 2));
                if (n >= 1 && n <= 8) return MOUSE_BASE - (n - 1);
            } catch (NumberFormatException ignored) {
            }
            return null;
        }

        if (t.length() == 1) {
            char c = t.charAt(0);
            if (c >= 'a' && c <= 'z') return GLFW.GLFW_KEY_A + (c - 'a');
            if (c >= '0' && c <= '9') return GLFW.GLFW_KEY_0 + (c - '0');
        }

        if (t.length() >= 2 && t.charAt(0) == 'f') {
            try {
                int n = Integer.parseInt(t.substring(1));
                if (n >= 1 && n <= 25) return GLFW.GLFW_KEY_F1 + n - 1;
            } catch (NumberFormatException ignored) {
            }
        }

        for (int i = 0; i < NAMED.length; i++) if (NAMED[i].replace("-", "").equals(t)) return NAMED_CODES[i];
        return null;
    }

    static final String[] KEY_SUGGESTIONS = {
        "none", "r", "f", "g", "v", "x", "z", "c", "mouse4", "mouse5", "rshift", "lalt", "capslock"
    };

    private static final String[] NAMED = {
        "rshift", "lshift", "rctrl", "lctrl", "ralt", "lalt", "tab", "capslock", "space", "enter",
        "backspace", "insert", "delete", "home", "end", "pgup", "pgdn", "up", "down", "left", "right",
        "grave", "minus", "equals", "lbracket", "rbracket", "backslash", "semicolon", "apostrophe",
        "comma", "period", "slash"
    };

    private static final int[] NAMED_CODES = {
        GLFW.GLFW_KEY_RIGHT_SHIFT, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_CONTROL,
        GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_ALT, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_TAB,
        GLFW.GLFW_KEY_CAPS_LOCK, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_BACKSPACE,
        GLFW.GLFW_KEY_INSERT, GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_END,
        GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN,
        GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_GRAVE_ACCENT, GLFW.GLFW_KEY_MINUS,
        GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_LEFT_BRACKET, GLFW.GLFW_KEY_RIGHT_BRACKET,
        GLFW.GLFW_KEY_BACKSLASH, GLFW.GLFW_KEY_SEMICOLON, GLFW.GLFW_KEY_APOSTROPHE,
        GLFW.GLFW_KEY_COMMA, GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_SLASH
    };
}
