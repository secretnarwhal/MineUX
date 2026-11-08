package com.mineux.core.ui.term;

import com.mineux.core.ui.ColourMath;
import com.mineux.core.ui.term.Term.Hit;
import com.mineux.core.ui.term.Term.Span;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mineux.core.ui.term.Term.*;

/**
 * A colour setting's picker, opened under its row in a settings window ({@link ModuleWindow}) by
 * the swatch beside its value: a saturation/value square with a hue strip beside it, an alpha
 * strip under it for a colour written with alpha ({@code #aarrggbb}), the colour before and now
 * side by side, the hex and its red, green and blue (and alpha) to type, and the last colours kept.
 * <p>
 * It writes as it is dragged, straight into the live config ({@link Setting#put}), so the sky or
 * the esp changes under the cursor; the file is saved once the button comes up. The drag is
 * followed here, every frame, from where the mouse is and whether the button is still down,
 * rather than through the desktop's drag events, and it is held at the square's edges however far
 * past them the cursor goes. Enter or ok keeps it, as does clicking anything else in the window;
 * Esc or cancel puts back what it was. The setting keeps its own text format: {@code #rrggbb}
 * stays six digits, {@code #aarrggbb} eight.
 * <p>
 * The hue, saturation and value are kept here, not read back from the colour each frame, so the
 * hue holds through a grey or black instead of snapping to red. The classic panel's own picker is
 * {@code ui/widget/ColorPicker}.
 */
final class ColourPicker {

    private static final int SV_H = 56;
    private static final int HUE_W = 8;
    private static final int PREVIEW_W = 22;
    private static final int GAP = 4;
    private static final int ALPHA_H = 6;
    private static final int LINE_H = 12;
    private static final int RECENT = 8;
    private static final int CELL = 8;
    /** How far past a strip's edge a press still takes hold of it. */
    private static final int REACH = 3;

    private static final int NONE = 0, SV = 1, HUE = 2, ALPHA = 3;
    private static final int HEX = 1, RED = 2, GREEN_F = 3, BLUE_F = 4, ALPHA_F = 5;

    /** The colours kept last, newest first, 0xAARRGGBB; shared by every picker for as long as the game runs. */
    private static final List<Integer> recent = new ArrayList<>();

    final Setting setting;
    private final String oldText;
    private final long oldColour;
    private final boolean alpha;

    /** Hue, saturation, value; and the alpha byte. */
    private final float[] hsv = new float[3];
    private int alphaByte = 255;

    private int held = NONE;
    private int field = NONE;
    private String buffer = "";
    private boolean dirty;
    private boolean done;
    /** Set when opened, until the window has scrolled it into view. */
    boolean fresh = true;

    /** Where the strips were drawn last frame, for a drag. */
    private int svX, svY, svW, hueY, alphaX, alphaW;
    private int lastMouseX, lastMouseY;

    private final Map<String, Span> spans = new HashMap<>();

    ColourPicker(Setting setting) {
        this.setting = setting;
        this.oldText = text(setting);
        this.oldColour = ColourMath.parse(oldText);
        this.alpha = ColourMath.hasAlpha(oldText);
        int start = oldColour < 0L ? 0xFFFFFFFF : (int) oldColour;
        ColourMath.toHsv(start, hsv);
        alphaByte = ColourMath.channel(start, 24);
    }

    /** Whether a setting is a colour: text, no list of choices, and written or described as {@code #rrggbb}. */
    static boolean colour(Setting s) {
        if (s.options != null || s.kind() != Setting.STRING) return false;
        String desc = s.desc == null ? "" : s.desc;
        if (desc.contains("#rrggbb") || desc.contains("#aarrggbb")) return true;
        String now = text(s);
        return now.startsWith("#") && ColourMath.parse(now) >= 0L;
    }

