package com.mineux.core.ui.term;

import com.mineux.core.Extensions;
import com.mineux.core.MineUX;
import com.mineux.core.TrayGroup;
import com.mineux.core.ui.term.Term.Hit;
import com.mineux.core.ui.term.Term.Span;
import com.mineux.core.util.Words;
import net.minecraft.client.gui.Font;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.mineux.core.ui.term.Term.*;

/**
 * The module switches in the right of the desktop bar, the way a status bar shows wifi and
 * bluetooth -- grouped by what they are for ({@link TrayGroup}s, added through
 * {@link Extensions#addTrayGroup}, after core's own). Each group is one icon, lit while any
 * of its modules is on; hovering it fades its modules in beside each other under it, an icon
 * each with its name on hover. A click opens the module's settings ({@link ModuleWindow}, tiled
 * on the desk with the rest of its group), a right click flips it. A click on the group icon
 * opens the group's window, a right click on it turns the whole group off and on again.
 * <p>
 * <b>A group off.</b> Every module of it that was on is switched off for real and remembered
 * ({@link TrayConfig}, per workstation); in the strip and the group's window they keep showing
 * what they were, and a right click on one only changes whether it comes back. Turned on again,
 * the group comes back exactly as it was. Switching one of its modules on from elsewhere (a key,
 * the terminal) wakes the group the same way.
 * <p>
 * Turning a module on from here turns on the switch above it as well, so a module is never left
 * switched on under a parent that is off and doing nothing.
 * <p>
 * <b>On the main menu.</b> An item's components are only bound once the game has loaded its
 * data, which is the first time a world is joined; until then even {@code new ItemStack(item)}
 * throws ("Components not bound yet"). So the tray binds its own icons' components itself, off
 * the render thread, from vanilla's built-in registries -- what the data generator does, and
 * only for these few items. Joining a world binds them again properly. Until that is done, or if
 * it fails, each shows its initial.
 */
public final class Tray {

    /**
     * One module of a group: its path in the tree, its label and its icon, and whether it is in the
     * bar's strip as well as in the group's window. A path that starts with {@link #COMMAND} runs
     * the rest as a command rather than naming a module.
     */
    record Row(String path, String label, Item item, boolean tray) {
        Row(String path, String label, Item item) {
            this(path, label, item, true);
        }

        Node node() {
            return Modules.at(path);
        }
    }

    /**
     * One icon in the bar, the modules under it, and a line on what they are for; a single row
     * with no label is a plain switch. {@code special}: in the bar on the special workspace only.
     */
    record Group(String key, String label, Item item, List<Row> rows, String blurb, boolean special) {}

    /** What a row's path starts with when it is a command rather than a module. */
    static final String COMMAND = "!";

    private static final int SLOT_W = 15;
    /** One module's cell in a group's row. */
    private static final int CELL = 17;
    /** How long a hovered row stays open after the mouse leaves it, so moving down onto it never shuts it. */
    private static final long GRACE_MS = 220L;
    private static final long FADE_MS = 130L;

    private static List<Group> groups;
    private static Map<Item, ItemStack> stacks;
    private static boolean binding;

    /** The open group, since when, and when the mouse was last on it. */
    private static String openKey;
    private static long openSince;
    private static long lastInside;
    /** The open row's box from the last frame, for the hover check: x0, y0, x1, y1. */
    private static final int[] openBox = new int[4];

    private Tray() {}

    static List<Group> groups() {
        if (groups == null || seenGroups != Extensions.trayGroups().size()) {
            List<Group> out = new ArrayList<>();
            // Core's own: the client around the game. Friends is not here: it is the friends
            // command's (friends on/off), so that with every module off nothing in the bar is lit.
            out.add(new Group("client", "client", Items.COMPARATOR, List.of(
                new Row("rpc", "discord status", Items.NOTE_BLOCK)),
                "the client around the game: your discord status", false));
            for (TrayGroup group : Extensions.trayGroups()) out.add(of(group));
            groups = added(out);
            seenGroups = Extensions.trayGroups().size();
        }
        return groups;
    }

    private static int seenGroups = -1;

