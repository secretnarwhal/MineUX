package com.mineux.core;

import com.mineux.core.rpc.PresenceSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The desktop's own extension points, beside {@link Extensions}' four: everything else
 * a host mod that builds core into itself (and the visuals, and Screens) needs to plug into the
 * desktop without editing it. Kept in a class of its own so that {@code Extensions.java} stays
 * small and stable.
 * <p>
 * Everything is registered once, from an init, before the desktop first opens. The suppliers
 * and runnables are JDK interfaces, so a lambda is fine; everything of MineUX's own is an
 * abstract class (an obfuscator a host may run over this code, such as Skidfuscator, breaks a lambda for an interface it renames).
 */
public final class DeskExtensions {

    private static final Map<String, Part> PARTS = new LinkedHashMap<>();
    private static final Map<String, Supplier<String>> STATS = new LinkedHashMap<>();
    private static final Map<String, Act> ACTS = new LinkedHashMap<>();
    private static final Map<String, String> PERSONAL = new LinkedHashMap<>();
    private static final List<String> PERSONAL_FIELDS = new ArrayList<>();
    private static final List<TermProgram.Type> PROGRAMS = new ArrayList<>();
    private static final List<BarReadout> READOUTS = new ArrayList<>();
    private static final List<Class<?>> SCREENS = new ArrayList<>();
    private static final List<Backdrop> BACKDROPS = new ArrayList<>();
    private static final List<PresenceSource> PRESENCE = new ArrayList<>();
    private static final List<HudLayer> HUD = new ArrayList<>();

    private DeskExtensions() {}

    // ---- the settings tree ------------------------------------------------

    /**
     * A {@code cfg=} name for a sub-dir that edits an object held inside a registered config
     * ({@code cfg=pvp.reach} for {@code PvpConfig.get().reach}): the settings tree reads and
     * writes {@code part}'s fields, and {@code commit} saves the config it belongs to. Not a
     * config of its own, so nothing about workstations changes: the whole config already is
     * per workstation.
     */
    public static void addConfigPart(String cfg, Supplier<Object> part, Runnable commit) {
        if (PARTS.containsKey(cfg)) throw new IllegalArgumentException("config part " + cfg + " is already added");
        PARTS.put(cfg, new Part(part, commit));
    }

    public static Map<String, Part> configParts() {
        return Collections.unmodifiableMap(PARTS);
    }

    /**
     * A {@code stat=} name (a line a settings window shows under a module's rows) or a
     * {@code confirm=} name (what a switch that asks first says under its question).
     */
    public static void addStat(String name, Supplier<String> line) {
        if (STATS.containsKey(name)) throw new IllegalArgumentException("stat " + name + " is already added");
        STATS.put(name, line);
    }

    public static Map<String, Supplier<String>> stats() {
        return Collections.unmodifiableMap(STATS);
    }

    /** An {@code act=} name: a button row a settings window shows under a module's rows. */
    public static void addAct(String name, String label, String hint, Runnable run) {
        if (ACTS.containsKey(name)) throw new IllegalArgumentException("act " + name + " is already added");
        ACTS.put(name, new Act(label, hint, run));
    }

    public static Map<String, Act> acts() {
        return Collections.unmodifiableMap(ACTS);
    }

    // ---- workstations -----------------------------------------------------

    /**
     * A config file no workstation keeps, and why: it belongs to the person (keybinds, accounts,
     * records), not to a loadout. Every registered config is per workstation unless named here.
     */
    public static void addPersonal(String file, String reason) {
        PERSONAL.put(file, reason);
    }

    public static Map<String, String> personal() {
        return Collections.unmodifiableMap(PERSONAL);
    }

    /** One field of a per-workstation file that a workstation switch leaves alone: {@code duel.json}, {@code botGames}. */
    public static void addPersonalField(String file, String field) {
        String key = file + ":" + field;
        if (!PERSONAL_FIELDS.contains(key)) PERSONAL_FIELDS.add(key);
    }

    /** As {@code file:field}. */
    public static List<String> personalFields() {
        return Collections.unmodifiableList(PERSONAL_FIELDS);
    }

    // ---- the terminal and the desktop ---------------------------------------