    private static String text(Setting s) {
        Object raw = s.raw();
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    /** The colour as it stands, 0xAARRGGBB (opaque for a setting without alpha). */
    int argb() {
        int rgb = ColourMath.hsv(hsv[0], hsv[1], hsv[2]);
        return ((alpha ? alphaByte : 255) << 24) | rgb;
    }

    boolean done() {
        return done;
    }

    // ---- the swatch on the row ---------------------------------------------------

    /** A setting's colour as a small square, a word it holds instead (esp, health) as a hatched one. */
    static void swatch(GuiGraphicsExtractor g, int x, int y, Setting s, boolean open, boolean hot) {
        String now = text(s);
        long value = ColourMath.parse(now);
        int edge = open ? GOLD : hot ? FG : 0xFF2A2F3C;
        g.fill(x - 1, y - 1, x + 9, y + 9, edge);
        if (value < 0L) {
            g.fill(x, y, x + 8, y + 8, 0xFF1A1D26);
            for (int i = 0; i < 8; i += 3) g.fill(x + i, y + 7 - i, x + i + 1, y + 8 - i, DIM);
            return;
        }
        if (ColourMath.hasAlpha(now)) checker(g, x, y, x + 8, y + 8);
        g.fill(x, y, x + 8, y + 8, (int) value);
    }

    // ---- drawing ------------------------------------------------------------------

    /** The picker under its row, from {@code y}; returns where it ends. */
    int draw(GuiGraphicsExtractor g, Font font, int x, int y, int w, int clipTop, int clipBottom, long now,
             int mouseX, int mouseY, List<Hit> hits, Hit hovered) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        follow(mouseX, mouseY);
        int top = y + 2;
        svX = x + 2;
        svY = top + 2;
        svW = Math.max(24, w - HUE_W - PREVIEW_W - GAP * 2 - 4);
        hueY = svY;
        int bottom = svY + SV_H + GAP;
        if (alpha) bottom += ALPHA_H + GAP;
        int end = bottom + LINE_H * 2 + 2;
        g.fill(x, top, x + w, end, 0x38000000);
        g.fill(x, top, x + w, top + 1, 0x1CFFFFFF);

        square(g, clipTop, clipBottom, hits);
        hue(g, clipTop, clipBottom, hits);
        preview(g, font, svX + svW + GAP + HUE_W + GAP, svY, clipTop, clipBottom, hits);
        if (alpha) alphaStrip(g, svX, svY + SV_H + GAP, clipTop, clipBottom, hits);
        numbers(g, font, x + 2, bottom, w - 4, clipTop, clipBottom, now, hits, hovered);
        lowerLine(g, font, x + 2, bottom + LINE_H, w - 4, clipTop, clipBottom, hits, hovered);
        return end + 3;
    }

    /** The square: saturation left to right, value top to bottom, a column at a time. */
    private void square(GuiGraphicsExtractor g, int clipTop, int clipBottom, List<Hit> hits) {
        for (int i = 0; i < svW; i++) {
            float s = (i + 0.5f) / svW;
            int topColour = 0xFF000000 | ColourMath.hsv(hsv[0], s, 1f);
            g.fillGradient(svX + i, svY, svX + i + 1, svY + SV_H, topColour, 0xFF000000);
        }
        marker(g, svX + hsv[1] * svW, svY + (1f - hsv[2]) * SV_H, 0xFF000000 | argb());
        Span span = span("sv", "pick - drag; it holds at the edges", SV);
        hit(hits, svX - REACH, svY - REACH, svX + svW + REACH, svY + SV_H + REACH, clipTop, clipBottom, span);
    }

    /** The hue strip, red at the top round to red at the bottom, in six straight blends. */
    private void hue(GuiGraphicsExtractor g, int clipTop, int clipBottom, List<Hit> hits) {
        int hx = svX + svW + GAP;
        for (int k = 0; k < 6; k++) {
            int y0 = hueY + SV_H * k / 6, y1 = hueY + SV_H * (k + 1) / 6;
            int a = 0xFF000000 | ColourMath.hsv(k / 6f, 1f, 1f);
            int b = 0xFF000000 | ColourMath.hsv((k + 1) / 6f, 1f, 1f);
            g.fillGradient(hx, y0, hx + HUE_W, y1, a, b);
        }
        float my = hueY + hsv[0] * SV_H;
        g.pose().pushMatrix();
        g.pose().translate(0f, my);
        g.fill(hx - 2, -2, hx + HUE_W + 2, 2, 0xFF000000);
        g.fill(hx - 1, -1, hx + HUE_W + 1, 1, 0xFFFFFFFF);
        g.pose().popMatrix();
        Span span = span("hue", "hue - drag", HUE);
        hit(hits, hx - REACH, hueY - REACH, hx + HUE_W + REACH, hueY + SV_H + REACH, clipTop, clipBottom, span);
    }