    /** The groups with the rows and blurbs extensions added to them ({@code DeskExtensions.addTrayRow}, {@code setTrayBlurb}). */
    private static List<Group> added(List<Group> groups) {
        if (com.mineux.core.DeskExtensions.trayRows().isEmpty() && com.mineux.core.DeskExtensions.trayBlurbs().isEmpty()) return groups;
        List<Group> out = new ArrayList<>();
        for (Group group : groups) {
            List<Row> rows = new ArrayList<>(group.rows());
            for (com.mineux.core.DeskExtensions.TrayAddition add : com.mineux.core.DeskExtensions.trayRows()) {
                if (!group.key().equals(add.group())) continue;
                TrayGroup.Row r = add.row();
                Row row = new Row(r.path(), r.label(), item(r.icon()), r.inStrip());
                int at = add.index() < 0 || add.index() > rows.size() ? rows.size() : add.index();
                rows.add(at, row);
            }
            String blurb = com.mineux.core.DeskExtensions.trayBlurbs().get(group.key());
            out.add(new Group(group.key(), group.label(), group.item(), List.copyOf(rows), blurb == null ? group.blurb() : blurb, group.special()));
        }
        return out;
    }

    /** An extension's group as the bar draws it. */
    private static Group of(TrayGroup group) {
        List<Row> rows = new ArrayList<>();
        for (TrayGroup.Row row : group.rows()) rows.add(new Row(row.path(), row.label(), item(row.icon()), row.inStrip()));
        String blurb = group.blurb() == null ? "" : group.blurb();
        return new Group(group.name(), group.label(), item(group.icon()), List.copyOf(rows), blurb, group.specialOnly());
    }

    /** The item an id names; paper for one that names none. */
    private static Item item(String id) {
        Identifier key = id == null ? null : Identifier.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return Items.PAPER;
        return BuiltInRegistries.ITEM.getValue(key);
    }

    /** An item's icon, or null while item components are not bound yet. */
    private static ItemStack stack(Item item) {
        if (stacks == null) {
            Set<Item> wanted = wanted();
            for (Item each : wanted) {
                if (!each.builtInRegistryHolder().areComponentsBound()) {
                    bindEarly(wanted);
                    return null;
                }
            }
            Map<Item, ItemStack> out = new HashMap<>();
            for (Item each : wanted) out.put(each, new ItemStack(each));
            stacks = out;
        }
        return stacks.get(item);
    }

    private static Set<Item> wanted() {
        Set<Item> wanted = new HashSet<>(icons().values());
        for (Group group : groups()) {
            wanted.add(group.item());
            for (Row row : group.rows()) wanted.add(row.item());
        }
        return wanted;
    }

    /**
     * Binds the tray items' components before any world has, on a worker: building vanilla's
     * registries takes a moment. Only items not bound yet, so a world's own binding is never
     * overwritten.
     */
    private static synchronized void bindEarly(Set<Item> wanted) {
        if (binding) return;
        binding = true;
        bind(wanted);
    }

    private static boolean bindingAll;

    /** Every item's components bound early, so a list of items can be drawn on the main menu (before a world has bound them). */
    public static synchronized void bindAllEarly() {
        if (bindingAll) return;
        bindingAll = true;
        bind(null);
    }

    /** The binding itself, on a worker: {@code wanted} only, or every item for null. */
    private static void bind(Set<Item> wanted) {
        Thread worker = new Thread(() -> {
            try {
                HolderLookup.Provider lookup = VanillaRegistries.createLookup();
                for (DataComponentInitializers.PendingComponents<?> pending : BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup)) {
                    pending.forEach((holder, components) -> {
                        // isInstance and a cast: the obfuscator breaks instanceof against Item's subclasses.
                        Object value = holder.value();
                        if (Item.class.isInstance(value) && (wanted == null || wanted.contains((Item) value)) && !holder.areComponentsBound()) {
                            holder.bindComponents(components);
                        }
                    });
                }
            } catch (RuntimeException | LinkageError e) {
                // The letters stay; a world binds the real components when one is joined.
                MineUX.LOGGER.warn("Could not prepare the tray icons early", e);
            }
        }, "mineux-tray-icons");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * A module's icon as its settings window draws it, big; null for a module with no icon, or
     * while item components are not bound yet.
     */
    static ItemStack stackFor(String module) {
        Item item = icons().get(module);
        return item == null ? null : stack(item);
    }

