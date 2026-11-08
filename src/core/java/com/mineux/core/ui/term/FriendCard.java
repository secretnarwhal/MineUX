package com.mineux.core.ui.term;

import com.mineux.core.social.Friend;
import com.mineux.core.ui.term.Term.Hit;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

import static com.mineux.core.ui.term.Term.*;

/**
 * One friend's profile in the friends app ({@link FriendsView}): their skin standing on the left
 * and looking at the cursor, their name large beside it with where they are, then what they may
 * do to your client as switches, what they let you do to theirs, and the buttons.
 */
final class FriendCard {

    private FriendCard() {}

    private static final int AVATAR_W = 64, LINE = 11;

    /**
     * Draws the profile scrolled up by {@code scroll} pixels; returns how tall all of it is, for
     * the view to keep the scroll inside it.
     */
    static int draw(FriendsView view, GuiGraphicsExtractor g, Font font, Friend friend, int x, int y, int w, int h,
                    int scroll, int mouseX, int mouseY, List<Hit> hits) {
        FriendsView.card(g, x, y, w, h);
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        FriendsView.clip(y + 1, y + h - 1);
        // A mouse outside the card hovers nothing in it (and does not turn the skin's head through the edge).
        boolean inside = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int mx = inside ? mouseX : Integer.MIN_VALUE, my = inside ? mouseY : Integer.MIN_VALUE;

        int avatarH = Math.max(40, Math.min(118, h - 52));
        int top = y - scroll;
        int ax = x + 8, ay = top + 8;
        g.fill(ax, ay, ax + AVATAR_W, ay + avatarH, 0x24000000);
        g.fillGradient(ax, ay + avatarH - 14, ax + AVATAR_W, ay + avatarH, 0x00000000, 0x30EFC648);
        FriendSkin.draw(g, ax + 2, ay + 4, ax + AVATAR_W - 2, ay + avatarH - 2, mouseX, mouseY,
            friend.skinUuid(), friend.skinName(), friend.user);

        int tx = ax + AVATAR_W + 10;
        int ty = name(g, font, friend, tx, ay);
        ty = facts(g, font, friend, tx, ty + 4, x + w - tx - 8);

        int sy = Math.max(ty + 6, ay + avatarH + 8);
        sy = switches(view, g, font, friend, x + 8, sy, w - 16, mx, my, hits);
        sy = theirs(g, font, friend, x + 8, sy + 4, w - 16);
        // The buttons sit at the bottom when it all fits, straight after the rest when it does not.
        int rows = buttonRows(font, friend, w - 16);
        int by = Math.max(sy + 6, top + h - 8 - rows * BUTTON_ROW);
        int end = buttons(view, g, font, friend, x + 8, by, w - 16, mx, my, hits);
        FriendsView.unclip();
        g.disableScissor();
        int tall = end + 8 - top;
        FriendsView.bar(g, x + w - 3, y + 2, h - 4, scroll, tall, h);
        return tall;
    }