    /** The colour it was over the colour now; a click on the old one goes back to it. */
    private void preview(GuiGraphicsExtractor g, Font font, int px, int py, int clipTop, int clipBottom, List<Hit> hits) {
        int half = SV_H / 2;
        g.fill(px - 1, py - 1, px + PREVIEW_W + 1, py + SV_H + 1, 0xFF2A2F3C);
        if (alpha) checker(g, px, py, px + PREVIEW_W, py + SV_H);
        if (oldColour < 0L) {
            g.fill(px, py, px + PREVIEW_W, py + half, 0xFF1A1D26);
            g.text(font, font.plainSubstrByWidth(oldText, PREVIEW_W - 2), px + 1, py + half / 2 - 4, DIM, false);
        } else {
            g.fill(px, py, px + PREVIEW_W, py + half, (int) oldColour);
        }
        g.fill(px, py + half, px + PREVIEW_W, py + SV_H, argb());
        Span back = span("old", "the colour it was (" + oldText + ") - click to go back to it", 0);
        back.action = this::revert;
        hit(hits, px, py, px + PREVIEW_W, py + half, clipTop, clipBottom, back);
        Span nowSpan = span("new", "the colour now", 0);
        hit(hits, px, py + half, px + PREVIEW_W, py + SV_H, clipTop, clipBottom, nowSpan);
    }

    /** See-through on the left to solid on the right, over a checkerboard. */
    private void alphaStrip(GuiGraphicsExtractor g, int x, int y, int clipTop, int clipBottom, List<Hit> hits) {
        alphaX = x;
        alphaW = svW;
        checker(g, x, y, x + alphaW, y + ALPHA_H);
        int rgb = argb() & 0xFFFFFF;
        for (int i = 0; i < alphaW; i++) {
            int a = Math.round(255f * i / Math.max(1, alphaW - 1));
            g.fill(x + i, y, x + i + 1, y + ALPHA_H, (a << 24) | rgb);
        }
        float mx = x + alphaByte / 255f * alphaW;
        g.pose().pushMatrix();
        g.pose().translate(mx, 0f);
        g.fill(-2, y - 2, 2, y + ALPHA_H + 2, 0xFF000000);
        g.fill(-1, y - 1, 1, y + ALPHA_H + 1, 0xFFFFFFFF);
        g.pose().popMatrix();
        Span span = span("alpha", "alpha - drag; left is see-through", ALPHA);
        hit(hits, x - REACH, y - 2, x + alphaW + REACH, y + ALPHA_H + 2, clipTop, clipBottom, span);
    }

    /** The hex, then red, green, blue (and alpha), each to click and type. */
    private void numbers(GuiGraphicsExtractor g, Font font, int x, int y, int w, int clipTop, int clipBottom, long now,
                         List<Hit> hits, Hit hovered) {
        boolean shown = y >= clipTop && y + 9 <= clipBottom;
        int c = argb();
        String hex = field == HEX ? buffer + caret(now) : ColourMath.hex(c, alpha);
        int cx = x;
        cx += field(g, font, hits, hovered, HEX, hex, cx, y, shown, "type a hex colour - enter sets it") + 8;
        cx = channel(g, font, hits, hovered, RED, "r", ColourMath.channel(c, 16), cx, y, shown, now);
        cx = channel(g, font, hits, hovered, GREEN_F, "g", ColourMath.channel(c, 8), cx, y, shown, now);
        cx = channel(g, font, hits, hovered, BLUE_F, "b", ColourMath.channel(c, 0), cx, y, shown, now);
        if (alpha) channel(g, font, hits, hovered, ALPHA_F, "a", alphaByte, cx, y, shown, now);
    }

    private int channel(GuiGraphicsExtractor g, Font font, List<Hit> hits, Hit hovered, int which, String name, int value,
                        int x, int y, boolean shown, long now) {
        if (shown) g.text(font, name, x, y, FAINT, false);
        int vx = x + font.width(name) + 2;
        String text = field == which ? buffer + caret(now) : String.valueOf(value);
        int width = field(g, font, hits, hovered, which, text, vx, y, shown, "type 0-255 - up/down nudge it, shift by 10");
        return vx + Math.max(width, font.width("000")) + 6;
    }

