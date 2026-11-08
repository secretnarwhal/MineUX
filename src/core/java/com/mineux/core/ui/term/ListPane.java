package com.mineux.core.ui.term;

import com.mineux.core.ui.term.Term.Hit;
import com.mineux.core.ui.term.Term.Line;
import com.mineux.core.ui.term.Term.Span;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.Supplier;

import static com.mineux.core.ui.term.Term.*;

/**
 * A window of clickable lines on the main menu's desk: the worlds, the servers. A window like
 * any other on the desk -- tiled beside the terminal, moved by its title bar (or SUPER+drag),
 * resized by its corner (or SUPER+right drag), floated with SUPER+F. Built fresh
 * every frame from whatever it lists, drawn in the terminal's glass, and every click on it is
 * a command the terminal runs and echoes ({@code play 2}, {@code join hypixel}), so whatever
 * the window does can also be typed.
 */
final class ListPane extends Pane {

    static final int HEADER_H = 12;
    private static final int LINE_H = 10;
    private static final int PAD = 5;

    /** What it lists: {@code worlds} or {@code servers}. */
    final String kind;
    private final String title;
    private final Supplier<List<Line>> content;
    private Desktop desktop;
    private int scroll;
    private List<Line> lines;
    private long builtAt;

    ListPane(Desktop desktop, String kind, String title, Supplier<List<Line>> content) {
        this.desktop = desktop;
        this.kind = kind;
        this.title = title;
        this.content = content;
        this.w = 1;
        this.h = 1;
    }

    @Override
    int minW() {
        return 180;
    }

    @Override
    int header() {
        return HEADER_H;
    }

    @Override
    int minH() {
        return HEADER_H + LINE_H * 3;
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
    void scrolled(double mouseY, double amount) {
        scroll = Math.max(0, scroll - (int) Math.signum(amount) * 2);
    }

    @Override
    String title() {
        return kind;
    }

    @Override
    void render(GuiGraphicsExtractor g, Font font, long now, int mouseX, int mouseY, boolean focused,
                List<Hit> hits, Hit hovered) {
        int wx = ix(), wy = iy(), ww = iw(), wh = ih();
        if (ww < 8 || wh < 8) return;

        g.fillGradient(wx, wy, wx + ww, wy + wh, 0xA6121620, 0xBC07080C);
        Decor.border(g, this, wx, wy, ww, wh, focused, 0);
        hits.add(new Hit(wx, wy, wx + ww, wy + wh, null, null, -1, null, ACT_BODY));

        g.enableScissor(wx, wy, wx + ww, wy + wh);

        // Header: what it is, and a close box.
        g.fill(wx, wy, wx + ww, wy + HEADER_H, 0x40000000);
        g.fill(wx, wy + HEADER_H - 1, wx + ww, wy + HEADER_H, 0x1CFFFFFF);
        g.text(font, title, wx + PAD, wy + 2, GOLD, false);
        String close = "x";
        int cx = wx + ww - PAD - font.width(close);
        boolean closeHot = mouseX >= cx - 3 && mouseX < cx + font.width(close) + 3 && mouseY >= wy && mouseY < wy + HEADER_H - 1;
        if (closeHot) g.fill(cx - 3, wy, cx + font.width(close) + 3, wy + HEADER_H - 1, 0x40FF5C57);
        g.text(font, close, cx, wy + 2, closeHot ? RED : DIM, false);
        Span closeSpan = new Span(close, RED);
        closeSpan.hint = "close (esc)";
        hits.add(new Hit(cx - 3, wy, cx + font.width(close) + 3, wy + HEADER_H - 1, null, closeSpan, -1, null, ACT_CLOSE));

        // Rebuilt now and then rather than every frame, so a hovered line is still the same
        // object on the next frame and lights up.
        if (lines == null || now - builtAt > 400) {
            lines = content.get();
            builtAt = now;
        }
        int top = wy + HEADER_H + 4;
        int visible = Math.max(1, (wh - HEADER_H - 8) / LINE_H);
        scroll = Math.max(0, Math.min(scroll, lines.size() - visible));
        int left = wx + PAD;

        g.enableScissor(wx + 1, top - 1, wx + ww - 1, wy + wh - 2);
        for (int i = scroll; i < lines.size() && i < scroll + visible; i++) {
            drawLine(g, font, lines.get(i), left, top + (i - scroll) * LINE_H, hits, hovered);
        }
        g.disableScissor();
        g.disableScissor();
        drawGrip(g, onGrip(mouseX, mouseY));
    }

    private void drawLine(GuiGraphicsExtractor g, Font font, Line line, int left, int y, List<Hit> hits, Hit hovered) {
        int x = left;
        for (Span span : line.spans) {
            if (span.x >= 0) x = Math.max(x + (x > left ? 4 : 0), left + span.x);
            if (span.server != null) {
                ServerIcons.draw(g, span.server, x, y - 1);
                x += ServerIcons.SIZE + 3;
                continue;
            }
            String text = span.text();
            if (text.isEmpty()) continue;
            int width = font.width(text);
            int color = span.color();
            if (span.clickable()) {
                if (hovered != null && hovered.span() == span) {
                    g.fill(x - 1, y - 1, x + width + 1, y + 9, 0x22FFFFFF);
                    g.fill(x, y + 8, x + width, y + 9, color);
                    color = mix(color, 0xFFFFFFFF, 0.3f);
                }
                hits.add(new Hit(x - 1, y - 1, x + width + 1, y + 9, null, span, -1, null, ACT_NONE));
            }
            g.text(font, text, x, y, color, false);
            x += width;
        }
    }
}
