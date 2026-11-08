package com.mineux.core.ui.term;

import com.mineux.core.ui.term.Term.Hit;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import java.util.List;

/**
 * Anything the window manager places: a terminal ({@link TermWindow}) or a debug readout
 * ({@link ReadoutPane}). Where it is, where it is heading, and the few things the desk asks
 * of whatever is inside it.
 * <p>
 * Told apart by {@link #readout()} and {@link #terminal()} rather than by type: the obfuscator breaks
 * {@code instanceof} against any class with subclasses, and this is one.
 */
abstract class Pane {

    static final int MIN_W = 150, MIN_H = 80;

    // ---- placement ------------------------------------------------------
    boolean floating;
    /** Where it floats, when it floats. */
    float fx, fy, fw = 300, fh = 190;
    /** Drawn this frame, and where the layout wants it. */
    float x, y, w, h;
    float targetX, targetY, targetW, targetH;
    /** Its share of its column (or row) along the column, when it is tiled. */
    float weight = 1f;

    /** Pinned ({@code pin}, SUPER+P): drawn over the game when the desktop is closed. On its own desk it is like any other. */
    boolean onAll;
    /** The group it is a tab of, or null; only the group's active tab is laid out and drawn. */
    Group group;
    /**
     * How solid it is drawn, 1 solid down to {@link #MIN_OPACITY} ({@code opacity}, SUPER+K and
     * SUPER+L), pinned over the game too; and when it last changed, for the percentage shown on it.
     */
    float opacity = 1f;
    long opacityAt;
    static final float MIN_OPACITY = 0.15f;
    private static final long OPACITY_SHOWN = 900L;
    /** Whether its border was last drawn focused, and when that changed, for the border's fade. */
    boolean borderFocused;
    long borderAt;

    // ---- motion ---------------------------------------------------------
    /** Not yet laid out: its first frame pops it in, as Hyprland's windowsIn does. */
    private boolean fresh = true;
    /** When it opened, for its fade in; 0 for a pane that was simply put in place. */
    long openedAt;
    private float fromX, fromY, fromW, fromH;
    private float endX = Float.NaN, endY, endW, endH;
    private long moveStart;
    private String moveAnim = "windowsMove";

    int minW() {
        return MIN_W;
    }

    int minH() {
        return MIN_H;
    }

    /** The readout this pane shows, or null for anything else. */
    String readout() {
        return null;
    }

    /** Whether this is a terminal, rather than a readout or a list window. */
    boolean terminal() {
        return false;
    }

    /** Whether the keyboard goes to it while it is focused: a terminal does, and so does a window with a text field. */
    boolean takesKeys() {
        return false;
    }

    /**
     * Whether it has the keyboard to itself for now -- a key row waiting for its key, a field
     * being typed into -- so that not even Escape or a window manager bind is anyone else's.
     */
    boolean capturing() {
        return false;
    }

    /** Whether what it is capturing is text, so the characters typed are its too. */
    boolean typing() {
        return false;
    }

    /** A mouse button pressed while it is {@link #capturing}; true when that button was what it waited for. */
    boolean captureClick(int button) {
        return false;
    }

    /**
     * Whether it draws its own rounded glass and edge ({@link ModuleWindow}) rather than sitting
     * in the window manager's square border, which then leaves its dimming alone too.
     */
    boolean glass() {
        return false;
    }

    /**
     * How tall its title bar is, or 0 for a pane without one. A pane with a title bar is a
     * window as any desktop has them: dragged by the bar, resized by the corner.
     */
    int header() {
        return 0;
    }

    /** Size of the corner a window is resized by, in GUI px. */
    static final int GRIP = 7;

    /** Whether a point is on this window's resize corner (bottom right). */
    boolean onGrip(double mx, double my) {
        return header() > 0 && mx >= x + w - GRIP && mx < x + w && my >= y + h - GRIP && my < y + h;
    }

