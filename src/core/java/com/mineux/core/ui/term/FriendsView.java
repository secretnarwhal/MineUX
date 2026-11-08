package com.mineux.core.ui.term;

import com.google.gson.JsonObject;
import com.mineux.core.social.Friend;
import com.mineux.core.social.FriendConfig;
import com.mineux.core.social.FriendList;
import com.mineux.core.social.FriendOps;
import com.mineux.core.social.FriendTalk;
import com.mineux.core.social.ServerName;
import com.mineux.core.ui.term.Term.Hit;
import com.mineux.core.ui.term.Term.Span;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.mineux.core.ui.term.Term.*;

/**
 * {@code friends}: the friends list as a program that takes over the terminal, like irc. The
 * list down the left (requests, online, offline, a face each), the chosen friend's profile on
 * the right: their skin in 3D looking at the cursor, where they are, and every setting as a
 * switch that flips the moment it is clicked ({@link FriendTalk#grant}). A line at the bottom
 * adds a friend; it takes the keys only once it is clicked, so opening the app never puts the
 * cursor there. The list and the profile scroll on their own, whichever the mouse is over. Up/down
 * choose, enter messages the chosen one, ctrl+c quits.
 */
final class FriendsView {

    private static final int HEADER_H = 16, FOOTER_H = 15, ROW_H = 20, PAD = 5, SECTION_H = 12;
    private static final int CARD = 0x30000000, EDGE = 0x1CFFFFFF, HOVER = 0x16FFFFFF, CHOSEN = 0x2AEFC648;
    private static final int TRACK_ON = 0xFF2E8B57, TRACK_OFF = 0xFF2A2E3A, KNOB = 0xFFE9ECF2;
    /** How far one notch of the wheel moves the profile, in pixels. */
    private static final int CARD_STEP = 14;
    /** The add-friend field's width. */
    private static final int FIELD_W = 92;

    /**
     * The size the app wants its window to open at: the whole profile (skin, switches, every
     * button) without scrolling. The bar's friends button opens it at this, as far as the screen allows.
     */
    static final int WANT_W = 470, WANT_H = 330;

    private final Shell shell;
    private String chosen = "";
    private final StringBuilder input = new StringBuilder();
    private String status = "";
    private int statusColor = DIM;
    private long statusAt;
    /** The list's scroll, in rows, and the profile's, in pixels. */
    private int scroll;
    private int cardScroll;
    /** Whether the add-friend field has the keys: only after a click on it, never on opening. */
    private boolean typing;
    /** Where the list ends and the profile begins, as last drawn, for the wheel. */
    private int split = Integer.MAX_VALUE;
    private String cardOf = "";
    private boolean done;

    /** The names in list order, as last drawn, for up/down. */
    private final List<String> order = new ArrayList<>();

    FriendsView(Shell shell, String target) {
        this.shell = shell;
        show(target);
    }

    /** Chooses a friend by account name; empty keeps the choice. */
    void show(String target) {
        if (target != null && !target.isEmpty()) {
            Friend friend = FriendList.find(target);
            chosen = friend != null ? friend.user : target;
        }
    }

    boolean done() {
        return done;
    }

    // ---- keys ---------------------------------------------------------------

    /** Whether the add-friend field has the keys. */
    boolean typing() {
        return typing;
    }

    /** Gives the add-friend field the keys, as a click on it does; false takes them away. */
    void type(boolean on) {
        typing = on;
    }

    String typed() {
        return input.toString();
    }

    void charTyped(CharacterEvent event) {
        if (!typing) return;
        String s = event.codepointAsString();
        if (input.length() < 32 && s.length() == 1 && (Character.isLetterOrDigit(s.charAt(0)) || s.charAt(0) == '_' || s.charAt(0) == '#')) {
            input.append(s);
        }
    }

