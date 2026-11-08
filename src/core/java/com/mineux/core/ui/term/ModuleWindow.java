package com.mineux.core.ui.term;

import com.mineux.core.ui.term.Term.Hit;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.mineux.core.ui.term.Term.*;

/**
 * A module's settings, as a window of the desk: tiled in on the right like the switch editor
 * ({@link SwitchPane}) and drawn the same way, in the terminal's own colours. One window per
 * tray group ({@link Tray#groups}) -- combat, mace, crystal, invswap -- with the group's modules
 * down the left; a module outside every group gets one of its own, its own sections down the left.
 * <p>
 * Left, the modules: a click shows one, a right click flips it, lit while it runs. Right, the one
 * shown: its name and switch, what it does, and a line per setting in {@code modules.txt} -- on/off,
 * a choice of words, a key (click, then press it; right click clears), a number (click to type it,
 * drag its track), text (click to type). The sections inside a module that are not on the left
 * (the anchor's auto aim) follow under it with their own switches.
 * <p>
 * Every row writes through the same {@link Setting} the terminal does. Turning a module on turns
 * on the old tab switch above it too ({@link Tray#flip}).
 */
final class ModuleWindow extends Pane {

    /** The kind of this window, for {@code Desktop#openWindow}: the path it was opened on follows. */
    static final String KIND = "module:";

    private static final int HEADER_H = 12;
    private static final int PAD = 5;
    private static final int LINE_H = 12;
    private static final int LIST_W = 92;
    private static final long SLIDE_MS = 240;

    /** One page: a module, what the list calls it, and whether its sections are pages of their own. */
    record Page(Node node, String label) {}

    /** Which window this is: a group's key, or the name of a module of its own. */
    final String key;
    private final String title;
    private final String subtitle;
    private final List<Page> pages;
    private Desktop desktop;
    private int selected;

    private Setting listening;
    private Setting editing;
    private String buffer = "";
    private String status = "";
    private int statusColor = DIM;
    private long statusAt;
    private long openedAt;
    private float scroll;
    private int maxScroll;
    /** The colour picker open under a colour row ({@link ColourPicker}), and whether a row drew it this frame. */
    private ColourPicker picker;
    private boolean pickerDrawn;
    private int lastMouseX, lastMouseY;

    private final Map<String, Span> spans = new HashMap<>();
    /** The modules whose "advanced" section is open, by path; closed to start with. */
    private final java.util.Set<String> advancedOpen = new java.util.HashSet<>();

    private ModuleWindow(Desktop desktop, String key, String title, String subtitle, List<Page> pages) {
        this.desktop = desktop;
        this.key = key;
        this.title = title;
        this.subtitle = subtitle;
        this.pages = pages;
        this.w = 1;
        this.h = 1;
    }

    // ---- which window a path opens ------------------------------------------

    /** The key of the window a path opens in: its group's, or its top module's name; null for no module. */
    static String keyFor(String path) {
        Tray.Group group = groupOf(path);
        if (group != null) return group.key();
        Node node = Modules.at(path);
        if (node == null || node.parent == null) return null;
        return top(node).name;
    }

    /** A new window for a path. */
    static ModuleWindow open(Desktop desktop, String path) {
        Tray.Group group = groupOf(path);
        ModuleWindow window;
        if (group != null) {
            List<Page> pages = new ArrayList<>();
            for (Tray.Row row : group.rows()) {
                Node node = row.path().startsWith(Tray.COMMAND) ? null : row.node();
                if (node != null) pages.add(new Page(node, row.label()));
            }
            window = new ModuleWindow(desktop, group.key(), group.label(), group.blurb(), pages);
        } else {
            Node module = top(Modules.at(path));
            List<Page> pages = new ArrayList<>();
            pages.add(new Page(module, module.name));
            for (Node child : module.children) pages.add(new Page(child, child.name));
            window = new ModuleWindow(desktop, module.name, module.name, module.desc, pages);
        }
        window.show(path);
        return window;
    }