    /** A module's icon wherever it is: its row's in a group, or its own; null while components are not bound. */
    static ItemStack stackFor(String label, Node node) {
        String path = node.path().length() > 2 ? node.path().substring(2) : node.name;
        for (Group group : groups()) {
            for (Row row : group.rows()) {
                if (Objects.equals(row.path(), path)) return stack(row.item());
            }
        }
        return stackFor(node.name);
    }

    /**
     * Makes the icons now -- binds the items' components early on the main menu, or builds the
     * stacks -- so the first hover over the bar never shows the letters standing in for them.
     */
    static void prepare() {
        stack(Items.COMPARATOR);
    }

    /** Whether a node is a module of some group, with a page of its own in that group's window. */
    static boolean isRow(Node node) {
        String path = node.path().length() > 2 ? node.path().substring(2) : node.name;
        for (Group group : groups()) {
            for (Row row : group.rows()) {
                if (Objects.equals(row.path(), path)) return true;
            }
        }
        return false;
    }

    /** Where a module's group icon was last drawn in the bar, as its centre x; -1 before it has been. */
    static int centreOf(String module) {
        Integer x = CENTRES.get(module);
        return x == null ? -1 : x;
    }

    private static final Map<String, Integer> CENTRES = new HashMap<>();

    /**
     * Top-level module to the icon its settings window shows, built on first use rather than
     * when this class loads, so the items are only asked for once the registries are up. A
     * table, not a switch on the module name: see {@link Words} for why this code has none.
     */
    private static Map<String, Item> icons() {
        if (ICONS == null) {
            Map<String, Item> map = new HashMap<>();
            for (Group group : groups()) map.putIfAbsent(group.key(), group.item());
            map.put("rpc", Items.NOTE_BLOCK);
            ICONS = map;
        }
        return ICONS;
    }

    private static Map<String, Item> ICONS;

    // ---- drawing --------------------------------------------------------

    /**
     * The groups, right to left from {@code right}, in a bar {@code barH} tall, and the open
     * group's rows under it. Each row is a hit whose span flips its module quietly, or opens the
     * switch editor; the screen answers a right click on it by opening the module's settings
     * window under the group.
     *
     * @return the x the tray starts at
     */
    static int draw(GuiGraphicsExtractor g, Font font, int right, int barH, int screenW, int mouseX, int mouseY, List<Hit> hits) {
        List<Group> all = groups();
        List<int[]> dim = new ArrayList<>();
        long now = System.currentTimeMillis();
        int x = right;
        Group open = null;
        int openX = 0;
        boolean special = Workstations.isSpecial(Workstations.current());

        for (int i = all.size() - 1; i >= 0; i--) {
            Group group = all.get(i);
            if (!special && group.special() || group.rows().isEmpty()) continue;
            boolean single = group.label() == null;
            Node node = single ? group.rows().get(0).node() : null;
            if (single && (node == null || node.toggle == null)) continue;

            // A module of a group that is off, switched on from somewhere else (a key, the
            // terminal), wakes the group: everything it held comes back with it.
            if (!single && parked(group) && anyReallyOn(group)) wake(group);
            boolean parked = !single && parked(group);

            x -= SLOT_W;
            int centre = x + SLOT_W / 2;
            CENTRES.put(group.key(), centre);
            for (Row row : group.rows()) CENTRES.put(top(row.path()), centre);

            boolean on = single ? node.on() : !parked && anyOn(group);
            boolean hot = mouseX >= x && mouseX < x + SLOT_W && mouseY < barH - 1;
            boolean isOpen = !single && Objects.equals(openKey, group.key());

            if (hot && !single) {
                if (!isOpen) {
                    openKey = group.key();
                    openSince = now;
                    isOpen = true;
                }
                lastInside = now;
            }

            if (on) {
                // A glow under the icon and a lit rule along the bottom, like a connected radio.
                g.fillGradient(x, 0, x + SLOT_W, barH - 1, 0x10EFC648, 0x48EFC648);
                g.fill(x + 2, barH - 3, x + SLOT_W - 2, barH - 1, GOLD);
            }
            if (hot || isOpen) g.fill(x, 0, x + SLOT_W, barH - 1, 0x26FFFFFF);

            ItemStack stack = stack(group.item());
            if (stack != null) {
                icon(g, stack, x + 1.5f, 2, 0.75f);
            } else {
                String letter = (single ? group.key() : group.label()).substring(0, 1).toUpperCase(Locale.ROOT);
                g.text(font, letter, x + (SLOT_W - font.width(letter)) / 2, (barH - 8) / 2, on ? GOLD : FG, false);
            }
            if (!on) dim.add(new int[] {x, 1, x + SLOT_W, barH - 2, parked ? 1 : 0});

            if (single) {
                hits.add(new Hit(x, 0, x + SLOT_W, barH - 1, null, rowSpan(group.rows().get(0), node), -1, null, ACT_TRAY));
                if (hot) tooltip(g, font, group.rows().get(0).label(), mouseX, mouseY);
            } else {
                // A click opens the group's window, on its first module; a right click turns the whole group off or on.
                Span span = new Span(group.rows().get(0).path(), FG);
                span.run = "open " + group.rows().get(0).path();
                span.quiet = true;
                span.action = () -> flipGroup(group);
                hits.add(new Hit(x, 0, x + SLOT_W, barH - 1, null, span, -1, null, ACT_TRAY));
                if (hot) tooltip(g, font, parked ? group.label() + " - off" : group.label(), mouseX, mouseY);
                if (isOpen) {
                    open = group;
                    openX = x;
                }
            }
        }

        // Grey out whatever is off, a group switched off darker still. On a new stratum, so it
        // lands over the item sprites whatever order the renderer would otherwise draw items and fills in.
        if (!dim.isEmpty()) {
            g.nextStratum();
            for (int[] r : dim) g.fill(r[0], r[1], r[2], r[3], r[4] == 1 ? 0xD80A0B0F : 0xB00A0B0F);
        }

        if (open != null) {
            boolean inside = mouseX >= openBox[0] && mouseX < openBox[2] && mouseY >= openBox[1] && mouseY < openBox[3];
            if (inside) lastInside = now;
            if (now - lastInside > GRACE_MS) {
                openKey = null;
            } else {
                drawRows(g, font, open, openX, barH, screenW, now, mouseX, mouseY, hits);
            }
        } else if (openKey != null) {
            openKey = null;
        }

        return x;
    }