    /** The resize corner's dots, for a window that has one. */
    void drawGrip(net.minecraft.client.gui.GuiGraphicsExtractor g, boolean hot) {
        int rx = ix() + iw() - 2, by = iy() + ih() - 2;
        int color = hot ? 0xD0EFC648 : 0x50FFFFFF;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j <= i; j++) g.fill(rx - 2 * (i - j) - 1, by - 2 * j - 1, rx - 2 * (i - j), by - 2 * j, color);
        }
    }

    // ---- geometry -------------------------------------------------------

    int ix() { return Math.round(x); }
    int iy() { return Math.round(y); }
    int iw() { return Math.round(w); }
    int ih() { return Math.round(h); }

    boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /**
     * Moves the drawn rectangle along the curve {@code hyprland.conf} gives {@code windowsMove}
     * towards the target, starting over from wherever it is whenever the target changes; a new
     * pane pops in ({@code windowsIn}, {@code popin 87%}) from a smaller copy of its first spot.
     *
     * @return true while it is still moving
     */
    boolean animate(float dt) {
        long now = System.currentTimeMillis();
        if (fresh) popIn(now);
        else if (retargeted()) begin("windowsMove", now);
        return advance(now);
    }

    private void popIn(long now) {
        HyprConf conf = HyprConf.get();
        fresh = false;
        openedAt = now;
        float scale = conf.style("windowsIn").startsWith("popin") ? conf.percent("windowsIn", 0.8f) : 1f;
        w = targetW * scale;
        h = targetH * scale;
        x = targetX + (targetW - w) / 2f;
        y = targetY + (targetH - h) / 2f;
        begin("windowsIn", now);
    }

    private boolean retargeted() {
        return Math.abs(targetX - endX) > 0.01f || Math.abs(targetY - endY) > 0.01f
            || Math.abs(targetW - endW) > 0.01f || Math.abs(targetH - endH) > 0.01f || Float.isNaN(endX);
    }

    private boolean advance(long now) {
        HyprConf conf = HyprConf.get();
        long ms = conf.ms(moveAnim);
        float t = ms <= 0 ? 1f : (now - moveStart) / (float) ms;
        if (t >= 1f) {
            snap();
            return false;
        }
        float e = conf.curve(moveAnim, t);
        x = fromX + (endX - fromX) * e;
        y = fromY + (endY - fromY) * e;
        w = fromW + (endW - fromW) * e;
        h = fromH + (endH - fromH) * e;
        return true;
    }

    private void begin(String anim, long now) {
        fromX = x;
        fromY = y;
        fromW = w;
        fromH = h;
        endX = targetX;
        endY = targetY;
        endW = targetW;
        endH = targetH;
        moveStart = now;
        moveAnim = anim;
    }

    void snap() {
        x = targetX;
        y = targetY;
        w = targetW;
        h = targetH;
        endX = targetX;
        endY = targetY;
        endW = targetW;
        endH = targetH;
        fresh = false;
    }

    /** How far its frame has faded in ({@code fadeIn}), 0 to 1. */
    float appear(long now) {
        if (openedAt == 0L) return 1f;
        HyprConf conf = HyprConf.get();
        long ms = conf.ms("fadeIn");
        if (ms <= 0) return 1f;
        float t = (now - openedAt) / (float) ms;
        return t >= 1f ? 1f : conf.curve("fadeIn", t);
    }

    /** What a group's tab calls it. */
    String title() {
        return "window";
    }

    // ---- what the desk asks of it ---------------------------------------

    /** Takes over drawing for a newly opened desktop. */
    void attach(Desktop screen) {
    }

    abstract void render(GuiGraphicsExtractor g, Font font, long now, int mouseX, int mouseY, boolean focused,
                         List<Hit> hits, Hit hovered);

    /** {@link #render} at its {@link #opacity}, see-through through {@link Faded}; for a moment after a change, how solid it now is. */
    final void draw(GuiGraphicsExtractor g, Font font, long now, int mouseX, int mouseY, boolean focused, List<Hit> hits, Hit hovered) {
        GuiGraphicsExtractor with = opacity < 0.995f ? Faded.of(g, mouseX, mouseY, opacity) : g;
        render(with, font, now, mouseX, mouseY, focused, hits, hovered);
        long since = now - opacityAt;
        if (since >= 0L && since < OPACITY_SHOWN) opacityTag(g, font, since);
    }

    /** The opacity in the middle of the pane, solid, fading out over its last third. */
    private void opacityTag(GuiGraphicsExtractor g, Font font, long since) {
        float fade = Math.min(1f, (OPACITY_SHOWN - since) / (OPACITY_SHOWN / 3f));
        int a = Math.round(255f * fade);
        if (a < 8) return;
        String text = "opacity " + Math.round(opacity * 100f) + "%";
        int tw = font.width(text);
        int cx = ix() + iw() / 2;
        int cy = iy() + ih() / 2;
        g.fill(cx - tw / 2 - 6, cy - 9, cx + tw / 2 + 6, cy + 9, (Math.round(0xD8 * fade) << 24) | 0x0A0C12);
        g.fill(cx - tw / 2 - 6, cy + 8, cx - tw / 2 - 6 + Math.round((tw + 12) * opacity), cy + 9, (a << 24) | 0xEFC648);
        g.text(font, text, cx - tw / 2, cy - 4, (a << 24) | 0xD7DCE6, false);
    }

    /** Drawn after every pane, so nothing covers it. */
    void renderPopup(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, List<Hit> hits) {
    }

    void tick(boolean settled) {
    }

    /** @return false for a key nothing here wanted, so the desktop can have it */
    boolean keyPressed(KeyEvent event) {
        return false;
    }

    boolean charTyped(CharacterEvent event) {
        return false;
    }

    void scrolled(double mouseY, double amount) {
    }

    /** Puts text into its prompt, for a terminal; nothing for anything else. */
    void setInput(String text) {
    }

    /** SUPER+Q, or its own close button. */
    abstract void close();
}
