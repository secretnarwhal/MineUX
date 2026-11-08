package com.mineux.core.ui.term;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

import static com.mineux.core.ui.term.Term.*;

/**
 * The modules' confirmations -- "anchor: popped", "totem to offhand", a keybind toggling
 * something -- as small cards over the hotbar rather than lines on the action bar.
 * <p>
 * Each card names the module it came from, and while that module has steps in progress
 * ({@link Trace}) it carries the module's sequence bar under the text, so a confirmation for
 * something still running shows how far along it is. The same message again inside a card's
 * life refreshes that card and counts it, rather than stacking a copy. Cards slide in, and
 * fade out at the end of their life.
 * <p>
 * Drawn by the readouts' overlay ({@link Readouts}), over the game, not over the desktop.
 */
public final class Notify {

    /** A good outcome, an informational one, a refusal, and something to look at. */
    public static final int GOOD = 0xFF5AF78E, INFO = 0xFFEFC648, BAD = 0xFFFF5C57, NOTE = 0xFF57C7FF;

    private static final long LIFE_MS = 2200;
    private static final long FADE_MS = 450;
    private static final long SLIDE_MS = 140;
    private static final int MAX = 4;
    private static final int CARD_H = 15;

    private static final class Card {
        final String id;
        final String text;
        final int color;
        long at;
        long bornAt;
        int count = 1;

        Card(String id, String text, int color, long now) {
            this.id = id;
            this.text = text;
            this.color = color;
            this.at = now;
            this.bornAt = now;
        }
    }

    private static final List<Card> CARDS = new ArrayList<>();

    private Notify() {}

    /** A confirmation from module {@code id}, in {@code color} (see {@link #GOOD} and on). */
    public static synchronized void show(String id, String text, int color) {
        long now = System.currentTimeMillis();
        for (Card c : CARDS) {
            if (c.id.equals(id) && c.text.equals(text) && now - c.at < LIFE_MS) {
                c.at = now;
                c.count++;
                return;
            }
        }
        CARDS.add(0, new Card(id, text, color, now));
        while (CARDS.size() > MAX) CARDS.remove(CARDS.size() - 1);
    }

    /** Whether any card is still on show, so the overlay stays up for it. */
    static synchronized boolean active() {
        long now = System.currentTimeMillis();
        CARDS.removeIf(c -> now - c.at > LIFE_MS);
        return !CARDS.isEmpty();
    }

    /** The cards, newest nearest the hotbar, stacking upwards. */
    static synchronized void draw(GuiGraphicsExtractor g, Font font, int screenW, int screenH, long now) {
        CARDS.removeIf(c -> now - c.at > LIFE_MS);
        int y = screenH - 60 - CARD_H;
        for (Card c : CARDS) {
            drawCard(g, font, c, screenW, y, now);
            y -= CARD_H + 3;
        }
    }

    private static void drawCard(GuiGraphicsExtractor g, Font font, Card c, int screenW, int y, long now) {
        long left = LIFE_MS - (now - c.at);
        float alpha = left < FADE_MS ? Math.max(0f, left / (float) FADE_MS) : 1f;
        float in = Math.min(1f, (now - c.bornAt) / (float) SLIDE_MS);
        if (alpha <= 0.02f) return;

        String tag = c.id;
        String text = c.count > 1 ? c.text + "  x" + c.count : c.text;
        int w = font.width(tag) + font.width(text) + 16;
        int x = (screenW - w) / 2;
        int top = y + Math.round((1f - in) * 6f);

        g.fill(x, top, x + w, top + CARD_H, Decor.fade(0xD00B0D12, alpha));
        g.fill(x, top, x + 2, top + CARD_H, Decor.fade(c.color, alpha));
        g.text(font, tag, x + 6, top + 3, Decor.fade(c.color, alpha), false);
        g.text(font, text, x + 10 + font.width(tag), top + 3, Decor.fade(FG, alpha), false);

        // The module's sequence while it is still running, else a thin line in its colour.
        Trace.Run run = Trace.get(c.id);
        if (run != null && run.busy(now)) {
            Meter.sequence(g, x + 6, top + CARD_H - 3, w - 10, 2, run, now, alpha);
        } else {
            g.fill(x + 6, top + CARD_H - 2, x + w - 4, top + CARD_H - 1, Decor.fade(c.color & 0x60FFFFFF, alpha));
        }
    }
}
