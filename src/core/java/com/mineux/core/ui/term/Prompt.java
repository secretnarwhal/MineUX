package com.mineux.core.ui.term;

import com.mineux.core.ui.term.Term.Hit;
import com.mineux.core.ui.term.Term.Span;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.mineux.core.ui.term.Term.*;

/**
 * A yes/no question in a window of its own, floating over the desk: {@code quit} asking before
 * the game closes, {@code reboot} before it restarts. It grows out of the middle of the screen,
 * and while it is up it has the keyboard and the mouse to itself -- y or Enter answers yes,
 * n or Esc no, as do its two buttons.
 * <p>
 * Not one of the desk's windows: nothing tiles it, saves it or focuses past it, and it is gone
 * the moment it is answered.
 */
final class Prompt extends Pane {

    private static final int HEADER_H = 12;
    private static final int PAD = 8;
    private static final int W = 260, H = 74;
    private static final int W_LONG = 340, ROW_H = 10;

    private final String question;
    private final String detail;
    /** The detail wrapped to the prompt's width, one entry a row; worked out when it is placed. */
    private List<String> rows = List.of();
    private int wrappedAt = -1;
    private final String yes;
    private final Runnable onYes;
    private final Runnable onDone;
    private boolean answered;

    /**
     * @param onYes  what yes does
     * @param onDone takes the prompt down, whichever the answer
     */
    Prompt(String question, String detail, String yes, Runnable onYes, Runnable onDone) {
        this.question = question;
        this.detail = detail;
        this.yes = yes;
        this.onYes = onYes;
        this.onDone = onDone;
        this.floating = true;
        this.w = 1;
        this.h = 1;
    }

    /** Centred on a screen that size, growing out of its middle the first time. */
    void place(int screenW, int screenH) {
        // A detail longer than a line (the friends' command grant's warning) gets a wider
        // prompt and as many rows as it needs; a one-liner keeps the old size exactly.
        boolean wide = detail.indexOf('\n') >= 0 || detail.length() > 48;
        targetW = Math.min(wide ? W_LONG : W, screenW - 16);
        int inner = Math.round(targetW) - PAD * 2;
        if (inner != wrappedAt) {
            // Placed every frame, wrapped only when the width changes.
            rows = wrap(net.minecraft.client.Minecraft.getInstance().font, detail, inner);
            wrappedAt = inner;
        }
        targetH = Math.min(H + (rows.size() - 1) * ROW_H + (wide ? 4 : 0), screenH - 16);
        targetX = (screenW - targetW) / 2f;
        targetY = (screenH - targetH) / 2f;
        if (w <= 1) {
            x = screenW / 2f;
            y = screenH / 2f;
        }
    }

    void answer(boolean yes) {
        if (answered) return;
        answered = true;
        onDone.run();
        if (yes) onYes.run();
    }

    /** A click on one of its buttons; true for any click at all, since nothing under it may have one. */
    boolean click(Hit hit) {
        if (hit != null && hit.action() == ACT_YES) answer(true);
        else if (hit != null && hit.action() == ACT_NO) answer(false);
        return true;
    }

    @Override
    boolean keyPressed(KeyEvent event) {
        switch (event.key()) {
            case GLFW.GLFW_KEY_Y, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> answer(true);
            case GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_ESCAPE -> answer(false);
            default -> {
            }
        }
        return true;
    }

    @Override
    void close() {
        answer(false);
    }

    @Override
    void render(GuiGraphicsExtractor g, Font font, long now, int mouseX, int mouseY, boolean focused,
                List<Hit> hits, Hit hovered) {
        int wx = ix(), wy = iy(), ww = iw(), wh = ih();
        if (ww < 8 || wh < 8) return;

        g.fillGradient(wx, wy, wx + ww, wy + wh, 0xE6121620, 0xF007080C);
        Decor.border(g, this, wx, wy, ww, wh, true, 0);
        hits.add(new Hit(wx, wy, wx + ww, wy + wh, null, null, -1, null, ACT_BODY));

        g.enableScissor(wx, wy, wx + ww, wy + wh);
        g.fill(wx, wy, wx + ww, wy + HEADER_H, 0x40000000);
        g.fill(wx, wy + HEADER_H - 1, wx + ww, wy + HEADER_H, 0x1CFFFFFF);
        g.text(font, "confirm", wx + PAD, wy + 2, GOLD, false);

        g.text(font, question, wx + PAD, wy + HEADER_H + 7, FG, false);
        int ry = wy + HEADER_H + 19;
        for (String row : rows) {
            // In a warning several rows long the first is the one that matters, so it is brighter.
            g.text(font, row, wx + PAD, ry, ry == wy + HEADER_H + 19 && rows.size() > 1 ? AMBER : DIM, false);
            ry += ROW_H;
        }

        int by = wy + wh - 8 - 12;
        String no = "[ no ]";
        String ok = "[ " + yes + " ]";
        int noX = wx + ww - PAD - font.width(no);
        int okX = noX - 10 - font.width(ok);
        button(g, font, ok, okX, by, RED, ACT_YES, "y / enter", mouseX, mouseY, hits);
        button(g, font, no, noX, by, FG, ACT_NO, "n / esc", mouseX, mouseY, hits);
        g.text(font, "y/n", wx + PAD, by + 2, FAINT, false);
        g.disableScissor();
    }

    /** Word-wrapped to a width in GUI px, a line break starting a new row. */
    private static List<String> wrap(Font font, String text, int width) {
        List<String> out = new java.util.ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder row = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String next = row.length() == 0 ? word : row + " " + word;
                if (row.length() > 0 && font.width(next) > width) {
                    out.add(row.toString());
                    row.setLength(0);
                    row.append(word);
                } else {
                    row.setLength(0);
                    row.append(next);
                }
            }
            out.add(row.toString());
        }
        return out;
    }

    private static void button(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, int action,
                               String hint, int mouseX, int mouseY, List<Hit> hits) {
        int w = font.width(text);
        boolean hot = mouseX >= x - 3 && mouseX < x + w + 3 && mouseY >= y - 2 && mouseY < y + 11;
        g.fill(x - 3, y - 2, x + w + 3, y + 11, hot ? 0x40FFFFFF : 0x1CFFFFFF);
        g.text(font, text, x, y + 1, hot ? mix(color, 0xFFFFFFFF, 0.3f) : color, false);
        Span span = new Span(text, color);
        span.hint = hint;
        hits.add(new Hit(x - 3, y - 2, x + w + 3, y + 11, null, span, -1, null, action));
    }
}