    void keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (typing && input.length() > 0) add(input.toString());
            else if (FriendList.find(chosen) != null) message(chosen);
        } else if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!typing) return;
            if (event.hasControlDown()) input.setLength(0);
            else if (input.length() > 0) input.setLength(input.length() - 1);
        } else if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            step(key == GLFW.GLFW_KEY_UP ? 0 - 1 : 1);
        } else if (key == GLFW.GLFW_KEY_TAB) {
            typing = !typing;
        } else if (key == GLFW.GLFW_KEY_Q && !typing) {
            done = true;
        }
    }

    /** The wheel: the list when the mouse is over it, else the profile. */
    void scrolled(double mouseX, double amount) {
        int notch = (int) Math.signum(amount);
        if (mouseX < split) scroll = Math.max(0, scroll - notch);
        else cardScroll = Math.max(0, cardScroll - notch * CARD_STEP);
    }

    int listScroll() {
        return scroll;
    }

    int cardScroll() {
        return cardScroll;
    }

    private void step(int by) {
        if (order.isEmpty()) return;
        int at = order.indexOf(chosen);
        chosen = order.get(Math.floorMod(at < 0 ? 0 : at + by, order.size()));
    }

    // ---- doing ----------------------------------------------------------------

    private void say(String text, int color) {
        status = text;
        statusColor = color;
        statusAt = System.currentTimeMillis();
    }

    private void add(String who) {
        input.setLength(0);
        typing = false;
        say("asking " + who + "...", DIM);
        FriendTalk.postLater(FriendOps.REQUEST, FriendTalk.who(who),
            why -> say(why == null ? "asked " + who + " - they will see it in their bell" : why, why == null ? GREEN : RED));
    }

    private void answer(String path, String who, String did) {
        FriendTalk.postLater(path, FriendTalk.who(who), why -> say(why == null ? did : why, why == null ? GREEN : RED));
    }

    private void message(String user) {
        com.mineux.core.social.Notices.readFrom(user);
        if (!shell.launch(Shell.APP_IRC + ":@" + user)) say("needs a terminal", RED);
    }

    private void flip(Friend friend, String field, boolean on) {
        if (on && java.util.Objects.equals("remoteCommand", field)) {
            // The one that hands over the whole client: never without the warning answered yes.
            shell.confirm("let " + friend.display() + " run commands in your client?", FriendCommands.COMMAND_WARNING, "allow",
                () -> FriendTalk.grant(friend.user, field, true, this::failed));
            return;
        }
        FriendTalk.grant(friend.user, field, on, this::failed);
    }

    private void failed(String why) {
        if (why != null) say(why, RED);
    }

    private void remove(Friend friend) {
        shell.confirm("remove " + friend.display() + " from your friends?",
            "Every setting between you goes with them. Adding them again starts a new request.", "remove",
            () -> answer(FriendOps.REMOVE, friend.user, friend.display() + " removed"));
    }

    /**
     * Joins the server their presence shows, the way the terminal's {@code join} does: the world
     * you are in left properly first, and not for an account with multiplayer switched off.
     */
    private void join(Friend friend) {
        if (!friend.somewhere()) {
            say(friend.display() + " is not on a server you can see", DIM);
            return;
        }
        if (ServerName.on(Minecraft.getInstance(), friend.server)) {
            say("you are already on " + friend.server, DIM);
            return;
        }
        MenuButtons menu = shell.menu();
        MenuButtons.Entry multiplayer = menu == null ? null : menu.find("multiplayer");
        if (multiplayer != null && !multiplayer.enabled()) {
            say("multiplayer is disabled for this account", RED);
            return;
        }
        Screen title = menu != null && !menu.pause() ? menu.title() : new TitleScreen();
        say("joining " + friend.server + "...", AMBER);
        String error = Servers.joinAddress(friend.server, title);
        if (error != null) say(error, RED);
    }

    private void sendHere(Friend friend) {
        String server = ServerName.current(Minecraft.getInstance());
        if (server.isEmpty()) {
            say("you are not on a server", RED);
            return;
        }
        JsonObject body = FriendTalk.who(friend.user);
        body.addProperty("server", server);
        FriendTalk.postLater(FriendOps.REMOTE_JOIN, body,
            why -> say(why == null ? "asked their client to join " + server : why, why == null ? GREEN : RED));
    }

    // ---- drawing ----------------------------------------------------------------

    void render(GuiGraphicsExtractor g, Font font, long now, int x, int y, int w, int h, int mouseX, int mouseY, List<Hit> hits) {
        if (Guest.is()) {
            Guest.draw(g, font, x, y, w, h);
            return;
        }
        // Under everything else: a click anywhere that is not a button or the field takes the keys
        // back from the field.
        hit(hits, x, y, x + w, y + h, () -> typing = false);
        header(g, font, x, y, w);
        int top = y + HEADER_H + 3, bottom = y + h - FOOTER_H - 2;
        if (!FriendConfig.get().enabled || !FriendList.connected()) {
            split = Integer.MAX_VALUE;
            offline(g, font, x, top, w, mouseX, mouseY, hits);
        } else {
            int listW = Math.max(110, Math.min(170, (int) (w * 0.36f)));
            list(g, font, x + PAD, top, listW, bottom - top, mouseX, mouseY, hits);
            Friend friend = FriendList.find(chosen);
            int px = x + PAD + listW + 4;
            int pw = x + w - PAD - px;
            split = px - 2;
            if (friend != null) {
                // Somebody else's profile starts at its top.
                if (!friend.user.equalsIgnoreCase(cardOf)) cardScroll = 0;
                cardOf = friend.user;
                int tall = FriendCard.draw(this, g, font, friend, px, top, pw, bottom - top, cardScroll, mouseX, mouseY, hits);
                cardScroll = Math.min(cardScroll, Math.max(0, tall - (bottom - top)));
            } else {
                card(g, px, top, pw, bottom - top);
                g.text(font, FriendList.all().isEmpty() ? "add a friend below" : "choose somebody on the left", px + 10, top + 10, FAINT, false);
            }
        }
        footer(g, font, now, x, y + h - FOOTER_H, w, mouseX, mouseY, hits);
    }

    private void header(GuiGraphicsExtractor g, Font font, int x, int y, int w) {
        g.fill(x, y, x + w, y + HEADER_H, 0x40000000);
        g.fill(x, y + HEADER_H - 1, x + w, y + HEADER_H, EDGE);
        String title = " friends ";
        g.fill(x + PAD, y + 3, x + PAD + font.width(title), y + HEADER_H - 3, GOLD);
        g.text(font, title, x + PAD, y + 4, 0xFF15110A, false);
        int tx = x + PAD + font.width(title) + 8;
        String online = FriendList.onlineCount() + " online";
        g.text(font, online, tx, y + 4, GREEN, false);
        g.text(font, "  of " + FriendList.all().size(), tx + font.width(online), y + 4, DIM, false);
        String me = FriendList.selfUser().isEmpty() ? "" : FriendList.selfUser() + " #" + FriendList.selfUid();
        g.text(font, me, x + w - PAD - font.width(me), y + 4, FAINT, false);
    }

    private void offline(GuiGraphicsExtractor g, Font font, int x, int top, int w, int mouseX, int mouseY, List<Hit> hits) {
        String why = !FriendConfig.get().enabled ? "friends are switched off" : FriendList.error().isEmpty() ? "connecting..." : FriendList.error();
        g.text(font, why, x + PAD + 4, top + 8, AMBER, false);
        button(g, font, hits, x + PAD + 4, top + 22, "connect", CYAN, mouseX, mouseY, () -> FriendTalk.connect());
    }

    private void list(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, List<Hit> hits) {
        card(g, x, y, w, h);
        List<Friend> all = FriendList.all();
        List<Friend> online = new ArrayList<>();
        List<Friend> away = new ArrayList<>();
        for (Friend friend : all) (friend.online ? online : away).add(friend);
        order.clear();
        for (Friend friend : online) order.add(friend.user);
        for (Friend friend : away) order.add(friend.user);
        if (FriendList.find(chosen) == null && !order.isEmpty()) chosen = order.get(0);

        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        clip(y + 1, y + h - 1);
        int at = y + 4 - scroll * ROW_H;
        List<String> asking = FriendList.requests();
        List<String> asked = FriendList.sent();
        if (!asking.isEmpty() || !asked.isEmpty()) {
            at = section(g, font, x, at, "requests", asking.size() + asked.size());
            for (String who : asking) at = request(g, font, x, at, w, who, true, mouseX, mouseY, hits);
            for (String who : asked) at = request(g, font, x, at, w, who, false, mouseX, mouseY, hits);
        }
        at = section(g, font, x, at, "online", online.size());
        for (Friend friend : online) at = row(g, font, x, at, w, friend, mouseX, mouseY, hits);
        at = section(g, font, x, at, "offline", away.size());
        for (Friend friend : away) at = row(g, font, x, at, w, friend, mouseX, mouseY, hits);
        unclip();
        g.disableScissor();
        // Scrolls in whole rows, up to where the last row is in view.
        int tall = at + scroll * ROW_H - y + 4;
        scroll = Math.min(scroll, Math.max(0, (tall - h + ROW_H - 1) / ROW_H));
        bar(g, x + w - 3, y + 2, h - 4, scroll * ROW_H, tall, h);
    }

    /** A thin scroll thumb down a pane's right edge, for content {@code tall} high scrolled {@code offset}, {@code shown} of it in view. */
    static void bar(GuiGraphicsExtractor g, int x, int y, int h, int offset, int tall, int shown) {
        if (tall <= shown || h <= 4) return;
        int thumb = Math.max(8, h * shown / tall);
        int at = y + (h - thumb) * Math.min(offset, tall - shown) / Math.max(1, tall - shown);
        g.fill(x, y, x + 2, y + h, 0x14FFFFFF);
        g.fill(x, at, x + 2, at + thumb, 0x60FFFFFF);
    }

    private static int section(GuiGraphicsExtractor g, Font font, int x, int y, String label, int count) {
        g.text(font, label.toUpperCase(java.util.Locale.ROOT) + "  " + count, x + 6, y + 2, FAINT, false);
        return y + SECTION_H;
    }

    private int row(GuiGraphicsExtractor g, Font font, int x, int y, int w, Friend friend, int mouseX, int mouseY, List<Hit> hits) {
        boolean hot = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_H;
        boolean mine = friend.user.equalsIgnoreCase(chosen);
        if (mine) {
            g.fill(x + 1, y, x + w - 1, y + ROW_H, CHOSEN);
            g.fill(x + 1, y, x + 3, y + ROW_H, GOLD);
        } else if (hot) {
            g.fill(x + 1, y, x + w - 1, y + ROW_H, HOVER);
        }
        FriendSkin.face(g, x + 6, y + 3, 14, friend.skinUuid(), friend.skinName(), friend.user);
        if (!friend.online) g.fill(x + 6, y + 3, x + 20, y + 17, 0x88000000);
        g.fill(x + 16, y + 13, x + 21, y + 18, 0xFF0A0B0F);
        g.fill(x + 17, y + 14, x + 20, y + 17, friend.online ? GREEN : 0xFF555A68);

        int tx = x + 25;
        // Where their presence shows a server, a join button at the row's end takes you there.
        boolean joinable = friend.somewhere();
        int room = w - 30 - (joinable ? font.width(" join ") + 10 : 0);
        g.text(font, NoticeLines.clip(friend.display(), Math.max(4, room / 6)), tx, y + 2, friend.online ? FG : DIM, false);
        String where = !friend.online ? friend.lastAt.isEmpty() ? "offline" : "seen " + NoticeLines.ago(friend.lastAt) + " ago"
            : friend.server.isEmpty() ? "online" : friend.server;
        g.text(font, NoticeLines.clip(where, Math.max(4, room / 6)), tx, y + 11, friend.online && !friend.server.isEmpty() ? AMBER : FAINT, false);

        String user = friend.user;
        hit(hits, x, y, x + w, y + ROW_H, () -> {
            chosen = user;
            typing = false;
        });
        if (joinable) {
            button(g, font, hits, x + w - 8 - font.width(" join ") - 4, y + 4, "join", GREEN, mouseX, mouseY, () -> join(friend));
        }
        return y + ROW_H;
    }

    private int request(GuiGraphicsExtractor g, Font font, int x, int y, int w, String who, boolean toMe, int mouseX, int mouseY, List<Hit> hits) {
        FriendSkin.face(g, x + 6, y + 3, 14, "", "", who);
        g.text(font, NoticeLines.clip(who, Math.max(4, (w - 70) / 6)), x + 25, y + 2, FG, false);
        g.text(font, toMe ? "wants to be friends" : "you asked", x + 25, y + 11, toMe ? AMBER : FAINT, false);
        int bx = x + w - 6;
        if (toMe) {
            bx = button(g, font, hits, bx - font.width(" x ") - 4, y + 4, "x", RED, mouseX, mouseY,
                () -> answer(FriendOps.DECLINE, who, "declined " + who)) - font.width(" x ") - 4;
            button(g, font, hits, bx - font.width(" accept ") - 8, y + 4, "accept", GREEN, mouseX, mouseY,
                () -> answer(FriendOps.ACCEPT, who, who + " is your friend now"));
        } else {
            button(g, font, hits, bx - font.width(" cancel ") - 4, y + 4, "cancel", AMBER, mouseX, mouseY,
                () -> answer(FriendOps.DECLINE, who, "took back the request to " + who));
        }
        return y + ROW_H;
    }

    private void footer(GuiGraphicsExtractor g, Font font, long now, int x, int y, int w, int mouseX, int mouseY, List<Hit> hits) {
        g.fill(x, y, x + w, y + FOOTER_H, 0x40000000);
        g.fill(x, y, x + w, y + 1, EDGE);
        int ty = y + 4;
        String label = "+ add friend ";
        g.text(font, label, x + PAD, ty, GREEN, false);
        int ix = x + PAD + font.width(label);
        String typed = input.length() == 0 ? "" : input.toString();
        boolean hot = mouseX >= x + PAD && mouseX < ix + FIELD_W && mouseY >= y + 1 && mouseY < y + FOOTER_H;
        g.fill(ix - 2, y + 2, ix + FIELD_W, y + FOOTER_H - 2, typing ? 0x40FFFFFF : hot ? 0x26FFFFFF : 0x18FFFFFF);
        if (typing) g.fill(ix - 2, y + FOOTER_H - 3, ix + FIELD_W, y + FOOTER_H - 2, GREEN);
        g.text(font, typed, ix, ty, typing ? FG : DIM, false);
        if (typing && now / 500 % 2 == 0) g.fill(ix + font.width(typed), ty - 1, ix + font.width(typed) + 1, ty + 9, FG);
        if (typed.isEmpty()) g.text(font, typing ? "account or #uid" : "click to type", ix + 2, ty, FAINT, false);
        // The label and the field: a click there gives the field the keys.
        hit(hits, x + PAD, y + 1, ix + FIELD_W, y + FOOTER_H, () -> typing = true);

        boolean fresh = System.currentTimeMillis() - statusAt < 8000L;
        String keys = typing ? "enter: send   tab: done   ctrl+c: quit" : "enter: message   up down: choose   ctrl+c: quit";
        String right = fresh && !status.isEmpty() ? status : keys;
        int rw = font.width(right);
        if (ix + FIELD_W + 10 + rw < x + w - PAD) g.text(font, right, x + w - PAD - rw, ty, fresh ? statusColor : FAINT, false);
    }

    // ---- pieces the card uses too -------------------------------------------------

    static void card(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, CARD);
        g.fill(x, y, x + w, y + 1, EDGE);
        g.fill(x, y + h - 1, x + w, y + h, EDGE);
        g.fill(x, y, x + 1, y + h, EDGE);
        g.fill(x + w - 1, y, x + w, y + h, EDGE);
    }

    /** A boxed button; returns its right edge. */
    static int button(GuiGraphicsExtractor g, Font font, List<Hit> hits, int x, int y, String label, int color,
                      int mouseX, int mouseY, Runnable click) {
        String text = " " + label + " ";
        int w = font.width(text) + 4;
        boolean hot = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 12;
        g.fill(x, y, x + w, y + 12, hot ? mix(color, 0x00000000, 0.55f) : 0x40000000);
        g.fill(x, y + 11, x + w, y + 12, color);
        g.text(font, text, x + 2, y + 2, hot ? 0xFFFFFFFF : color, false);
        hit(hits, x, y, x + w, y + 12, click);
        return x + w;
    }

    /** A switch: a track that is green while on, the knob at the end it is set to. */
    static void toggle(GuiGraphicsExtractor g, List<Hit> hits, int x, int y, boolean on, int mouseX, int mouseY, Runnable click) {
        int w = 18, h = 9;
        boolean hot = mouseX >= x - 2 && mouseX < x + w + 2 && mouseY >= y - 2 && mouseY < y + h + 2;
        g.fill(x, y, x + w, y + h, on ? TRACK_ON : TRACK_OFF);
        if (hot) g.fill(x, y, x + w, y + h, 0x22FFFFFF);
        int kx = on ? x + w - h + 1 : x + 1;
        g.fill(kx, y + 1, kx + h - 2, y + h - 1, KNOB);
        hit(hits, x - 2, y - 2, x + w + 2, y + h + 2, click);
    }

    /**
     * The rows clicks may land in while a scrolled pane is drawn, so a row scrolled out of view
     * cannot be clicked through the header, the footer or the window above. Render thread only.
     */
    private static int clipTop = Integer.MIN_VALUE, clipBottom = Integer.MAX_VALUE;

    static void clip(int top, int bottom) {
        clipTop = top;
        clipBottom = bottom;
    }

    static void unclip() {
        clipTop = Integer.MIN_VALUE;
        clipBottom = Integer.MAX_VALUE;
    }

    static void hit(List<Hit> hits, int x0, int y0, int x1, int y1, Runnable click) {
        y0 = Math.max(y0, clipTop);
        y1 = Math.min(y1, clipBottom);
        if (y1 <= y0) return;
        Span span = new Span("", FG);
        span.action = click;
        hits.add(new Hit(x0, y0, x1, y1, null, span, -1, null, ACT_NONE));
    }

    // ---- what the card calls back -----------------------------------------------------

    void cardFlip(Friend friend, String field, boolean on) {
        flip(friend, field, on);
    }

    void cardMessage(Friend friend) {
        message(friend.user);
    }

    void cardJoin(Friend friend) {
        join(friend);
    }

    void cardSend(Friend friend) {
        sendHere(friend);
    }

    void cardRemove(Friend friend) {
        remove(friend);
    }
}