    /** Clickable text that a click starts typing into; returns its width. */
    private int field(GuiGraphicsExtractor g, Font font, List<Hit> hits, Hit hovered, int which, String text, int x, int y,
                      boolean shown, String hint) {
        int width = font.width(text);
        if (!shown) return width;
        boolean typing = field == which;
        Span span = span("field" + which, hint, 0);
        span.action = () -> beginField(which);
        boolean hot = hovered != null && hovered.span() == span;
        if (typing) g.fill(x - 2, y - 2, x + width + 2, y + 10, 0x40000000);
        else if (hot) g.fill(x - 1, y - 1, x + width + 1, y + 9, 0x22FFFFFF);
        g.text(font, text, x, y, typing ? AMBER : which == HEX ? CYAN : FG, false);
        hits.add(new Hit(x - 1, y - 1, x + width + 1, y + 9, null, span, -1, null, ACT_NONE));
        return width;
    }

    /** The colours kept last, to click; then ok and cancel on the right. */
    private void lowerLine(GuiGraphicsExtractor g, Font font, int x, int y, int w, int clipTop, int clipBottom,
                           List<Hit> hits, Hit hovered) {
        if (y < clipTop || y + 9 > clipBottom) return;
        int right = x + w;
        String cancel = "cancel", ok = "ok";
        int cancelX = right - font.width(cancel);
        int okX = cancelX - 8 - font.width(ok);
        int cx = x;
        if (recent.isEmpty()) g.text(font, font.plainSubstrByWidth("recent colours show here", Math.max(0, okX - x - 4)), x, y, FAINT, false);
        for (int i = 0; i < recent.size() && cx + CELL <= okX - 4; i++) {
            recentCell(g, hits, hovered, i, cx, y);
            cx += CELL + 3;
        }
        button(g, font, hits, hovered, "ok", ok, okX, y, GREEN, "keep it (enter)", this::keep);
        button(g, font, hits, hovered, "cancel", cancel, cancelX, y, DIM, "put back " + oldText + " (esc)", this::cancel);
    }

    private void recentCell(GuiGraphicsExtractor g, List<Hit> hits, Hit hovered, int index, int x, int y) {
        int colour = recent.get(index);
        Span span = span("recent" + index, ColourMath.hex(colour, alpha) + " - click to use it", 0);
        span.action = () -> useRecent(colour);
        boolean hot = hovered != null && hovered.span() == span;
        g.fill(x - 1, y - 1, x + CELL + 1, y + CELL + 1, hot ? FG : 0xFF2A2F3C);
        checker(g, x, y, x + CELL, y + CELL);
        g.fill(x, y, x + CELL, y + CELL, alpha ? colour : 0xFF000000 | colour);
        hits.add(new Hit(x - 1, y - 1, x + CELL + 1, y + CELL + 1, null, span, -1, null, ACT_NONE));
    }

    private void button(GuiGraphicsExtractor g, Font font, List<Hit> hits, Hit hovered, String key, String text, int x, int y,
                        int color, String hint, Runnable action) {
        Span span = span(key, hint, 0);
        span.action = action;
        int width = font.width(text);
        int drawn = color;
        if (hovered != null && hovered.span() == span) {
            g.fill(x - 1, y - 1, x + width + 1, y + 9, 0x22FFFFFF);
            g.fill(x, y + 8, x + width, y + 9, color);
            drawn = mix(color, 0xFFFFFFFF, 0.3f);
        }
        g.text(font, text, x, y, drawn, false);
        hits.add(new Hit(x - 1, y - 1, x + width + 1, y + 9, null, span, -1, null, ACT_NONE));
    }