    /** Shows the page a path is on. */
    void show(String path) {
        Node node = Modules.at(path);
        if (node == null) return;
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).node() == node) {
                select(i);
                return;
            }
        }
        // A section inside a page: that page.
        for (int i = 0; i < pages.size(); i++) {
            if (inside(node, pages.get(i).node())) {
                select(i);
                return;
            }
        }
    }

    private static Tray.Group groupOf(String path) {
        for (Tray.Group group : Tray.groups()) {
            if (group.label() == null) continue;
            for (Tray.Row row : group.rows()) {
                if (Objects.equals(row.path(), path) || path.startsWith(row.path() + "/")) return group;
                // A dir above a group's rows (a tab whose modules are rows): opening it opens their group.
                if (row.path().startsWith(path + "/")) return group;
            }
        }
        return null;
    }

    private static Node top(Node node) {
        Node module = node;
        while (module.parent != null && module.parent.parent != null) module = module.parent;
        return module;
    }

    private static boolean inside(Node node, Node page) {
        for (Node n = node; n != null; n = n.parent) if (n == page) return true;
        return false;
    }

    // ---- the pane -------------------------------------------------------------

    @Override
    int minW() {
        return 260;
    }

    @Override
    int minH() {
        return 150;
    }

    @Override
    int header() {
        return HEADER_H;
    }

    @Override
    boolean takesKeys() {
        return capturing();
    }

    @Override
    boolean capturing() {
        return listening != null || editing != null || picker != null;
    }

    @Override
    boolean typing() {
        return editing != null || picker != null;
    }

    @Override
    void attach(Desktop screen) {
        this.desktop = screen;
    }

    @Override
    void close() {
        desktop.closeWindow(this);
    }

    @Override
    String title() {
        return title;
    }

    @Override
    void scrolled(double mouseY, double amount) {
        scroll = Math.max(0f, Math.min(maxScroll, scroll - (float) amount * 18f));
    }

    private void select(int index) {
        if (index == selected) return;
        selected = Math.max(0, Math.min(pages.size() - 1, index));
        scroll = 0f;
        calm();
    }

    private void said(String error, String ok) {
        status = error == null ? (ok == null ? "" : ok) : error;
        statusColor = error == null ? GREEN : RED;
        statusAt = System.currentTimeMillis();
    }

    private void calm() {
        listening = null;
        editing = null;
        // A click on anything else keeps the colour being picked.
        if (picker != null) picker.keep();
        picker = null;
    }

    /** The keyboard has gone to another window: whatever waited for a key or was being typed stops. */
    private void blur() {
        listening = null;
        editing = null;
        if (picker != null) picker.blur();
    }

    // ---- drawing ----------------------------------------------------------------

    @Override
    void render(GuiGraphicsExtractor g, Font font, long now, int mouseX, int mouseY, boolean focused,
                List<Hit> hits, Hit hovered) {
        if (openedAt == 0L) openedAt = now;
        int ww = iw(), wh = ih();
        if (ww < 8 || wh < 8) return;
        if (!focused) blur();
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        pickerDrawn = false;

        // Slides in from the right edge as it opens, as the switch editor does.
        float t = Math.min(1f, (now - openedAt) / (float) SLIDE_MS);
        float eased = 1f - (1f - t) * (1f - t) * (1f - t);
        int wx = ix() + Math.round((1f - eased) * ww * 0.6f);
        int wy = iy();

        g.enableScissor(ix(), wy, ix() + ww, wy + wh);
        g.fillGradient(wx, wy, wx + ww, wy + wh, 0xC0121620, 0xD007080C);
        Decor.border(g, this, wx, wy, ww, wh, focused, 0);
        hits.add(new Hit(wx, wy, wx + ww, wy + wh, null, null, -1, null, ACT_BODY));

        header(g, font, wx, wy, ww, mouseX, mouseY, hits);

        int top = wy + HEADER_H + 3;
        list(g, font, wx + PAD, top, wh - HEADER_H - 6, hits, hovered);
        g.fill(wx + PAD + LIST_W, top, wx + PAD + LIST_W + 1, wy + wh - 4, 0x1CFFFFFF);

        int ex = wx + PAD + LIST_W + 6;
        int ew = ww - LIST_W - PAD * 2 - 6;
        if (!pages.isEmpty()) page(g, font, pages.get(selected), ex, top, ew, wy + wh - 4, focused, now, mouseX, mouseY, hits, hovered);
        if (picker != null && (picker.done() || !pickerDrawn)) {
            // Kept, put back, or its row went out of the window (another page, the advanced rows closed).
            picker.keep();
            picker = null;
        }

        g.disableScissor();
        drawGrip(g, onGrip(mouseX, mouseY));
    }

    private void header(GuiGraphicsExtractor g, Font font, int wx, int wy, int ww, int mouseX, int mouseY, List<Hit> hits) {
        g.fill(wx, wy, wx + ww, wy + HEADER_H, 0x40000000);
        g.fill(wx, wy + HEADER_H - 1, wx + ww, wy + HEADER_H, 0x1CFFFFFF);
        g.text(font, title, wx + PAD, wy + 2, GOLD, false);
        int sx = wx + PAD + font.width(title) + 6;
        g.text(font, font.plainSubstrByWidth(subtitle, Math.max(0, ww - (sx - wx) - 20)), sx, wy + 2, FAINT, false);
        String close = "x";
        int cx = wx + ww - PAD - font.width(close);
        boolean hot = mouseX >= cx - 3 && mouseX < cx + font.width(close) + 3 && mouseY >= wy && mouseY < wy + HEADER_H - 1;
        if (hot) g.fill(cx - 3, wy, cx + font.width(close) + 3, wy + HEADER_H - 1, 0x40FF5C57);
        g.text(font, close, cx, wy + 2, hot ? RED : DIM, false);
        Span closeSpan = span("close", close, RED, "close (esc)", null);
        hits.add(new Hit(cx - 3, wy, cx + font.width(close) + 3, wy + HEADER_H - 1, null, closeSpan, -1, null, ACT_CLOSE));
    }

    /** The modules: a click shows one, a right click flips it. */
    private void list(GuiGraphicsExtractor g, Font font, int x, int y, int height, List<Hit> hits, Hit hovered) {
        int row = y;
        for (int i = 0; i < pages.size(); i++) {
            if (row + LINE_H > y + height) break;
            Page page = pages.get(i);
            Node node = page.node();
            boolean on = running(node);
            boolean picked = i == selected;
            if (picked) g.fill(x - 2, row - 1, x + LIST_W - 3, row + LINE_H - 2, 0x30EFC648);

            ItemStack stack = Tray.stackFor(page.label(), node);
            if (stack != null) {
                g.pose().pushMatrix();
                g.pose().translate(x, row - 2);
                g.pose().scale(0.6f, 0.6f);
                g.item(stack, 0, 0);
                g.pose().popMatrix();
            }
            int index = i;
            String hint = node.toggle == null ? "show it" : "show it - right click turns it " + (on ? "off" : "on");
            int color = on ? GOLD : DIM;
            button(g, font, hits, hovered, "page:" + i, font.plainSubstrByWidth(page.label(), LIST_W - 26), x + 12, row, color, hint,
                () -> {
                    if (Term.clickButton == 1 && node.toggle != null) flip(node);
                    else select(index);
                });
            if (node.toggle != null) g.text(font, on ? "*" : "", x + LIST_W - 10, row, GREEN, false);
            row += LINE_H;
        }
    }

    /** One module: its head, its rows, and its sections under them. */
    private void page(GuiGraphicsExtractor g, Font font, Page page, int x, int y, int w, int bottom, boolean focused, long now,
                      int mouseX, int mouseY, List<Hit> hits, Hit hovered) {
        Node node = page.node();
        int right = x + w;

        // The head stays put; the rows under it scroll.
        int row = y;
        g.text(font, page.label(), x, row, GOLD, false);
        if (node.toggle != null) {
            // In a group switched off from the tray: what it comes back as, and saying so.
            boolean on = Tray.switchShows(node);
            boolean parked = Tray.isParked(node);
            String state = on ? "[ON ]" : "[off]";
            button(g, font, hits, hovered, "master:" + node.path(), state, right - font.width(state), row, on ? GREEN : DIM,
                parked ? "its group is off: " + (on ? "stays off when it comes back" : "comes back on with it") : "turn it " + (on ? "off" : "on"),
                () -> flip(node));
        }
        row += LINE_H;
        for (String line : wrap(font, node.desc, w, 3)) {
            g.text(font, line, x, row, FAINT, false);
            row += 10;
        }
        row += 2;
        g.fill(x, row, right, row + 1, 0x1CFFFFFF);
        row += 4;

        int bodyTop = row;
        int statusRoom = 11;
        int bodyBottom = bottom - statusRoom;
        g.enableScissor(x - 2, bodyTop, right + 2, bodyBottom);
        int start = bodyTop - Math.round(scroll);
        int end = block(g, font, node, page, x, start, w, bodyTop, bodyBottom, focused, now, hits, hovered, 0);
        // The old tab above it (clicker, knockback, swap) is no page of its own any more: its
        // own rows -- the tab's debug panel -- go at the foot of each of its modules.
        Node tab = node.parent;
        if (tab != null && tab.parent != null && tab.parent.parent == null && tab.toggle != null && !isPage(tab)) {
            for (Setting s : tab.settings) {
                end = setting(g, font, s, x, end, w, bodyTop, bodyBottom, focused, now, hits, hovered);
            }
        }
        g.disableScissor();
        maxScroll = Math.max(0, end - start - (bodyBottom - bodyTop));
        scroll = Math.min(scroll, maxScroll);
        if (maxScroll > 0) {
            int view = bodyBottom - bodyTop;
            int barH = Math.max(8, view * view / (view + maxScroll));
            int barY = bodyTop + Math.round((view - barH) * (scroll / maxScroll));
            g.fill(right + 1, barY, right + 2, barY + barH, 0x50EFC648);
        }

        if (!status.isEmpty() && now - statusAt < 4000) {
            g.text(font, font.plainSubstrByWidth(status, w), x, bottom - 9, statusColor, false);
        } else if (listening != null) {
            g.text(font, "press a key - esc cancels, backspace clears", x, bottom - 9, AMBER, false);
        } else if (editing != null) {
            g.text(font, "type - enter sets, esc cancels", x, bottom - 9, AMBER, false);
        } else if (picker != null) {
            g.text(font, font.plainSubstrByWidth(picker.status(), w), x, bottom - 9, AMBER, false);
        }
    }

    /** A module's rows, then each of its sections that is not a page of its own; returns where they end. */
    private int block(GuiGraphicsExtractor g, Font font, Node node, Page page, int x, int y, int w, int clipTop, int clipBottom,
                      boolean focused, long now, List<Hit> hits, Hit hovered, int depth) {
        int row = y;
        Setting debug = null;
        int advanced = 0;
        for (Setting s : node.settings) {
            if (s.advanced) {
                advanced++;
                continue;
            }
            if (Objects.equals("debug", s.key)) {
                debug = s;
                continue;
            }
            row = setting(g, font, s, x, row, w, clipTop, clipBottom, focused, now, hits, hovered);
        }
        if (node.act != null) {
            if (row >= clipTop && row + 9 <= clipBottom) {
                String text = "[ " + node.act.label() + " ]";
                button(g, font, hits, hovered, "act:" + node.path(), text, x + w - font.width(text), row, CYAN, node.act.hint(), node.act.action());
            }
            row += LINE_H;
        }
        if (node.stat != null) {
            if (visible(row, clipTop, clipBottom)) g.text(font, font.plainSubstrByWidth(node.stat.get(), w), x, row, DIM, false);
            row += LINE_H;
        }
        for (Node child : node.children) {
            // A section that is a module of a group has its own page there (hole esp under view).
            if (isPage(child) || Tray.isRow(child)) continue;
            row += 3;
            if (visible(row, clipTop, clipBottom)) {
                g.text(font, "> " + child.name, x, row, CYAN, false);
                if (child.toggle != null) {
                    boolean on = child.on();
                    String state = on ? "[ON ]" : "[off]";
                    if (row >= clipTop && row + 9 <= clipBottom) {
                        button(g, font, hits, hovered, "master:" + child.path(), state, x + w - font.width(state), row, on ? GREEN : DIM,
                            child.desc, () -> flip(child));
                    }
                }
            }
            row += LINE_H;
            row = block(g, font, child, page, x + 6, row, w - 6, clipTop, clipBottom, focused, now, hits, hovered, depth + 1);
        }
        if (debug != null) row = setting(g, font, debug, x, row, w, clipTop, clipBottom, focused, now, hits, hovered);
        if (advanced > 0) row = advanced(g, font, node, advanced, x, row, w, clipTop, clipBottom, focused, now, hits, hovered);
        return row;
    }

    /** The settings an end user need not see, under a line that opens and closes them; returns where they end. */
    private int advanced(GuiGraphicsExtractor g, Font font, Node node, int count, int x, int y, int w, int clipTop, int clipBottom,
                         boolean focused, long now, List<Hit> hits, Hit hovered) {
        String path = node.path();
        boolean open = advancedOpen.contains(path);
        int row = y + 3;
        if (row >= clipTop && row + 9 <= clipBottom) {
            String head = (open ? "v " : "> ") + "advanced (" + count + ")";
            button(g, font, hits, hovered, "advanced:" + path, head, x, row, open ? CYAN : DIM,
                open ? "hide them again" : "settings the defaults already have right: the models it picks from, and how",
                () -> {
                    if (!advancedOpen.remove(path)) advancedOpen.add(path);
                });
        }
        row += LINE_H;
        if (!open) return row;
        for (Setting s : node.settings) {
            if (s.advanced) row = setting(g, font, s, x + 6, row, w - 6, clipTop, clipBottom, focused, now, hits, hovered);
        }
        return row;
    }

    private boolean isPage(Node node) {
        for (Page page : pages) if (page.node() == node) return true;
        return false;
    }

    private static boolean visible(int row, int clipTop, int clipBottom) {
        return row + 9 > clipTop && row < clipBottom;
    }

    /** One setting: its name on the left, its value on the right -- or under it, for a track or a long choice. */
    private int setting(GuiGraphicsExtractor g, Font font, Setting s, int x, int y, int w, int clipTop, int clipBottom,
                        boolean focused, long now, List<Hit> hits, Hit hovered) {
        int right = x + w;
        int row = y;
        int kind = s.kind();
        boolean clickable = row >= clipTop && row + 9 <= clipBottom;
        if (visible(row, clipTop, clipBottom)) g.text(font, font.plainSubstrByWidth(s.shown(), w / 2), x, row, DIM, false);
        String id = s.node.path() + "/" + s.key;

        if (kind == Setting.BOOL) {
            boolean on = s.bool();
            String text = on ? "on" : "off";
            if (clickable) button(g, font, hits, hovered, id, text, right - font.width(text), row, on ? GREEN : DIM, s.desc, () -> act(s, "toggle"));
            return row + LINE_H;
        }
        if (kind == Setting.KEY) {
            boolean wait = listening == s;
            String shown = s.display();
            String text = wait ? "press a key..." : shown;
            int color = wait ? AMBER : Objects.equals("none", shown) ? FAINT : MAGENTA;
            if (clickable) button(g, font, hits, hovered, id, text, right - font.width(text), row, color,
                "click, then press it - right click clears", () -> keyClick(s));
            return row + LINE_H;
        }
        if (kind == Setting.STRING && s.options != null) {
            // The offered choices, and a hidden one while it is the one set.
            String retired = s.hiddenNow();
            String[] shown = s.options;
            if (retired != null) {
                shown = java.util.Arrays.copyOf(s.options, s.options.length + 1);
                shown[s.options.length] = retired;
            }
            int total = 0;
            for (String option : shown) total += font.width(option) + 6;
            int ox = right - total + 6;
            int oy = row;
            if (total - 6 > w - font.width(s.shown()) - 8) {
                // No room beside the name: under it, from the left.
                oy = row + LINE_H;
                ox = x + 4;
            }
            String current = Setting.slug(String.valueOf(s.raw()));
            for (String option : shown) {
                String slug = Setting.slug(option);
                boolean on = Objects.equals(slug, current);
                if (ox + font.width(option) > right) {
                    ox = x + 4;
                    oy += LINE_H;
                }
                if (oy >= clipTop && oy + 9 <= clipBottom) {
                    int width = button(g, font, hits, hovered, id + ":" + slug, option, ox, oy, on ? GOLD : DIM, s.desc, () -> act(s, slug));
                    if (on) g.fill(ox, oy + 9, ox + width, oy + 10, GOLD);
                }
                ox += font.width(option) + 6;
            }
            return oy + LINE_H;
        }

        if (kind == Setting.STRING && ColourPicker.colour(s)) {
            return colour(g, font, s, id, x, row, w, clipTop, clipBottom, now, hits, hovered);
        }

        // A number or text: its value, which a click types over.
        boolean edit = editing == s;
        String value = edit ? buffer + ((now / 450) % 2 == 0 ? "_" : " ") : s.display();
        value = font.plainSubstrByWidth(value, w / 2);
        if (clickable) {
            if (edit) g.fill(right - font.width(value) - 3, row - 2, right + 1, row + 10, 0x40000000);
            button(g, font, hits, hovered, id, value, right - font.width(value), row, edit ? AMBER : CYAN,
                s.desc == null ? "click to type" : s.desc, () -> beginEdit(s));
        }
        row += LINE_H;
        if (kind != Setting.STRING && !Double.isNaN(s.min)) {
            // Its track, to drag.
            int x0 = x + 4, x1 = right - 2;
            if (row >= clipTop && row + 8 <= clipBottom) {
                Slider.draw(g, s, x0, x1, row - 2, -1);
                Span drag = span("drag:" + id, "", FG, s.desc, null);
                drag.slider = s;
                hits.add(new Hit(x0, row - 4, x1, row + 6, null, drag, -1, null, ACT_SLIDER));
            }
            row += 9;
        }
        return row;
    }

    /**
     * A colour: its swatch, which opens the picker under the row, and its text, which a click
     * types over as before (for a word such as esp); then the picker, while it is open.
     */
    private int colour(GuiGraphicsExtractor g, Font font, Setting s, String id, int x, int y, int w, int clipTop, int clipBottom,
                       long now, List<Hit> hits, Hit hovered) {
        int right = x + w;
        boolean open = picker != null && picker.setting == s;
        boolean edit = editing == s;
        String value = edit ? buffer + ((now / 450) % 2 == 0 ? "_" : " ") : s.display();
        value = font.plainSubstrByWidth(value, w / 2 - 14);
        int vx = right - font.width(value);
        int sx = vx - 13;
        if (y >= clipTop && y + 9 <= clipBottom) {
            if (edit) g.fill(vx - 3, y - 2, right + 1, y + 10, 0x40000000);
            button(g, font, hits, hovered, id, value, vx, y, edit ? AMBER : CYAN, s.desc == null ? "click to type" : s.desc, () -> beginEdit(s));
            Span pick = span("pick:" + id, "", FG, open ? "keep it and close the picker" : "pick the colour", () -> togglePicker(s));
            boolean hot = hovered != null && hovered.span() == pick;
            ColourPicker.swatch(g, sx, y, s, open, hot);
            hits.add(new Hit(sx - 2, y - 2, sx + 10, y + 10, null, pick, -1, null, ACT_NONE));
        }
        int row = y + LINE_H;
        if (!open) return row;
        pickerDrawn = true;
        int end = picker.draw(g, font, x, row - 2, w, clipTop, clipBottom, now, lastMouseX, lastMouseY, hits, hovered);
        if (picker.fresh) {
            // Just opened: scrolled up into view if it runs off the bottom.
            picker.fresh = false;
            if (end > clipBottom) scroll += Math.min(end - clipBottom, Math.max(0, y - clipTop));
        }
        return end;
    }

    private void togglePicker(Setting s) {
        boolean was = picker != null && picker.setting == s;
        calm();
        if (!was) picker = new ColourPicker(s);
    }

    // ---- what a click does ----------------------------------------------------

    /**
     * Whether a module shows as on: whether it really runs (on, every tab switch above it on), or,
     * in a group switched off from its tray icon, whether it comes back on with the group.
     */
    private static boolean running(Node node) {
        return Tray.shownOn(node);
    }

    private void flip(Node node) {
        calm();
        boolean was = running(node);
        Tray.flip(node);
        said(null, node.name + (was ? " off" : " on"));
    }

    private void act(Setting s, String value) {
        calm();
        if (s.asks(value)) {
            desktop.confirm(s.question(), s.warning.get(), "allow", () -> said(s.set("on"), null));
            return;
        }
        said(s.set(value), null);
    }

    private void keyClick(Setting s) {
        boolean was = listening == s;
        calm();
        if (Term.clickButton == 1) s.writeKey(GLFW.GLFW_KEY_UNKNOWN);
        else if (!was) listening = s;
    }

    private void beginEdit(Setting s) {
        calm();
        editing = s;
        buffer = "";
    }

    private void commitEdit() {
        commit(editing);
    }

    // The settings come in as parameters, never local copies of editing/listening: the
    // obfuscator would read the field again after it is cleared (HoistCheck).
    private void commit(Setting s) {
        editing = null;
        if (s != null && !buffer.isBlank()) said(s.set(buffer.trim()), null);
    }

    private void bindMouse(Setting s, int button) {
        listening = null;
        s.writeKey(Setting.MOUSE_BASE - button);
    }

    private void bindKey(Setting s, int key) {
        listening = null;
        if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) s.writeKey(GLFW.GLFW_KEY_UNKNOWN);
        else if (key != GLFW.GLFW_KEY_ESCAPE) s.writeKey(key);
    }

    @Override
    boolean captureClick(int button) {
        if (listening == null || button < GLFW.GLFW_MOUSE_BUTTON_MIDDLE) return false;
        // A middle or side button, while a key row waits: that button is the bind.
        bindMouse(listening, button);
        return true;
    }

    @Override
    boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (picker != null && listening == null && editing == null) {
            picker.keyPressed(event);
            return true;
        }
        if (listening != null) {
            bindKey(listening, key);
            return true;
        }
        if (editing != null) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) commitEdit();
            else if (key == GLFW.GLFW_KEY_ESCAPE) editing = null;
            else if (key == GLFW.GLFW_KEY_BACKSPACE && !buffer.isEmpty()) buffer = buffer.substring(0, buffer.length() - 1);
            return true;
        }
        return false;
    }

    @Override
    boolean charTyped(CharacterEvent event) {
        if (picker != null && editing == null) {
            picker.charTyped(event);
            return true;
        }
        if (editing == null) return false;
        if (event.isAllowedChatCharacter() && buffer.length() < 32) buffer += event.codepointAsString();
        return true;
    }

    // ---- widgets ------------------------------------------------------------------

    private Span span(String key, String text, int color, String hint, Runnable action) {
        String id = key + "|" + text;
        Span span = spans.get(id);
        if (span == null) {
            if (spans.size() > 4000) spans.clear();
            span = new Span(text, color);
            spans.put(id, span);
        }
        span.hint = hint;
        span.action = action;
        return span;
    }

    /** Clickable text; returns its width. */
    private int button(GuiGraphicsExtractor g, Font font, List<Hit> hits, Hit hovered, String key, String text,
                       int x, int y, int color, String hint, Runnable action) {
        Span span = span(key, text, color, hint, action);
        int width = font.width(text);
        int drawn = color;
        if (hovered != null && hovered.span() == span) {
            g.fill(x - 1, y - 1, x + width + 1, y + 9, 0x22FFFFFF);
            g.fill(x, y + 8, x + width, y + 9, color);
            drawn = mix(color, 0xFFFFFFFF, 0.3f);
        }
        g.text(font, text, x, y, drawn, false);
        hits.add(new Hit(x - 1, y - 1, x + width + 1, y + 9, null, span, -1, null, ACT_NONE));
        return width;
    }

    /** Words wrapped to a width, at most {@code max} lines, the last cut short with an ellipsis. */
    private static List<String> wrap(Font font, String text, int width, int max) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty() || width <= 10) return out;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (font.width(next) <= width) {
                line.setLength(0);
                line.append(next);
                continue;
            }
            if (!line.isEmpty()) out.add(line.toString());
            line.setLength(0);
            line.append(word);
            if (out.size() == max) break;
        }
        if (out.size() < max && !line.isEmpty()) out.add(line.toString());
        if (out.size() > max) out = out.subList(0, max);
        if (out.size() == max) {
            String last = out.get(max - 1);
            if (font.width(last + "...") > width) last = font.plainSubstrByWidth(last, width - font.width("...")) + "...";
            out.set(max - 1, last);
        }
        return out;
    }
}
