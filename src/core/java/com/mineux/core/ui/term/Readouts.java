package com.mineux.core.ui.term;

import com.mineux.core.DeskExtensions;
import com.mineux.core.HudLayer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The debug readouts: what each module says it is doing, as live panels.
 * <p>
 * Every module whose directory has a {@code debug} switch is a readout, named after the
 * directory ({@code aim}, {@code mace}, {@code combat}, {@code clicker}...). Switching it on
 * opens a {@link ReadoutPane} on the open workstation's desk; closing the pane switches it
 * off again. The modules post their lines here each tick instead of to the action bar.
 * <p>
 * <b>One workstation each.</b> Most switches are a workstation's own, saved and put back with it,
 * so their panes come and go with the workstation. A switch that is not (a module's {@code debug},
 * whose config is the person's rather than a loadout's) stays on wherever you go, so its pane is
 * tied to the workstation it was opened on instead ({@link #HOMES}) and shows nowhere else.
 * <p>
 * <b>Outside the desktop.</b> A pane stays on screen after the desktop closes, wherever it was
 * left and however big. There is no hook into the in-game HUD here, and adding one would
 * mean a new mixin -- but vanilla draws its toasts every frame, on top of the HUD and of
 * any open screen, and a toast is something the mod can add on its own. So one toast
 * that never leaves ({@link HudToast}) draws the panes, from the top left of the screen
 * rather than from its toast slot. Left as it is, it would hold the first toast slot for good
 * and whoosh in like an advancement; {@link HudToast#settle} takes the slot back and skips
 * the entrance, and if reflection ever cannot, the toast leaves whenever no readout is on.
 */
public final class Readouts {

    private static final int LOG_SIZE = 40;

    /** What one readout last said. */
    static final class Feed {
        String[] lines = new String[0];
        long at;
        /** When the last event came in; the live lines are lit for a moment after. */
        long eventAt;
        final Deque<String[]> log = new ArrayDeque<>();
    }

    private static final Map<String, Feed> FEEDS = new HashMap<>();

    /**
     * A readout's own drawing, between its header and its lines: for what a module has to show
     * that is not a line of text -- a picture of something a module tracks. A class to extend, drawn every frame.
     */
    public abstract static class Canvas {
        /** How tall it wants to be at this width. */
        public abstract int height(int width);

        public abstract void draw(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h);
    }

    private static final Map<String, Canvas> CANVASES = new HashMap<>();

    /** Gives readout {@code id} a drawing of its own. */
    public static void canvas(String id, Canvas canvas) {
        CANVASES.put(id, canvas);
    }

    public static Canvas canvasOf(String id) {
        return CANVASES.get(id);
    }

    /** Where each readout's pane was last left, as {id, x, y, w, h}. */
    private static final Map<String, float[]> PLACES = new HashMap<>();
    /** How solid each readout's pane was left ({@link Pane#opacity}), where it is not. */
    private static final Map<String, Float> OPACITY = new HashMap<>();
    private static boolean placesLoaded;

    /**
     * For a readout whose switch no workstation keeps: the workstation its pane is on. Follows the
     * pane when it is sent to another ({@code movetoworkspace}), and is kept with its place.
     */
    private static final Map<String, String> HOMES = new HashMap<>();

    /** Whether each readout's switch is outside every workstation's configs, worked out once. */
    private static final Map<String, Boolean> ROAMING = new HashMap<>();

    private static List<Node> sources;

    private Readouts() {}

    // ---- what the modules call --------------------------------------------

    /** What a readout is doing right now, one line per entry; replaces what it said before. */
    public static void post(String id, String... lines) {
        Feed feed = FEEDS.computeIfAbsent(id, k -> new Feed());
        feed.lines = lines;
        feed.at = System.currentTimeMillis();
    }

    /** Something that happened -- a hit, a smash, a new target: logged, and the panel lights up. */
    public static void event(String id, String text) {
        Feed feed = FEEDS.computeIfAbsent(id, k -> new Feed());
        long now = System.currentTimeMillis();
        feed.eventAt = now;
        String[] last = feed.log.peekFirst();
        if (last != null && last[1].equals(text) && now - Long.parseLong(last[0]) < 250) return;
        feed.log.addFirst(new String[] {String.valueOf(now), text});
        while (feed.log.size() > LOG_SIZE) feed.log.removeLast();
    }

    static Feed feed(String id) {
        return FEEDS.get(id);
    }

    // ---- which readouts there are -------------------------------------------

    /** Every directory with a {@code debug} switch, in tree order. */
    static synchronized List<Node> sources() {
        if (sources == null) {
            List<Node> all = new ArrayList<>();
            Modules.root().walk(all);
            List<Node> found = new ArrayList<>();
            for (Node node : all) {
                Setting s = debugSwitch(node);
                if (s != null) found.add(node);
            }
            sources = found;
        }
        return sources;
    }

    private static Setting debugSwitch(Node node) {
        for (Setting s : node.settings) if (s.key.equals("debug")) return s;
        return null;
    }

    static boolean on(Node node) {
        Setting s = debugSwitch(node);
        return s != null && s.bool();
    }

    static void turnOff(String id) {
        Node node = Modules.find(id);
        Setting s = node == null ? null : debugSwitch(node);
        if (s != null) s.set("off");
    }

    private static boolean anyOn() {
        for (Node node : sources()) if (on(node)) return true;
        return false;
    }

    /** Whether a readout's switch stays as it is from one workstation to the next. */
    private static boolean roaming(Node node) {
        Boolean known = ROAMING.get(node.name);
        if (known == null) {
            Setting s = debugSwitch(node);
            known = s != null && !Workstations.keeps(s.owner.get()) ? Boolean.TRUE : Boolean.FALSE;
            ROAMING.put(node.name, known);
        }
        return known.booleanValue();
    }

    /**
     * Keeps the roaming readouts to one workstation each: one switched off loses its pane on every
     * desk and its home with it, and one with a pane somewhere has that workstation for its home.
     */
    private static void settle() {
        for (Node node : sources()) {
            if (!roaming(node)) continue;
            boolean on = on(node);
            if (!on) HOMES.remove(node.name);
            for (String name : Workstations.names()) {
                Desk desk = Desk.peek(name);
                if (desk == null) continue;
                for (Pane pane : new ArrayList<>(desk.windows)) {
                    if (!node.name.equals(pane.readout()) || pane.onAll) continue;
                    if (on) {
                        HOMES.put(node.name, name);
                    } else {
                        remember(pane);
                        desk.close(pane);
                    }
                }
            }
        }
    }

    // ---- panes ------------------------------------------------------------

    /**
     * Puts the desk's readout panes in step with the switches: a pane for each one that is on,
     * none for one that is off. Cheap enough to run every frame, which is what lets a switch
     * flipped anywhere -- a command, the classic panel, a workstation change -- show at once.
     */
    static void sync(Desk desk, int screenW, int screenH) {
        loadPlaces();
        settle();
        String here = Workstations.current();
        List<Node> on = new ArrayList<>();
        for (Node node : sources()) if (on(node)) on.add(node);

        // Pinned readouts are checked too, wherever they are: one switched off goes from over the game as well.
        List<Pane> panes = new ArrayList<>(desk.windows);
        for (Pane pin : Desk.PINS) if (!panes.contains(pin)) panes.add(pin);
        for (Pane pane : panes) {
            String id = pane.readout();
            if (id == null) continue;
            boolean wanted = false;
            for (Node node : on) if (node.name.equals(id)) wanted = true;
            if (!wanted) {
                remember(pane);
                for (Desk d : Desk.all()) if (d.windows.contains(pane)) d.close(pane);
                Desk.PINS.remove(pane);
            }
        }

        int index = 0;
        for (Node node : on) {
            if (paneFor(desk, node.name) != null) {
                index++;
                continue;
            }
            if (roaming(node)) {
                // Open on another workstation: that is where it stays.
                String home = HOMES.get(node.name);
                if (home != null && !home.equals(here) && Workstations.names().contains(home)) continue;
                HOMES.put(node.name, here);
            }
            ReadoutPane pane = new ReadoutPane(node);
            float[] place = PLACES.get(node.name);
            if (place != null) {
                pane.fx = place[0];
                pane.fy = place[1];
                pane.fw = place[2];
                pane.fh = place[3];
            } else {
                pane.fw = 250;
                pane.fh = ReadoutPane.naturalHeight(node.name);
                pane.fx = 8;
                pane.fy = 26 + index * 90;
            }
            Float opacity = OPACITY.get(node.name);
            if (opacity != null) pane.opacity = opacity;
            pane.floating = true;
            clampFloating(pane, screenW, screenH);
            pane.snap();
            desk.windows.add(pane);
            index++;
        }
    }

    private static Pane paneFor(Desk desk, String id) {
        // A pinned readout is over the game from whichever desk it is on: no second one here.
        for (Pane pane : Desk.PINS) if (id.equals(pane.readout())) return pane;
        for (Pane pane : desk.windows) if (id.equals(pane.readout())) return pane;
        return null;
    }

    /**
     * Where a floating pane is drawn on a screen this size: its own place, pulled inside the
     * screen. Only the drawn place: its own is kept as it was left, so a smaller window for a
     * while (the small window, mini) puts nothing anywhere else once it is big again.
     */
    private static void clampFloating(Pane pane, int screenW, int screenH) {
        pane.targetX = pane.fx;
        pane.targetY = pane.fy;
        pane.targetW = pane.fw;
        pane.targetH = pane.fh;
        if (screenW <= 0 || screenH <= 0) return;
        pane.targetW = Math.max(pane.minW(), Math.min(pane.fw, screenW - 4));
        pane.targetH = Math.max(pane.minH(), Math.min(pane.fh, screenH - 4));
        pane.targetX = Math.max(0, Math.min(pane.fx, screenW - pane.targetW));
        pane.targetY = Math.max(0, Math.min(pane.fy, screenH - pane.targetH));
    }

    // ---- where they were left -----------------------------------------------

    private static void loadPlaces() {
        if (placesLoaded) return;
        placesLoaded = true;
        Workstations.Place[] saved = Workstations.state().readouts;
        if (saved == null) return;
        for (Workstations.Place p : saved) {
            if (p == null || p.id == null) continue;
            PLACES.put(p.id, new float[] {p.x, p.y, p.w, p.h});
            if (p.desk != null) HOMES.put(p.id, p.desk);
            if (p.opacity > 0f && p.opacity < 1f) OPACITY.put(p.id, Math.max(Pane.MIN_OPACITY, p.opacity));
        }
    }

    /** Notes where a pane is, as it looks now -- a tiled one comes back floating in the same spot. */
    private static void remember(Pane pane) {
        String id = pane.readout();
        if (id == null) return;
        if (pane.opacity < 1f) OPACITY.put(id, pane.opacity);
        else OPACITY.remove(id);
        if (pane.floating) PLACES.put(id, new float[] {pane.fx, pane.fy, pane.fw, pane.fh});
        else if (pane.w > 8 && pane.h > 8) PLACES.put(id, new float[] {pane.x, pane.y, pane.w, pane.h});
    }

    /** Writes where every readout pane is into {@code terminal.json}. */
    static void save() {
        loadPlaces();
        for (Desk desk : Desk.all()) for (Pane pane : desk.windows) remember(pane);

        List<Workstations.Place> out = new ArrayList<>();
        for (Map.Entry<String, float[]> e : PLACES.entrySet()) {
            Workstations.Place p = new Workstations.Place();
            p.id = e.getKey();
            p.x = e.getValue()[0];
            p.y = e.getValue()[1];
            p.w = e.getValue()[2];
            p.h = e.getValue()[3];
            p.desk = HOMES.get(e.getKey());
            Float opacity = OPACITY.get(e.getKey());
            p.opacity = opacity == null ? 1f : opacity;
            out.add(p);
        }
        Workstations.state().readouts = out.toArray(new Workstations.Place[0]);
        Workstations.saveState();
    }

    // ---- outside the desktop ------------------------------------------------

    /** Whether any extension's HUD layer has something to draw. */
    private static boolean layersOn() {
        for (HudLayer layer : DeskExtensions.hudLayers()) {
            if (layer.on()) return true;
        }
        return false;
    }

    /**
     * Once a tick: makes sure the toast that draws the panes is up while any readout is on, any
     * pane is pinned or a confirmation ({@link Notify}) is showing.
     */
    public static void onClientTick(Minecraft mc) {
        if (mc.gui == null || !anyOn() && Desk.PINS.isEmpty() && !Notify.active() && !layersOn() && !FriendBanner.active()) return;
        // Pinned terminals go on running what they run (pipes.sh, a bonsai) with the desktop closed.
        if (!TerminalScreen.class.isInstance(com.mineux.core.ui.Overlay.top(mc))) {
            for (Pane pin : new ArrayList<>(Desk.PINS)) pin.tick(true);
        }
        ToastManager toasts = mc.gui.toastManager();
        if (toasts.getToast(HudToast.class, Toast.NO_TOKEN) != null) return;
        toasts.addToast(new HudToast());
    }

    /**
     * The readouts of the open workstation, and every pinned pane ({@code pin}, SUPER+P), drawn
     * over the game while the desktop is closed.
     */
    private static void drawHud(GuiGraphicsExtractor g, Font font) {
        Minecraft mc = Minecraft.getInstance();
        if (TerminalScreen.class.isInstance(com.mineux.core.ui.Overlay.top(mc))) return;
        // The game's overlay, so only in a world: not over the login screen's fade into the boot
        // sequence, nor the title screen's own screens.
        if (mc.level == null) return;

        // Under the readouts: whatever the extensions draw over the game, in the order they were added.
        for (HudLayer layer : DeskExtensions.hudLayers()) {
            if (layer.on()) layer.draw(g, font);
        }

        Desk desk = Desk.of(Workstations.current());
        sync(desk, g.guiWidth(), g.guiHeight());

        long now = System.currentTimeMillis();
        List<Term.Hit> ignored = new ArrayList<>();
        for (Pane pane : desk.windows) {
            if (pane.readout() == null || pane.onAll) continue;
            if (pane.floating) clampFloating(pane, g.guiWidth(), g.guiHeight());
            pane.snap();
            pane.draw(g, font, now, -1, -1, false, ignored, null);
        }

        // Pinned panes and the confirmations are gone with the HUD (F1).
        if (mc.gui.hud.isHidden()) return;
        Notify.draw(g, font, g.guiWidth(), g.guiHeight(), now);
        // News about a friend, at the top: see the note in FriendBanner for why it is not
        // another colour of confirmation card down by the hotbar.
        FriendBanner.draw(g, font, g.guiWidth(), now);
        // Every pinned pane, whichever desk it is on, where it was last shown: floating where it floats, tiled where its tile was.
        for (Pane pin : new ArrayList<>(Desk.PINS)) {
            if (Group.hidden(pin)) continue;
            if (pin.floating) clampFloating(pin, g.guiWidth(), g.guiHeight());
            pin.snap();
            pin.draw(g, font, now, -1, -1, false, ignored, null);
        }
    }

    /**
     * A toast that never goes, and draws the readout panes wherever they are rather than in
     * the top right corner where toasts go.
     */
    static final class HudToast implements Toast {

        private static boolean hudFailed;

        private boolean settled;
        /** Whether {@link #settle} managed to take its slot back; if not, it leaves when idle. */
        private boolean released;

        @Override
        public Visibility getWantedVisibility() {
            return released || anyOn() || !Desk.PINS.isEmpty() || Notify.active() || layersOn() || FriendBanner.active()
                ? Visibility.SHOW : Visibility.HIDE;
        }

        @Override
        public void update(ToastManager manager, long visibleTime) {
        }

        @Override
        public float xPos(int screenWidth, float visiblePortion) {
            return 0;
        }

        @Override
        public float yPos(int firstSlotIndex) {
            return 0;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, Font font, long visibleTime) {
            if (!settled) {
                settled = true;
                released = settle(this);
            }
            // Drawn every frame over the game: a bug in a readout turns the overlay off, logged,
            // rather than crashing the game from its HUD.
            if (hudFailed) return;
            try {
                drawHud(g, font);
            } catch (RuntimeException | LinkageError e) {
                hudFailed = true;
                com.mineux.core.MineUX.LOGGER.error("The readout overlay failed and is off until the game restarts", e);
            }
        }

        /**
         * Gives back the toast slot this took, so real toasts still start at the top, and
         * marks it already shown, so it does not play the toast sound coming in. Best effort:
         * runs the frame after the manager places it, before its first update.
         */
        private static boolean settle(HudToast self) {
            try {
                ToastManager manager = Minecraft.getInstance().gui.toastManager();
                List<?> visible = (List<?>) field(ToastManager.class, "visibleToasts").get(manager);
                BitSet slots = (BitSet) field(ToastManager.class, "occupiedSlots").get(manager);

                for (Object instance : visible) {
                    Class<?> type = instance.getClass();
                    if (field(type, "toast").get(instance) != self) continue;

                    field(type, "visibility").set(instance, Visibility.SHOW);
                    Field first = field(type, "firstSlotIndex");
                    Field count = field(type, "occupiedSlotCount");
                    int from = first.getInt(instance);
                    slots.clear(from, from + count.getInt(instance));
                    // So that if it ever does go, it frees nothing somebody else has since taken.
                    count.setInt(instance, 0);
                    return true;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
            return false;
        }

        private static Field field(Class<?> type, String name) throws NoSuchFieldException {
            Field f = type.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        }
    }
}
