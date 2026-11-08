package com.mineux.core.ui.term;

import com.mineux.core.ui.term.Term.Hit;
import com.mineux.core.ui.term.Term.Span;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

import static com.mineux.core.ui.term.Term.*;

/**
 * One frame of a scrolling window's body, drawn top to bottom: where the next thing goes
 * ({@link #y}), and the few shapes {@link LookupPane} is made of -- text, a section rule, a
 * stat tile, a big score card, a button. Nothing scrolled out of the window can be clicked.
 * Public for an extension's programs and windows (see {@code Extensions} and {@code DeskExtensions}).
 */
public final class Sheet {

    public static final int LINE_H = 10;
    public static final int TILE_H = 21;
    public static final int CARD_H = 40;

    public final GuiGraphicsExtractor g;
    public final Font font;
    public final int mouseX, mouseY;
    /** The body's left edge and width, inside the window's padding. */
    public final int left, width;
    private final List<Hit> hits;
    private final Hit hovered;
    private final int top, bottom;
    public int y;

    public Sheet(GuiGraphicsExtractor g, Font font, List<Hit> hits, Hit hovered, int mouseX, int mouseY,
          int left, int width, int top, int bottom, int y) {
        this.g = g;
        this.font = font;
        this.hits = hits;
        this.hovered = hovered;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.left = left;
        this.width = width;
        this.top = top;
        this.bottom = bottom;
        this.y = y;
    }

    /** @return the x after the text */
    public int text(String text, int x, int color) {
        g.text(font, text, x, y, color, false);
        return x + font.width(text);
    }

    /** Text ending at {@code right}. */
    public void textRight(String text, int right, int color) {
        g.text(font, text, right - font.width(text), y, color, false);
    }

    /** A label, then its value, and on to the next line. */
    public void row(String label, String value, int color) {
        text(label, left, DIM);
        text(font.plainSubstrByWidth(value, width - 52), left + 52, color);
        y += LINE_H;
    }

    /** A source's heading: its name, a rule, and its state on the right. */
    public void section(String title, String state, int stateColor) {
        y += 4;
        int end = text(title, left, GOLD);
        int stateX = left + width - font.width(state);
        g.fill(end + 4, y + 4, Math.max(end + 4, stateX - 4), y + 5, 0x1CFFFFFF);
        g.text(font, state, stateX, y, stateColor, false);
        y += LINE_H + 2;
    }

    /** A small stat: its value over its label. */
    public void tile(int x, int w, String value, String label, int color) {
        g.fill(x, y, x + w, y + TILE_H - 1, 0x18FFFFFF);
        g.text(font, value, x + (w - font.width(value)) / 2, y + 2, color, false);
        g.text(font, label, x + (w - font.width(label)) / 2, y + 11, DIM, false);
    }

    /**
     * A title card: a score out of 100 at twice the size, what it scores, and a bar of it.
     *
     * @return true while the mouse is on it
     */
    public boolean card(int x, int w, String score, String label, float progress, int color) {
        boolean hot = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + CARD_H - 1 && mouseY >= top && mouseY < bottom;
        g.fill(x, y, x + w, y + CARD_H - 1, hot ? 0x30FFFFFF : 0x1CFFFFFF);
        g.fill(x, y, x + w, y + 1, Decor.fade(color, 0.7f));
        g.pose().pushMatrix();
        g.pose().translate(x + (w - font.width(score) * 2) / 2f, y + 5);
        g.pose().scale(2f, 2f);
        g.text(font, score, 0, 0, color, false);
        g.pose().popMatrix();
        String fitted = font.plainSubstrByWidth(label, w - 2);
        g.text(font, fitted, x + (w - font.width(fitted)) / 2, y + 24, hot ? FG : DIM, false);
        Meter.bar(g, x + 3, y + CARD_H - 6, w - 6, 3, progress, color, 1f);
        return hot;
    }

    /** Wins against losses as one bar, green then red; nothing for no games. */
    public void record(int wins, int losses) {
        int games = wins + losses;
        if (games <= 0) return;
        int split = left + Math.round(width * (wins / (float) games));
        g.fill(left, y, split, y + 3, 0xC05AF78E);
        g.fill(split, y, left + width, y + 3, 0xC0FF5C57);
        y += 6;
    }

    /** @return the x after the button, or after it on the next line when it did not fit on this one */
    public int button(Span span, int x) {
        String text = span.text();
        int w = font.width(text) + 6;
        if (x + w > left + width && x > left) {
            x = left;
            y += LINE_H + 3;
        }
        boolean hot = hovered != null && hovered.span() == span;
        g.fill(x, y - 2, x + w, y + 10, hot ? 0x50EFC648 : 0x22FFFFFF);
        g.text(font, text, x + 3, y, hot ? 0xFFFFFFFF : span.color(), false);
        click(span, x, y - 2, x + w, y + 10);
        return x + w + 3;
    }

    /** Text that is clicked. @return the x after it */
    public int link(Span span, int x) {
        String text = span.text();
        int w = font.width(text);
        boolean hot = hovered != null && hovered.span() == span;
        if (hot) g.fill(x, y + 8, x + w, y + 9, span.color());
        g.text(font, text, x, y, hot ? mix(span.color(), 0xFFFFFFFF, 0.3f) : span.color(), false);
        click(span, x - 1, y - 1, x + w + 1, y + 9);
        return x + w;
    }

    private void click(Span span, int x0, int y0, int x1, int y1) {
        if (y1 <= top || y0 >= bottom) return;
        hits.add(new Hit(x0, Math.max(y0, top), x1, Math.min(y1, bottom), null, span, -1, null, ACT_NONE));
    }
}