    /** Whether a point is on the open group's rows. */
    static boolean over(double x, double y) {
        return openKey != null && x >= openBox[0] && x < openBox[2] && y >= openBox[1] && y < openBox[3];
    }

    /** The most cells side by side under a group's icon; a bigger group wraps onto another line. */
    private static final int COLS = 8;

    /**
     * The open group's modules in a grid under its icon, fading in. The items are drawn from the
     * very first frame, growing into place -- a sprite cannot be faded, and the letters that
     * stood in while the box faded in flashed up on every hover.
     */
    private static void drawRows(GuiGraphicsExtractor g, Font font, Group group, int iconX, int barH, int screenW,
                                 long now, int mouseX, int mouseY, List<Hit> hits) {
        float t = Math.min(1f, (now - openSince) / (float) FADE_MS);
        float ease = 1f - (1f - t) * (1f - t);
        int alpha = Math.round(ease * 255f);
        boolean parked = parked(group);

        List<Row> shown = new ArrayList<>();
        for (Row row : group.rows()) if (row.tray()) shown.add(row);
        int cols = Math.max(1, Math.min(COLS, shown.size()));
        int lines = (shown.size() + cols - 1) / cols;
        int w = cols * CELL + 4;
        int x1 = Math.min(screenW - 2, iconX + SLOT_W + 2);
        int x0 = Math.max(2, x1 - w);
        x1 = x0 + w;
        int y0 = barH + Math.round((1f - ease) * -4f);
        int y1 = y0 + lines * CELL + 4;
        openBox[0] = x0;
        openBox[1] = barH - 1;
        openBox[2] = x1;
        openBox[3] = y1;

        // Over the windows below the bar, item sprites and all.
        g.nextStratum();
        g.fill(x0, y0, x1, y1, fade(0xF00C0D12, alpha));
        int edge = parked ? 0xFF4A2A2C : 0xFF2A2C38;
        g.fill(x0, y0, x1, y0 + 1, fade(edge, alpha));
        g.fill(x0, y1 - 1, x1, y1, fade(edge, alpha));
        g.fill(x0, y0, x0 + 1, y1, fade(edge, alpha));
        g.fill(x1 - 1, y0, x1, y1, fade(edge, alpha));

        float grow = 0.55f + 0.45f * ease;
        List<int[]> dim = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) {
            Row row = shown.get(i);
            int x = x0 + 2 + (i % cols) * CELL;
            int y = y0 + 2 + (i / cols) * CELL;
            boolean switches = row.path().startsWith(COMMAND);
            Node node = switches ? null : row.node();
            boolean toggles = node != null && node.toggle != null;
            // In a group that is off, a module shows what it will come back as.
            boolean on = toggles && shownOn(node);
            boolean hot = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;

            if (on) {
                g.fillGradient(x, y, x + CELL, y + CELL, fade(0x10EFC648, alpha), fade(0x48EFC648, alpha));
                g.fill(x + 2, y + CELL - 2, x + CELL - 2, y + CELL, fade(GOLD, alpha));
            }
            if (hot) g.fill(x, y, x + CELL, y + CELL, fade(0x26FFFFFF, alpha));

            ItemStack stack = stack(row.item());
            if (stack != null) {
                float s = 0.75f * grow;
                icon(g, stack, x + 8.5f - 8f * s, y + 8.5f - 8f * s, s);
            } else if (alpha > 8) {
                // Only while the items' components are not bound yet (the main menu, the first moment).
                String letter = row.label().substring(0, 1).toUpperCase(Locale.ROOT);
                g.text(font, letter, x + (CELL - font.width(letter)) / 2, y + 5, fade(on ? GOLD : FG, alpha), false);
            }
            // Greyed unless it runs: one with no switch (a key, an editor) never lights up.
            if (!on) dim.add(new int[] {x + 1, y + 1, x + CELL - 1, y + CELL - 1});

            if (switches) {
                Span span = new Span(row.path(), FG);
                span.run = row.path().substring(COMMAND.length());
                span.quiet = true;
                hits.add(new Hit(x, y, x + CELL, y + CELL, null, span, -1, null, ACT_TRAY));
                if (hot) tooltip(g, font, row.label(), mouseX, mouseY);
            } else if (node != null) {
                hits.add(new Hit(x, y, x + CELL, y + CELL, null, rowSpan(row, node), -1, null, ACT_TRAY));
                if (hot) tooltip(g, font, parked ? row.label() + " (" + group.label() + " is off)" : row.label(), mouseX, mouseY);
            }
        }

