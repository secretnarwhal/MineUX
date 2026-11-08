package com.mineux.core.ui.term;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineux.core.config.ConfigFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Workstations: whole-client config profiles, one per desktop in the top bar.
 * <p>
 * A workstation is a folder under {@code config/mineux/workstations} holding a copy of
 * every module's json. The live files stay where they always were and stay the truth -- every
 * module goes on reading its own config exactly as before. Switching writes the live state
 * into the workstation being left, then reads the one being entered back <i>into the objects
 * already loaded</i>, field by field, rather than swapping instances: nothing outside core
 * needs a setter for that, and every module holding a reference to its config keeps it.
 * <p>
 * What a workstation keeps is {@link Configs#perWorkstation()}: every config the settings tree
 * registers, so a new module is per-workstation the moment it is registered there. What stays
 * the person's -- how the desktop answers keys ({@code ui.json}), the accounts, the
 * terminal's own state, and whatever an extension names -- is named in {@link Configs#personal()} and
 * {@link Configs#personalFields()}, with the reason.
 * <p>
 * A workstation saved before a config (or a setting) existed has no copy of it. Entering it
 * gives that module its <i>defaults</i> -- off -- not the value live in the workstation being
 * left: otherwise a module switched on anywhere would come along to every old workstation,
 * which is the very thing workstations are for stopping. The live value is not lost: it was
 * written into the workstation being left first.
 */
final class Workstations {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/workstations");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static final String DEFAULT = "default";

    /**
     * The special workspace (SUPER+S, Hyprland's scratchpad): a workstation like the rest -- its
     * own copy of every config, loaded when it is opened -- made the first time it is asked for.
     * Tray groups that are {@code specialOnly} are in the bar only there, which is how an
     * extension keeps modules to it. Kept out of the numbers: its place in the bar is after them
     * all, past the plus.
     */
    static final String SPECIAL = "special";

    /** Past anything SUPER+number or {@code ws open <number>} can name ({@link #parseSlot}). */
    private static final int SPECIAL_SLOT = 100;

    /** The terminal's own file: workstations, the open one, the art on show, command history. */
    static final class State {
        public String current = DEFAULT;
        public List<String> workstations = new ArrayList<>();
        public int art = 0;
        /** The art on show by name, which survives art being added; {@link #art} is the fallback. */
        public String artName;
        /** The art's colours: one of {@link ArtView#PALETTES}. */
        public String artPalette = ArtView.GOLD_PALETTE;
        /** The art's now-and-then glitch. */
        public boolean artGlitch = true;
        /** The art's float and the bright band that sweeps down it. */
        public boolean artMotion = true;
        public List<String> history = new ArrayList<>();
        public String superKey = "alt";
        /**
         * Each workstation's number, i3-style, in {@link #workstations} order: what the bar
         * shows and SUPER+number goes to. Numbers can have gaps -- SUPER+5 with only 1 and 2 in
         * use makes a 5 -- and the list is kept sorted by them.
         * <p>
         * An {@code int[]} on disk rather than a map of names to numbers: Gson reads a map's
         * value type off the field's generic signature, and a number read without one comes
         * back a Double. An array's element type survives anything.
         */
        public int[] slotList = new int[0];

        /** Where each debug readout's pane was last left ({@link Readouts}). */
        public Place[] readouts = new Place[0];

        /** {@code bind <key> "<command>"}: a key that runs a command in game ({@link Binds}). */
        public Bind[] binds = new Bind[0];

        /** SUPER+M's last choice, {@code borderless} or {@code maximize}, put back after each sign-in ({@link WindowMode}). */
        public String windowMode = "";

        /** {@link #slotList} by name, for use; written back by {@link #saveState}. */
        transient Map<String, Integer> slots = new LinkedHashMap<>();
    }

    /**
     * One readout pane's spot, in GUI pixels. Arrays of these rather than a map or a list: an
     * array's element type survives the obfuscator stripping generic signatures (see
     * {@link State#slotList}).
     */
    static final class Place {
        public String id;
        public float x, y, w, h;
        /** The workstation it is on, for a readout whose switch no workstation keeps; null for the rest. */
        public String desk;
        /** How solid it is drawn (SUPER+K, SUPER+L); 0 in files from before there was one, read as 1. */
        public float opacity;
    }

    /** One {@code bind}: the key as {@code bind} names it, and the command it runs. */
    static final class Bind {
        public String key;
        public String command;
    }

    private static State state;

    private Workstations() {}

    private static Path root() {
        return ConfigFile.directory().resolve("workstations");
    }

    private static Path stateFile() {
        return ConfigFile.directory().resolve("terminal.json");
    }

    static synchronized State state() {
        if (state != null) return state;

        Path file = stateFile();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                state = GSON.fromJson(reader, State.class);
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Could not read {}", file, e);
            }
        }

        if (state == null) state = new State();
        if (state.workstations == null) state.workstations = new ArrayList<>();
        if (state.history == null) state.history = new ArrayList<>();
        if (state.readouts == null) state.readouts = new Place[0];
        if (state.binds == null) state.binds = new Bind[0];
        List<Bind> binds = new ArrayList<>();
        for (Bind bind : state.binds) if (bind != null && bind.key != null && bind.command != null) binds.add(bind);
        state.binds = binds.toArray(new Bind[0]);
        state.workstations.removeIf(n -> n == null || !valid(n));
        if (state.workstations.isEmpty()) state.workstations.add(DEFAULT);
        if (state.current == null || !state.workstations.contains(state.current)) {
            state.current = state.workstations.get(0);
        }
        // One spelling, so the name is the key it is looked up by everywhere.
        for (int i = 0; i < state.workstations.size(); i++) {
            if (isSpecial(state.workstations.get(i))) state.workstations.set(i, SPECIAL);
        }
        if (isSpecial(state.current)) state.current = SPECIAL;
        state.slots = new LinkedHashMap<>();
        if (state.slotList != null) {
            for (int i = 0; i < state.slotList.length && i < state.workstations.size(); i++) {
                state.slots.put(state.workstations.get(i), state.slotList[i]);
            }
        }
        for (String name : state.workstations) {
            Integer slot = state().slots.get(name);
            if (slot == null || slot < 1 || Collections.frequency(state.slots.values(), slot) > 1) {
                state.slots.put(name, freeSlot(name));
            }
        }
        if (state.workstations.contains(SPECIAL)) state.slots.put(SPECIAL, SPECIAL_SLOT);
        sortBySlot();

        // A workstation that has never been written is the live config as it stands.
        if (!Files.isDirectory(root().resolve(state.current))) snapshot(state.current);
        saveState();
        return state;
    }

    static synchronized void saveState() {
        if (state == null) return;

        while (state.history.size() > 200) state.history.remove(0);

        state.slotList = new int[state.workstations.size()];
        for (int i = 0; i < state.slotList.length; i++) state.slotList[i] = slot(state.workstations.get(i));


        Path file = stateFile();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(state, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not write {}", file, e);
        }
    }

    static String current() {
        return state().current;
    }

    /**
     * Whether a config object is one a workstation keeps a copy of -- a unit, or an object held in
     * one of a unit's fields ({@code PvpConfig#reach}). What is not is the same on every one.
     */
    static boolean keeps(Object config) {
        if (config == null) return false;
        for (Configs.Entry unit : Configs.perWorkstation()) {
            try {
                Object live = unit.live().get();
                if (live == config) return true;
                for (Field field : live.getClass().getFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                    if (field.get(live) == config) return true;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.warn("Could not look into {}", unit.file(), e);
            }
        }
        return false;
    }

    static List<String> names() {
        return state().workstations;
    }

    /** The special workspace, by its own name or the host's ({@link com.mineux.core.Host#specialName}). */
    static boolean isSpecial(String name) {
        return name != null && (name.equalsIgnoreCase(SPECIAL) || name.equalsIgnoreCase(com.mineux.core.MineUX.specialName()));
    }

    static boolean hasSpecial() {
        return names().contains(SPECIAL);
    }

    /** What a card says a workstation is: its number and name, or just the name for the one without a number. */
    static String tag(String name) {
        return isSpecial(name) ? com.mineux.core.MineUX.specialName() : slot(name) + " " + name;
    }

    /**
     * Makes the special workspace, a copy of the open one, without opening it. Null when it is
     * there afterwards, else the reason it is not.
     */
    static synchronized String makeSpecial() {
        if (hasSpecial()) return null;

        snapshot(current());
        snapshot(SPECIAL);
        names().add(SPECIAL);
        state.slots.put(SPECIAL, SPECIAL_SLOT);
        sortBySlot();
        saveState();
        return null;
    }

    static boolean valid(String name) {
        return name != null && name.matches("[A-Za-z0-9_.\\-]{1,24}") && !name.startsWith(".");
    }

    /** The next free {@code ws2}, {@code ws3}... for the top bar's plus button, named after its number. */
    static String freshName() {
        return nameFor(freeSlot(null));
    }

    private static String nameFor(int slot) {
        String name = "ws" + slot;
        for (int i = 2; names().contains(name); i++) name = "ws" + slot + "-" + i;
        return name;
    }

    /** A workstation's number in the bar. */
    static int slot(String name) {
        Integer slot = state().slots.get(name);
        return slot == null ? 0 : slot;
    }

    /** The workstation with this number, or null. */
    static String atSlot(int slot) {
        for (Map.Entry<String, Integer> e : state().slots.entrySet()) {
            if (e.getValue() == slot) return e.getKey();
        }
        return null;
    }

    /** The lowest number nothing else has; {@code self} is ignored so it can keep its own. */
    private static int freeSlot(String self) {
        for (int slot = 1; ; slot++) {
            boolean taken = false;
            for (Map.Entry<String, Integer> e : state.slots.entrySet()) {
                if (e.getValue() == slot && !e.getKey().equals(self)) taken = true;
            }
            if (!taken) return slot;
        }
    }

    private static void sortBySlot() {
        state.workstations.sort(Comparator.comparingInt(n -> state.slots.getOrDefault(n, Integer.MAX_VALUE)));
    }

    // ---- operations -----------------------------------------------------
    // Each returns null on success or a one-line reason for the terminal to print.

    /** Writes the live configs into the open workstation. */
    static synchronized void save() {
        snapshot(current());
    }

    /** A new workstation, starting as a copy of the open one, and opened. */
    static synchronized String create(String name) {
        return create(name, 0);
    }

    /** The same, at a given number -- 0 for the lowest free one. */
    static synchronized String create(String name, int slot) {
        if (name == null) name = nameFor(slot > 0 ? slot : freeSlot(null));
        if (isSpecial(name)) return open(SPECIAL);
        if (!valid(name)) return "ws: bad name '" + name + "' (letters, digits, - _ . up to 24)";
        if (names().contains(name)) return "ws: '" + name + "' already exists";
        if (slot > 0 && atSlot(slot) != null) return "ws: " + slot + " is already " + atSlot(slot);

        snapshot(current());
        snapshot(name);
        names().add(name);
        state.slots.put(name, slot > 0 ? slot : freeSlot(name));
        sortBySlot();
        previous = state.current;
        state.current = name;
        saveState();
        return null;
    }

    /**
     * A new workstation at a free number, a copy of the open one, left closed: somewhere to send
     * a window to ({@code movetoworkspacesilent}). Its name, or null when the number is taken.
     */
    static synchronized String createQuiet(int slot) {
        if (slot <= 0 || atSlot(slot) != null) return atSlot(slot);
        String name = nameFor(slot);
        snapshot(current());
        snapshot(name);
        names().add(name);
        state.slots.put(name, slot);
        sortBySlot();
        saveState();
        return name;
    }

    /** The workstation open before the one open now ({@code workspace previous}), or null. */
    private static String previous;

    static String previous() {
        return previous != null && names().contains(previous) ? previous : null;
    }

    static synchronized String open(String name) {
        // The one workstation that is made by being asked for.
        if (isSpecial(name)) makeSpecial();

        String found = find(name);
        if (found == null) return "ws: no workstation '" + name + "'";
        if (found.equals(current())) return null;
        previous = current();

        snapshot(current());
        restore(found);
        state.current = found;
        saveState();
        return null;
    }

    static synchronized String remove(String name) {
        String found = find(name);
        if (found == null) return "ws: no workstation '" + name + "'";
        if (names().size() == 1) return "ws: can't delete the only workstation";

        if (found.equals(current())) {
            int index = names().indexOf(found);
            String next = names().get(index == 0 ? 1 : index - 1);
            String error = open(next);
            if (error != null) return error;
        }

        names().remove(found);
        state.slots.remove(found);
        Desk.remove(found);
        deleteTree(root().resolve(found));
        saveState();
        return null;
    }

    static synchronized String rename(String from, String to) {
        String found = find(from);
        if (found == null) return "ws: no workstation '" + from + "'";
        if (isSpecial(found) || isSpecial(to)) return "ws: the special workspace keeps its name";
        if (!valid(to)) return "ws: bad name '" + to + "'";
        if (names().contains(to)) return "ws: '" + to + "' already exists";

        try {
            Path source = root().resolve(found);
            if (Files.isDirectory(source)) Files.move(source, root().resolve(to));
        } catch (IOException e) {
            return "ws: rename failed (" + e.getMessage() + ")";
        }

        names().set(names().indexOf(found), to);
        state.slots.put(to, state.slots.remove(found));
        Desk.rename(found, to);
        if (found.equals(state.current)) state.current = to;
        saveState();
        return null;
    }

    /** Case-insensitive, and a number picks by the number the bar shows. */
    static String find(String name) {
        if (name == null) return null;
        for (String n : names()) if (n.equalsIgnoreCase(name)) return n;
        int slot = parseSlot(name);
        return slot > 0 ? atSlot(slot) : null;
    }

    /** A workstation number as typed, or 0 when it is not one. */
    static int parseSlot(String text) {
        try {
            int slot = Integer.parseInt(text.trim());
            return slot >= 1 && slot <= 99 ? slot : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---- disk -----------------------------------------------------------

    private static void snapshot(String name) {
        Path dir = root().resolve(name);

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.warn("Could not create {}", dir, e);
            return;
        }

        for (Configs.Entry unit : Configs.perWorkstation()) {
            try (Writer writer = Files.newBufferedWriter(dir.resolve(unit.file()))) {
                GSON.toJson(unit.live().get(), writer);
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Could not snapshot {} into {}", unit.file(), name, e);
            }
        }
    }

    private static void restore(String name) {
        Path dir = root().resolve(name);
        for (Configs.Entry unit : Configs.perWorkstation()) {
            try {
                JsonObject json = stored(dir, unit.file());
                load(unit.file(), unit.live().get(), json);
                unit.commit().run();
            } catch (IOException | ReflectiveOperationException | RuntimeException e) {
                LOGGER.warn("Could not restore {} from {}", unit.file(), name, e);
            }
        }
    }

    /** A workstation's copy of a file, or an empty object when it has none (saved before the module existed). */
    private static JsonObject stored(Path dir, String file) throws IOException {
        Path path = dir.resolve(file);
        // A workstation saved before the pvp settings' file was renamed.
        if (!Files.exists(path)) return new JsonObject();
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement tree = JsonParser.parseReader(reader);
            return tree != null && tree.isJsonObject() ? tree.getAsJsonObject() : new JsonObject();
        }
    }

    /**
     * A stored file into the live config object, in place. Read through Gson first, so a key the
     * file has no copy of comes out as the field's default (its initialiser), as at a first run;
     * then every field is copied over but the person's own ({@link Configs#personalFields()}).
     */
    static void load(String file, Object live, JsonObject json) throws ReflectiveOperationException {
        Object stored = GSON.fromJson(json, live.getClass());
        if (stored == null) return;
        for (Field field : live.getClass().getFields()) {
            if (!copied(field) || Configs.personalField(file, field.getName())) continue;
            copy(field, live, stored, 0);
        }
    }

    private static boolean copied(Field field) {
        int mods = field.getModifiers();
        return !Modifier.isStatic(mods) && !Modifier.isTransient(mods) && !Modifier.isFinal(mods);
    }

    /**
     * One field from the stored object to the live one. A nested settings object
     * ({@code PvpConfig#reach}) is copied into rather than replaced, so a module that holds on to
     * it keeps reading the live one.
     */
    private static void copy(Field field, Object live, Object stored, int depth) throws ReflectiveOperationException {
        field.setAccessible(true);
        Object to = field.get(live);
        Object from = field.get(stored);
        if (depth < 4 && nested(field.getType()) && to != null && from != null && to.getClass() == from.getClass()) {
            for (Field inner : to.getClass().getFields()) {
                if (copied(inner)) copy(inner, to, from, depth + 1);
            }
            return;
        }
        field.set(live, from);
    }

    /** A settings object of the client's own, not a value, a string, an array or a JDK collection. */
    private static boolean nested(Class<?> type) {
        if (type.isPrimitive() || type.isArray() || type.isEnum() || type.isRecord()) return false;
        String name = type.getName();
        return !name.startsWith("java.") && !name.startsWith("javax.") && !name.startsWith("com.google.");
    }

    private static void deleteTree(Path dir) {
        if (!Files.exists(dir)) return;

        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (IOException e) {
            LOGGER.warn("Could not delete {}", dir, e);
        }
    }
}
