package com.mineux.core.ui.term;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * "Alex is playing anarchy on 2b2t.org" -- a card that slides down from the top of the screen
 * when a friend turns up somewhere, and fades out on its own.
 * <p>
 * Separate from {@link Notify} rather than another colour of card in it, because the two are
 * different kinds of message and want different places on the screen. A {@code Notify} card is
 * this client confirming something you just did, so it sits over the hotbar where your eyes
 * already are and it is deliberately small. This is news about somebody else that you did not
 * ask for, arriving while you are busy -- so it goes to the top, out of the way of the fight,
 * carries two lines because the second one is the useful one, and is wider and slower.
 * <p>
 * Drawn by {@link Readouts}'s overlay element, like the confirmations, so no mixin and no new
 * hook number. Hidden with the rest of the HUD on F1. The terminal desktop draws
 * them too, under its bar, and there a card can be clicked: a message's card opens the
 * conversation, a request's the friends app.
 * <p>
 * Everything here is synchronized on the class: cards are added from the friends stream thread
 * and drawn from the render thread.
 */
public final class FriendBanner {

    private FriendBanner() {}

    /**
     * The accent colours, public because the friends package raises these cards and {@link Term}
     * is package-private to the terminal. The same values Term uses, named for what they mean
     * here rather than for the colour.
     */
    public static final int ARRIVED = 0xFF5AF78E;
    public static final int ALERT = 0xFFFFA928;
    public static final int SOCIAL = 0xFF57C7FF;

    private static final long LIFE_MS = 5_000;
    private static final long FADE_MS = 700;
    private static final long SLIDE_MS = 260;

    /** Three is already a lot of screen. A fourth replaces the oldest. */
    private static final int MAX = 3;

    private static final int PAD = 7;
    private static final int CARD_H = 24;
    private static final int TOP = 8;

    private static final class Card {
        final String who;
        final String what;
        final int accent;
        final long bornAt;
        /** The desktop window a click on the card opens ({@link TerminalScreen#openWindow}); empty for none. */
        final String open;
        /** Where it was last drawn, for the click. */
        int x0, y0, x1, y1;

        Card(String who, String what, int accent, long bornAt, String open) {
            this.who = who;
            this.what = what;
            this.accent = accent;
            this.bornAt = bornAt;
            this.open = open;
        }
    }

    private static final List<Card> CARDS = new ArrayList<>();

    /**
     * A friend has turned up somewhere.
     *
     * @param who  their name, as you call them
     * @param what where they are and what they are playing, already worded
     */
    public static synchronized void show(String who, String what) {
        add(who, what, ARRIVED, "");
    }

    /** Something about a friend that is not an arrival: a request, a remote instruction. */
    public static synchronized void note(String who, String what, int accent) {
        add(who, what, accent, "");
    }

    /** A message from a friend: who, and what they said; a click on it opens the conversation. */
    public static synchronized void message(String from, String text) {
        String flat = text.replace('\n', ' ');
        add(from, flat.length() > 70 ? flat.substring(0, 67) + "..." : flat, SOCIAL, NoticeLines.CHAT + from);
    }

    /** Somebody asked to be your friend; a click on it opens the friends app, where the request is. */
    public static synchronized void request(String who, String what) {
        add(who, what, SOCIAL, NoticeLines.FRIENDS);
    }

    private static void add(String who, String what, int accent, String open) {
        long now = System.currentTimeMillis();
        // The same friend arriving somewhere twice in a few seconds is one piece of news, and
        // the newer one is the true one: replace rather than stack.
        CARDS.removeIf(card -> card.who.equals(who));
        CARDS.add(new Card(who, what, accent, now, open));
        while (CARDS.size() > MAX) CARDS.remove(0);
    }

    /** Whether any card is still up, so the overlay stays alive for it. */
    static synchronized boolean active() {
        long now = System.currentTimeMillis();
        CARDS.removeIf(card -> now - card.bornAt > LIFE_MS);
        return !CARDS.isEmpty();
    }

    static synchronized void draw(GuiGraphicsExtractor g, Font font, int screenW, long now) {
        draw(g, font, screenW, 0, now);
    }

    /** The cards, the first {@code below} pixels down (under the desktop's bar). */
    static synchronized void draw(GuiGraphicsExtractor g, Font font, int screenW, int below, long now) {
        CARDS.removeIf(card -> now - card.bornAt > LIFE_MS);
        if (CARDS.isEmpty()) return;

        int y = TOP + below;
        for (Card card : CARDS) {
            drawCard(g, font, card, screenW, y, now);
            y += CARD_H + 4;
        }
    }

    /**
     * The window a click at this point opens, the card it was on taken down; null when there is
     * no card that opens anything there.
     */
    static synchronized String clickAt(double mx, double my) {
        long now = System.currentTimeMillis();
        for (int i = CARDS.size() - 1; i >= 0; i--) {
            Card card = CARDS.get(i);
            if (now - card.bornAt > LIFE_MS || card.open.isEmpty()) continue;
            if (mx >= card.x0 && mx < card.x1 && my >= card.y0 && my < card.y1) {
                CARDS.remove(i);
                return card.open;
            }
        }
        return null;
    }

    private static void drawCard(GuiGraphicsExtractor g, Font font, Card card, int screenW, int y, long now) {
        long age = now - card.bornAt;
        long left = LIFE_MS - age;
        float alpha = left < FADE_MS ? Math.max(0f, left / (float) FADE_MS) : 1f;
        if (alpha <= 0.02f) return;

        // Slides in from above and eases out, rather than appearing: a card that simply exists
        // one frame is the kind of thing people see out of the corner of an eye and distrust.
        float in = Math.min(1f, age / (float) SLIDE_MS);
        float eased = 1f - (1f - in) * (1f - in);
        int top = y - Math.round((1f - eased) * (CARD_H + TOP));

        int width = Math.max(font.width(card.who), font.width(card.what)) + PAD * 2 + 4;
        int x = (screenW - width) / 2;
        card.x0 = x;
        card.y0 = top;
        card.x1 = x + width;
        card.y1 = top + CARD_H;

        g.fill(x, top, x + width, top + CARD_H, Decor.fade(0xDC0B0D12, alpha));
        // The accent bar doubles as the clock: it empties as the card ages.
        int bar = Math.max(1, Math.round(width * (left / (float) LIFE_MS)));
        g.fill(x, top + CARD_H - 2, x + width, top + CARD_H - 1, Decor.fade(0x40FFFFFF, alpha));
        g.fill(x, top + CARD_H - 2, x + bar, top + CARD_H - 1, Decor.fade(card.accent, alpha));
        g.fill(x, top, x + 2, top + CARD_H, Decor.fade(card.accent, alpha));

        g.text(font, card.who, x + PAD, top + 4, Decor.fade(Term.FG, alpha), false);
        g.text(font, card.what, x + PAD, top + 14, Decor.fade(Term.DIM, alpha), false);
    }
}