    /** A ring round the colour picked, at a point between pixels, so it glides rather than steps. */
    private static void marker(GuiGraphicsExtractor g, float x, float y, int colour) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.fill(-4, -4, 4, 4, 0xFF000000);
        g.fill(-3, -3, 3, 3, 0xFFFFFFFF);
        g.fill(-2, -2, 2, 2, colour);
        g.pose().popMatrix();
    }

    private static void checker(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, 0xFFBFBFBF);
        for (int y = y0; y < y1; y += 3) {
            for (int x = x0 + ((y - y0) / 3 % 2) * 3; x < x1; x += 6) g.fill(x, y, Math.min(x + 3, x1), Math.min(y + 3, y1), 0xFF7F7F7F);
        }
    }

    private static String caret(long now) {
        return (now / 450) % 2 == 0 ? "_" : " ";
    }

    // ---- hits ------------------------------------------------------------------------

    /** A span that a press on takes hold of a strip ({@code grab}), or a plain one for {@code grab} 0. */
    private Span span(String key, String hint, int grab) {
        Span span = spans.get(key);
        if (span == null) {
            span = new Span("", FG);
            spans.put(key, span);
        }
        span.hint = hint;
        if (grab != NONE) span.action = () -> grab(grab);
        return span;
    }

    /** A hit cut to the part of it inside the window's scrolled body; none when nothing is. */
    private static void hit(List<Hit> hits, int x0, int y0, int x1, int y1, int clipTop, int clipBottom, Span span) {
        int top = Math.max(y0, clipTop), bottom = Math.min(y1, clipBottom);
        if (bottom > top) hits.add(new Hit(x0, top, x1, bottom, null, span, -1, null, ACT_NONE));
    }

    // ---- the mouse -----------------------------------------------------------------

    private void grab(int what) {
        if (Term.clickButton != 0) return;
        endField(false);
        held = what;
        move(cursorX(lastMouseX), cursorY(lastMouseY));
    }

    /** Every frame: where a held strip's cursor is now, until the button comes up. */
    private void follow(int mouseX, int mouseY) {
        if (held == NONE) return;
        if (!buttonDown()) {
            held = NONE;
            save();
            return;
        }
        move(cursorX(mouseX), cursorY(mouseY));
    }

    private void move(double mx, double my) {
        if (held == SV) {
            hsv[1] = (float) ColourMath.along(mx, svX, svW);
            hsv[2] = 1f - (float) ColourMath.along(my, svY, SV_H);
        } else if (held == HUE) {
            hsv[0] = (float) ColourMath.along(my, hueY, SV_H);
        } else if (held == ALPHA) {
            alphaByte = (int) Math.round(255.0 * ColourMath.along(mx, alphaX, alphaW));
        }
        write();
    }

    private static boolean buttonDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        return GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    /** The cursor between GUI pixels, for a smooth drag; the whole pixel the frame was given when they disagree. */
    private static double cursorX(int given) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.mouseHandler == null) return given;
        double exact = mc.mouseHandler.getScaledXPos(mc.getWindow());
        return Math.abs(exact - given) < 1.5 ? exact : given;
    }

    private static double cursorY(int given) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.mouseHandler == null) return given;
        double exact = mc.mouseHandler.getScaledYPos(mc.getWindow());
        return Math.abs(exact - given) < 1.5 ? exact : given;
    }

    // ---- writing --------------------------------------------------------------------

    /** Into the live config, unsaved: it shows at once. */
    private void write() {
        String text = ColourMath.hex(argb(), alpha);
        if (!text.equals(text(setting)) && setting.put(text)) dirty = true;
    }

    /** Saved, once a drag or a typed value is done. */
    private void save() {
        if (!dirty) return;
        dirty = false;
        setting.node.commit();
    }

    /** A whole colour at once (typed, or from the recent ones): hue kept through greys. */
    private void setColour(int argb) {
        ColourMath.keepHue(argb & 0xFFFFFF, hsv);
        if (alpha) alphaByte = ColourMath.channel(argb, 24);
        write();
        save();
    }

    private void useRecent(int colour) {
        endField(false);
        setColour(colour);
    }

    private void revert() {
        endField(false);
        if (oldColour >= 0L) {
            setColour((int) oldColour);
            return;
        }
        // A word (esp, health): put it back as it was.
        setting.set(oldText);
        dirty = false;
    }

    /** Ok, enter, or a click on anything else in the window: kept, and remembered among the recent ones. */
    void keep() {
        if (done) return;
        endField(true);
        held = NONE;
        save();
        done = true;
        long now = ColourMath.parse(text(setting));
        if (now >= 0L && now != oldColour) remember((int) now);
    }

    /** Cancel or esc: what it was before it opened. */
    void cancel() {
        if (done) return;
        field = NONE;
        held = NONE;
        dirty = false;
        if (!oldText.equals(text(setting))) setting.set(oldText);
        done = true;
    }

    private static void remember(int colour) {
        recent.remove(Integer.valueOf(colour));
        recent.add(0, colour);
        while (recent.size() > RECENT) recent.remove(recent.size() - 1);
    }

    // ---- typing ---------------------------------------------------------------------

    private void beginField(int which) {
        if (Term.clickButton != 0) return;
        endField(true);
        field = which;
        buffer = "";
    }

    /** Leaves the field being typed in, setting what was typed when {@code apply}. */
    private void endField(boolean apply) {
        leaveField(apply, field);
    }

    /** {@code which}: the field being left, a parameter rather than a local copy of the field (HoistCheck). */
    private void leaveField(boolean apply, int which) {
        field = NONE;
        if (apply && which != NONE && !buffer.isEmpty()) applyField(which, buffer);
        buffer = "";
    }

    private void applyField(int which, String text) {
        if (which == HEX) {
            long parsed = ColourMath.parse(text);
            if (parsed < 0L) return;
            int c = (int) parsed;
            // Six digits typed into a colour with alpha keep the alpha it had.
            if (alpha && !ColourMath.hasAlpha(text)) c = ColourMath.withChannel(c, 24, alphaByte);
            setColour(c);
            return;
        }
        int value;
        try {
            value = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return;
        }
        setChannel(which, value);
    }

    private void setChannel(int which, int value) {
        if (which == ALPHA_F) {
            alphaByte = Math.max(0, Math.min(255, value));
            write();
            save();
            return;
        }
        int shift = which == RED ? 16 : which == GREEN_F ? 8 : 0;
        setColour(ColourMath.withChannel(argb(), shift, value));
    }

    private int channelValue(int which) {
        if (which == ALPHA_F) return alphaByte;
        int shift = which == RED ? 16 : which == GREEN_F ? 8 : 0;
        return ColourMath.channel(argb(), shift);
    }

    /** Leaves the field being typed when the window loses the keyboard. */
    void blur() {
        if (field != NONE) endField(false);
    }

    void keyPressed(KeyEvent event) {
        int key = event.key();
        if (field != NONE) {
            fieldKey(event, key);
            return;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) cancel();
        else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) keep();
    }

    private void fieldKey(KeyEvent event, int key) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            endField(true);
        } else if (key == GLFW.GLFW_KEY_ESCAPE) {
            endField(false);
        } else if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!buffer.isEmpty()) buffer = buffer.substring(0, buffer.length() - 1);
            if (field == HEX) preview();
        } else if ((key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) && field != HEX) {
            int step = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
            int which = field;
            int value = channelValue(which) + (key == GLFW.GLFW_KEY_UP ? step : 0 - step);
            setChannel(which, value);
            buffer = String.valueOf(channelValue(which));
        }
    }

    void charTyped(CharacterEvent event) {
        String typed = event.codepointAsString();
        if (typed.length() != 1) return;
        char c = typed.charAt(0);
        boolean hexDigit = Character.digit(c, 16) >= 0;
        if (field == NONE) {
            // Typing a colour straight away types it into the hex.
            if (!hexDigit && c != '#') return;
            field = HEX;
            buffer = "";
        }
        if (field == HEX) {
            if ((hexDigit || c == '#' && buffer.isEmpty()) && buffer.length() < 9) buffer += c;
            preview();
            return;
        }
        if (Character.isDigit(c) && buffer.length() < 3) buffer += c;
    }

    /** A hex typed out in full shows at once, before enter. */
    private void preview() {
        String digits = buffer.startsWith("#") ? buffer.substring(1) : buffer;
        if (digits.length() != 6 && digits.length() != 8) return;
        long parsed = ColourMath.parse(buffer);
        if (parsed < 0L) return;
        int c = (int) parsed;
        if (alpha && digits.length() == 6) c = ColourMath.withChannel(c, 24, alphaByte);
        ColourMath.keepHue(c & 0xFFFFFF, hsv);
        if (alpha) alphaByte = ColourMath.channel(c, 24);
        write();
    }

    /** Whether it is typing into one of its fields. */
    boolean typing() {
        return field != NONE;
    }

    /** What the window's status line says while it is open. */
    String status() {
        if (field != NONE) return "type - enter sets, esc leaves it";
        return "drag to pick, or type a hex - enter keeps, esc puts it back";
    }
}