    /** A program that takes a terminal over until Ctrl+C, typed by its name in muxsh. */
    public static void addProgram(TermProgram.Type type) {
        for (TermProgram.Type have : PROGRAMS) {
            if (have.name().equals(type.name())) throw new IllegalArgumentException("program " + type.name() + " is already added");
        }
        PROGRAMS.add(type);
    }

    public static List<TermProgram.Type> programs() {
        return Collections.unmodifiableList(PROGRAMS);
    }

    /** A readout on the right of the bar, left of the clock. */
    public static void addBarReadout(BarReadout readout) {
        READOUTS.add(readout);
    }

    public static List<BarReadout> barReadouts() {
        return Collections.unmodifiableList(READOUTS);
    }

    /**
     * A screen class of the extension's own that, opened in a world, is held over the game as
     * the desktop is (the overlay) rather than put up as the game's screen: nothing pauses.
     * A host's own settings panel, say.
     */
    public static void addOverlayScreen(Class<?> screen) {
        if (!SCREENS.contains(screen)) SCREENS.add(screen);
    }

    public static List<Class<?>> overlayScreens() {
        return Collections.unmodifiableList(SCREENS);
    }

    /**
     * What the home desk draws behind everything in place of its black: the visuals' menu
     * scenes, say. The last one added that is {@link Backdrop#on()} draws; none, and it is black.
     */
    public static void addBackdrop(Backdrop backdrop) {
        BACKDROPS.add(backdrop);
    }

    public static List<Backdrop> backdrops() {
        return Collections.unmodifiableList(BACKDROPS);
    }

    /** What the Discord presence says while the extension is doing something: a game it is playing for you, say. */
    public static void addPresenceSource(PresenceSource source) {
        PRESENCE.add(source);
    }

    public static List<PresenceSource> presenceSources() {
        return Collections.unmodifiableList(PRESENCE);
    }

    /** Something drawn over the game while the desktop is closed, under its pinned windows. */
    public static void addHudLayer(HudLayer layer) {
        HUD.add(layer);
    }

    public static List<HudLayer> hudLayers() {
        return Collections.unmodifiableList(HUD);
    }

    // ---- added for hosts that build core into their own mod ----

    private static final List<String> HIDDEN = new ArrayList<>();
    private static final Map<String, PaneFactory> PANES = new LinkedHashMap<>();
    private static final List<ShutdownLayer> SHUTDOWN = new ArrayList<>();
    private static RebootHook reboot;
    private static final List<TrayAddition> TRAY_ROWS = new ArrayList<>();
    private static final Map<String, String> TRAY_BLURBS = new LinkedHashMap<>();
    private static final List<Class<?>> IN_GAME_SCREENS = new ArrayList<>();
    private static final List<java.util.function.BooleanSupplier> IN_GAME_CHECKS = new ArrayList<>();

    /**
     * Takes one of the shell's own command words away ({@code login}): it is no longer run,
     * listed in {@code help} or completed, and falls through to an extension's command of that
     * word or to the settings tree. Lower case, as typed.
     */
    public static void hideBuiltIn(String word) {
        String lower = word.toLowerCase(java.util.Locale.ROOT);
        if (!HIDDEN.contains(lower)) HIDDEN.add(lower);
    }

    public static List<String> hiddenBuiltIns() {
        return Collections.unmodifiableList(HIDDEN);
    }

    /**
     * A kind of window an extension draws on the desk ({@link com.mineux.core.ui.term.DeskWindow}),
     * opened by {@code CommandOut.openWindow(kind)}: tiled in on the right, or brought forward
     * when one is open.
     */
    public static void addPane(String kind, PaneFactory factory) {
        if (PANES.containsKey(kind)) throw new IllegalArgumentException("pane " + kind + " is already added");
        PANES.put(kind, factory);
    }

    public static Map<String, PaneFactory> panes() {
        return Collections.unmodifiableMap(PANES);
    }

    /** What {@code reboot} does once answered yes, in place of MineUX's close-then-start. One; a second is refused. */
    public static void setRebootHook(RebootHook hook) {
        if (reboot != null) throw new IllegalArgumentException("a reboot hook is already set");
        reboot = hook;
    }

    /** The reboot hook, or null for MineUX's own reboot. */
    public static RebootHook rebootHook() {
        return reboot;
    }