    /** The name at one and a half size, the account and uid under it. Returns the next line. */
    private static int name(GuiGraphicsExtractor g, Font font, Friend friend, int x, int y) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(1.5f, 1.5f);
        g.text(font, friend.display(), 0, 0, GOLD, true);
        g.pose().popMatrix();
        String under = (friend.nickname.isEmpty() ? "" : friend.user + "  ") + "#" + friend.uid;
        g.text(font, under, x, y + 15, DIM, false);
        return y + 15 + LINE;
    }

    /** Where they are, as which Minecraft account, and since when you are friends. */
    private static int facts(GuiGraphicsExtractor g, Font font, Friend friend, int x, int y, int room) {
        int max = Math.max(6, room / 6);
        int dot = friend.online ? GREEN : 0xFF555A68;
        g.fill(x, y + 2, x + 5, y + 7, dot);
        String state;
        int color;
        if (friend.online && !friend.server.isEmpty()) {
            state = "playing " + friend.where();
            color = AMBER;
        } else if (friend.online) {
            state = "online, on the menu";
            color = GREEN;
        } else {
            state = friend.lastAt.isEmpty() ? "offline" : "offline, last seen " + NoticeLines.ago(friend.lastAt) + " ago";
            color = DIM;
        }
        g.text(font, NoticeLines.clip(state, max), x + 9, y, color, false);
        y += LINE;
        String mc = friend.skinName();
        if (!mc.isEmpty()) {
            g.text(font, (friend.mc.isEmpty() ? "last played as " : "as ") , x + 9, y, FAINT, false);
            g.text(font, mc, x + 9 + font.width(friend.mc.isEmpty() ? "last played as " : "as "), y, FG, false);
            y += LINE;
        }
        String since = NoticeLines.ago(friend.since);
        if (!since.isEmpty()) {
            g.text(font, "friends for " + (since.equals("now") ? "a moment" : since), x + 9, y, FAINT, false);
            y += LINE;
        }
        if (friend.muted) {
            g.text(font, "muted", x + 9, y, AMBER, false);
            y += LINE;
        }
        return y;
    }

    /** What they may do to your client: a switch a line, flipped the moment it is clicked. */
    private static int switches(FriendsView view, GuiGraphicsExtractor g, Font font, Friend friend, int x, int y, int w,
                                int mouseX, int mouseY, List<Hit> hits) {
        g.text(font, "WHAT " + friend.display().toUpperCase(java.util.Locale.ROOT) + " MAY DO", x, y, FAINT, false);
        y += LINE + 2;
        y = toggle(view, g, font, friend, x, y, w, "Send you to servers", "joins whatever server they choose",
            friend.allowJoin, "remoteJoin", mouseX, mouseY, hits);
        y = toggle(view, g, font, friend, x, y, w, "Run commands", "any terminal command in your client - asks first",
            friend.allowCommand, "remoteCommand", mouseX, mouseY, hits);
        y = toggle(view, g, font, friend, x, y, w, "See your server", "your presence: server, mode and account",
            friend.sharing, "sharePresence", mouseX, mouseY, hits);
        y = toggle(view, g, font, friend, x, y, w, "Enable screens", "both of you need this on to see each other's screens",
            friend.screens, "screens", mouseX, mouseY, hits);
        return y;
    }

    private static int toggle(FriendsView view, GuiGraphicsExtractor g, Font font, Friend friend, int x, int y, int w,
                              String label, String what, boolean on, String field, int mouseX, int mouseY, List<Hit> hits) {
        boolean hot = mouseX >= x && mouseX < x + w && mouseY >= y - 1 && mouseY < y + 19;
        if (hot) g.fill(x - 2, y - 1, x + w + 2, y + 19, 0x10FFFFFF);
        g.text(font, label, x, y, on ? FG : DIM, false);
        g.text(font, NoticeLines.clip(what, Math.max(6, (w - 30) / 6)), x, y + 9, FAINT, false);
        Runnable click = () -> view.cardFlip(friend, field, !on);
        FriendsView.toggle(g, hits, x + w - 20, y + 4, on, mouseX, mouseY, click);
        FriendsView.hit(hits, x, y - 1, x + w - 24, y + 19, click);
        return y + 21;
    }

    /** What they let you do to their client: shown, not settable -- it is theirs to give. */
    private static int theirs(GuiGraphicsExtractor g, Font font, Friend friend, int x, int y, int w) {
        g.text(font, "WHAT THEY LET YOU DO", x, y, FAINT, false);
        y += LINE + 1;
        y = allowed(g, font, x, y, "send them to your server", friend.theyAllowJoin);
        y = allowed(g, font, x, y, "run commands in their client", friend.theyAllowCommand);
        return allowed(g, font, x, y, NoticeLines.clip(friend.display(), 16) + " has screens " + (friend.theyScreens ? "on" : "off"),
            friend.theyScreens);
    }

    private static int allowed(GuiGraphicsExtractor g, Font font, int x, int y, String what, boolean yes) {
        g.fill(x, y + 1, x + 7, y + 8, yes ? 0xFF2E8B57 : 0xFF2A2E3A);
        g.text(font, yes ? "+" : "-", x + 1, y, yes ? 0xFFFFFFFF : FAINT, false);
        g.text(font, what, x + 11, y, yes ? FG : FAINT, false);
        return y + LINE;
    }

    /** One row of buttons and the gap under it. */
    private static final int BUTTON_ROW = 15;

    /** The buttons' labels, in order: which there are depends on the friend. */
    private static String[] labels(Friend friend) {
        List<String> out = new java.util.ArrayList<>();
        out.add("message");
        if (friend.somewhere()) out.add("join");
        if (friend.theyAllowJoin) out.add("bring here");
        out.add(friend.muted ? "unmute" : "mute");
        out.add("remove");
        return out.toArray(new String[0]);
    }

    private static int buttonW(Font font, String label) {
        return font.width(" " + label + " ") + 4;
    }

    /** How many rows the buttons take in {@code w}: they wrap rather than run off the card. */
    static int buttonRows(Font font, Friend friend, int w) {
        int rows = 1, bx = 0;
        for (String label : labels(friend)) {
            int bw = buttonW(font, label);
            if (bx > 0 && bx + bw > w) {
                rows++;
                bx = 0;
            }
            bx += bw + 4;
        }
        return rows;
    }

    /** The buttons from {@code y}, wrapping inside {@code w}; returns the bottom of the last row. */
    private static int buttons(FriendsView view, GuiGraphicsExtractor g, Font font, Friend friend, int x, int y, int w,
                               int mouseX, int mouseY, List<Hit> hits) {
        int bx = x, by = y;
        for (String label : labels(friend)) {
            int bw = buttonW(font, label);
            if (bx > x && bx + bw > x + w) {
                bx = x;
                by += BUTTON_ROW;
            }
            bx = FriendsView.button(g, font, hits, bx, by, label, colour(label), mouseX, mouseY, click(view, friend, label)) + 4;
        }
        return by + 12;
    }

    private static int colour(String label) {
        if (java.util.Objects.equals("join", label)) return GREEN;
        if (java.util.Objects.equals("mute", label) || java.util.Objects.equals("unmute", label)) return AMBER;
        if (java.util.Objects.equals("remove", label)) return RED;
        return CYAN;
    }

    // An if chain on the label, not a string switch: see Words for why this code has none.
    private static Runnable click(FriendsView view, Friend friend, String label) {
        if (java.util.Objects.equals("join", label)) return () -> view.cardJoin(friend);
        if (java.util.Objects.equals("bring here", label)) return () -> view.cardSend(friend);
        if (java.util.Objects.equals("mute", label) || java.util.Objects.equals("unmute", label)) {
            return () -> view.cardFlip(friend, "muted", !friend.muted);
        }
        if (java.util.Objects.equals("remove", label)) return () -> view.cardRemove(friend);
        return () -> view.cardMessage(friend);
    }
}