        if (!dim.isEmpty()) {
            g.nextStratum();
            for (int[] r : dim) g.fill(r[0], r[1], r[2], r[3], fade(0xB00A0B0F, alpha));
        }
    }

    /** A row's span: a click opens its settings ({@code run}), a right click flips it ({@code action}). */
    private static Span rowSpan(Row row, Node node) {
        Span span = new Span(row.path(), FG);
        span.quiet = true;
        span.run = "open " + row.path();
        if (node.toggle != null) span.action = () -> flip(node);
        return span;
    }

    /**
     * Flips a module; turning it on turns on the tab switches above it too. In a group that is
     * switched off it only changes what comes back when the group does: nothing runs.
     */
    static void flip(Node node) {
        String path = pathOf(node);
        if (parkedGroupOf(path) != null) {
            TrayConfig config = TrayConfig.get();
            if (!config.held.remove(path)) config.held.add(path);
            config.save();
            return;
        }
        boolean turningOn = !node.on();
        if (turningOn) {
            for (Node up = node.parent; up != null; up = up.parent) {
                if (up.toggle != null && !up.on()) up.toggle.set("on");
            }
        }
        node.toggle.set(turningOn ? "on" : "off");
    }

    // ---- a whole group off and on ---------------------------------------

    /** Whether a group is switched off from its icon. */
    static boolean parked(Group group) {
        return TrayConfig.get().off.contains(group.key());
    }

    /**
     * Turns a whole group off -- every module of it that is on goes off for real, and is
     * remembered -- or back on, with exactly those modules on again. The modules' own switches
     * are left showing what they were in the tray and the group's window.
     */
    static void flipGroup(Group group) {
        if (parked(group)) wake(group);
        else park(group);
    }

    private static void park(Group group) {
        TrayConfig config = TrayConfig.get();
        if (!config.off.contains(group.key())) config.off.add(group.key());
        // Remembered first, switched off after: switching a parent off first would not change what a child says, but be safe.
        List<Node> going = new ArrayList<>();
        for (Row row : group.rows()) {
            Node node = toggled(row);
            if (node == null || !node.on()) continue;
            if (!config.held.contains(row.path())) config.held.add(row.path());
            going.add(node);
        }
        config.save();
        for (Node node : going) node.toggle.set("off");
    }

    private static void wake(Group group) {
        TrayConfig config = TrayConfig.get();
        config.off.remove(group.key());
        List<Node> coming = new ArrayList<>();
        for (Row row : group.rows()) {
            if (!config.held.remove(row.path())) continue;
            Node node = toggled(row);
            if (node != null && !node.on()) coming.add(node);
        }
        config.save();
        for (Node node : coming) node.toggle.set("on");
    }

    /** Whether any module of a group is really switched on, whatever the tab switches above it say. */
    private static boolean anyReallyOn(Group group) {
        for (Row row : group.rows()) {
            Node node = toggled(row);
            if (node != null && node.on()) return true;
        }
        return false;
    }

    /** A row's module if it has a switch, else null. */
    private static Node toggled(Row row) {
        if (row.path().startsWith(COMMAND)) return null;
        Node node = row.node();
        return node != null && node.toggle != null ? node : null;
    }

    /** The switched-off group a module path is a row of, or null. */
    private static Group parkedGroupOf(String path) {
        TrayConfig config = TrayConfig.get();
        if (config.off.isEmpty()) return null;
        for (Group group : groups()) {
            if (!config.off.contains(group.key())) continue;
            for (Row row : group.rows()) if (Objects.equals(row.path(), path)) return group;
        }
        return null;
    }

    /**
     * Whether a module shows as on: in a group that is switched off, whether it comes back on with
     * it; otherwise whether it really runs (on, with every tab switch above it on).
     */
    static boolean shownOn(Node node) {
        if (node.toggle == null) return false;
        String path = pathOf(node);
        if (parkedGroupOf(path) != null) return TrayConfig.get().held.contains(path);
        return node.on() && parentsOn(node);
    }

    /** What a module's own switch shows: in a group switched off, whether it comes back on; otherwise whether it is on. */
    static boolean switchShows(Node node) {
        if (node.toggle == null) return false;
        String path = pathOf(node);
        if (parkedGroupOf(path) != null) return TrayConfig.get().held.contains(path);
        return node.on();
    }

    /** Whether a module is a row of a group switched off from the tray. */
    static boolean isParked(Node node) {
        return parkedGroupOf(pathOf(node)) != null;
    }

    /** A node's path from the root without the {@code ~/}: {@code clicker/reach}. */
    private static String pathOf(Node node) {
        return node.path().length() > 2 ? node.path().substring(2) : node.name;
    }

    /** The name and nothing else: whether it is on, the icon already shows. */
    private static void tooltip(GuiGraphicsExtractor g, Font font, String label, int mouseX, int mouseY) {
        // The tooltip is drawn 12 above the mouse with a 4 wide frame around it, and
        // nothing keeps it under the top of the screen: this close to the top, push it down.
        g.setComponentTooltipForNextFrame(font, List.of(Component.literal(label)), mouseX, Math.max(mouseY, 17));
    }

    private static boolean anyOn(Group group) {
        for (Row row : group.rows()) {
            Node node = toggled(row);
            if (node != null && node.on() && parentsOn(node)) return true;
        }
        return false;
    }

    /** Whether every tab switch above a module is on, so that it really runs. */
    static boolean parentsOn(Node node) {
        for (Node up = node.parent; up != null; up = up.parent) {
            if (up.toggle != null && !up.on()) return false;
        }
        return true;
    }

    /** The top-level module a path is in: {@code swap} for {@code swap/lunge}. */
    private static String top(String path) {
        int slash = path.indexOf('/');
        return slash < 0 ? path : path.substring(0, slash);
    }

    private static int fade(int argb, int alpha) {
        int a = (argb >>> 24) * alpha / 255;
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** An item sprite at {@code scale} (three quarters sits inside the bar). */
    private static void icon(GuiGraphicsExtractor g, ItemStack stack, float x, float y, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.item(stack, 0, 0);
        g.pose().popMatrix();
    }
}