    /** Drawn over the desktop while the game goes down, and holding it up meanwhile ({@link ShutdownLayer}). */
    public static void addShutdownLayer(ShutdownLayer layer) {
        SHUTDOWN.add(layer);
    }

    public static List<ShutdownLayer> shutdownLayers() {
        return Collections.unmodifiableList(SHUTDOWN);
    }

    /**
     * A row added to a tray group that is already there, core's own ({@code client}) or another
     * extension's, at {@code index} among its rows (past the end, or negative: last).
     */
    public static void addTrayRow(String group, int index, TrayGroup.Row row) {
        TRAY_ROWS.add(new TrayAddition(group, index, row));
    }

    public static List<TrayAddition> trayRows() {
        return Collections.unmodifiableList(TRAY_ROWS);
    }

    /** What a tray group's window says it is for, in place of its own blurb: for a group that gained rows. */
    public static void setTrayBlurb(String group, String blurb) {
        TRAY_BLURBS.put(group, blurb);
    }

    public static Map<String, String> trayBlurbs() {
        return Collections.unmodifiableMap(TRAY_BLURBS);
    }

    /**
     * A screen class of the extension's own that the in-game keys treat as the desktop's: the
     * window manager's SUPER+number is left to it, as under the desktop, rather than taken.
     * Unlike {@link #addOverlayScreen}, it is put up as the game's screen as usual.
     */
    public static void addInGameScreen(Class<?> screen) {
        if (!IN_GAME_SCREENS.contains(screen)) IN_GAME_SCREENS.add(screen);
    }

    public static List<Class<?>> inGameScreens() {
        return Collections.unmodifiableList(IN_GAME_SCREENS);
    }

    /** As {@link #addInGameScreen}, for something of the extension's that is open without a screen class of its own: true while it is. */
    public static void addInGameCheck(java.util.function.BooleanSupplier open) {
        IN_GAME_CHECKS.add(open);
    }

    public static List<java.util.function.BooleanSupplier> inGameChecks() {
        return Collections.unmodifiableList(IN_GAME_CHECKS);
    }

    // ---- chatrooms from somewhere else ----------------------------------------

    private static final List<com.mineux.core.social.ChatRoomSource> CHAT_ROOMS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final List<String> CHAT_ROOM_NAMES = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * One more room in {@code irc}, served by a server of the extension's with its own sign-in
     * ({@link com.mineux.core.social.ChatRoomSource}): a tab of its own while shown, with its own
     * stream and reconnect. Its name ({@code room()}, read once, here) is letters, digits, {@code -}
     * and {@code _}, at most 32, and neither MineUX's own room's nor one already added.
     */
    public static void addChatRoom(com.mineux.core.social.ChatRoomSource source) {
        String room = source.room();
        if (room == null || !room.matches("[A-Za-z0-9_-]{1,32}")) throw new IllegalArgumentException("chat room name " + room + " is not a plain word");
        if (room.equalsIgnoreCase(com.mineux.core.social.Chat.PUBLIC.substring(1))) throw new IllegalArgumentException("chat room " + room + " is MineUX's own");
        for (String have : CHAT_ROOM_NAMES) {
            if (have.equalsIgnoreCase(room)) throw new IllegalArgumentException("chat room " + room + " is already added");
        }
        CHAT_ROOM_NAMES.add(room);
        CHAT_ROOMS.add(source);
    }

    public static List<com.mineux.core.social.ChatRoomSource> chatRooms() {
        return Collections.unmodifiableList(CHAT_ROOMS);
    }

    /** The name a chatroom was added under, as {@link #addChatRoom} read it; empty for one never added. */
    public static String chatRoomName(com.mineux.core.social.ChatRoomSource source) {
        int at = CHAT_ROOMS.indexOf(source);
        return at < 0 ? "" : CHAT_ROOM_NAMES.get(at);
    }

    /** One {@link #addTrayRow}. */
    public record TrayAddition(String group, int index, TrayGroup.Row row) {}

    /** One {@link #addConfigPart}. */
    public record Part(Supplier<Object> part, Runnable commit) {}

    /** One {@link #addAct}. */
    public record Act(String label, String hint, Runnable run) {}
}
