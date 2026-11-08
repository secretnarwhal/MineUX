package com.mineux.core.ui.term;

import com.mineux.core.social.Chat;
import com.mineux.core.social.ChatNet;
import com.mineux.core.social.Friend;
import com.mineux.core.social.FriendList;
import com.mineux.core.ui.term.Term.Hit;
import com.mineux.core.ui.term.Term.Span;
import com.mineux.core.util.Words;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.mineux.core.ui.term.Term.*;

/**
 * irc: the site's chatrooms and the conversations with friends, as a program that takes over the
 * terminal it was typed in -- as an extension's {@code TermProgram} does -- until Ctrl+C.
 * <p>
 * A tab a channel along the top: {@code #public}, the room on mineux.com, then any room an
 * extension added from a server of its own ({@code DeskExtensions.addChatRoom}, while it is
 * shown), then one for each friend a conversation is open with ({@code dm <friend>}), each with a
 * count of what came while it was not on show. Under them what was said, who is in the
 * room down the right side where there is room, and a line to type on.
 * <p>
 * Keys: Enter says the line, Tab and Shift+Tab change tab, PageUp, PageDown and the wheel scroll.
 * And typed on the line: {@code /me} for an action, {@code /dm <friend>} to open a conversation
 * (with a message after the name, to say it there), {@code /close} to shut the one on show,
 * {@code /quit}.
 */
final class ChatView {

    private static final int HEADER_H = 11, TAB_H = 13, ROW_H = 10, PAD = 5, USERS_W = 84, MAX_LINE = 400;

    private static final Set<String> W_ME = Words.of("/me");
    private static final Set<String> W_DM = Words.of("/dm /msg /query /w");
    private static final Set<String> W_CLOSE = Words.of("/close /part");
    private static final Set<String> W_QUIT = Words.of("/quit /exit /q");

    /** One row on screen: the first of a line has its time and who said it, the rest only the words. */
    private static final class Row {
        String time = "";
        String nick = "";
        int nickColor;
        String text = "";
        int color;
    }

    private Chat.Channel channel;
    private final StringBuilder input = new StringBuilder();
    private int scroll;
    private boolean done;

    private List<Row> rows = new ArrayList<>();
    private Chat.Channel rowsOf;
    private int rowsVersion = -1, rowsWidth = -1;

    ChatView(String target) {
        ChatNet.connect();
        show(target);
    }

    /** Brings a tab to the front: a room by its name, a friend's conversation as {@code @name}; anything else, the first room. */
    void show(String target) {
        Chat.Channel found = null;
        if (target != null && target.startsWith("@") && target.length() > 1) {
            found = Chat.conversation(target.substring(1));
        } else if (target != null && !target.isEmpty()) {
            found = Chat.room(target.startsWith("#") ? target : "#" + target);
        }
        open(found != null ? found : fallback());
    }

    /** The first room; for a guest, the first extension room there is, which needs no MineUX sign-in. */
    private static Chat.Channel fallback() {
        List<Chat.Channel> all = Chat.channels();
        if (Guest.is()) {
            for (Chat.Channel each : all) if (each.source != null) return each;
        }
        return all.isEmpty() ? Chat.room(Chat.PUBLIC) : all.get(0);
    }

    private void open(Chat.Channel which) {
        channel = which;
        scroll = 0;
        ChatNet.load(which);
    }

    /** {@code /quit}: the terminal takes its prompt back. */
    boolean done() {
        return done;
    }

    // ---- input ----------------------------------------------------------

    void charTyped(CharacterEvent event) {
        if (event.isAllowedChatCharacter() && input.length() < MAX_LINE) input.append(event.codepointAsString());
    }

    void keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            String line = input.toString().trim();
            input.setLength(0);
            if (!line.isEmpty()) entered(line);
        } else if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (event.hasControlDown()) input.setLength(0);
            else if (input.length() > 0) input.setLength(input.length() - 1);
        } else if (key == GLFW.GLFW_KEY_V && event.hasControlDown()) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard().replaceAll("[\\r\\n\\t]+", " ");
            input.append(clip, 0, Math.min(clip.length(), Math.max(0, MAX_LINE - input.length())));
        } else if (key == GLFW.GLFW_KEY_TAB) {
            step(event.hasShiftDown() ? 0 - 1 : 1);
        } else if (key == GLFW.GLFW_KEY_PAGE_UP) {
            scroll += 8;
        } else if (key == GLFW.GLFW_KEY_PAGE_DOWN) {
            scroll = Math.max(0, scroll - 8);
        } else if (key == GLFW.GLFW_KEY_END) {
            scroll = 0;
        }
    }

    void scrolled(double amount) {
        scroll = Math.max(0, scroll + (int) Math.signum(amount) * 3);
    }

    private void step(int by) {
        List<Chat.Channel> all = Chat.channels();
        int at = all.indexOf(channel);
        open(all.get(Math.floorMod(at + by, all.size())));
    }

    private void entered(String line) {
        if (!line.startsWith("/")) {
            ChatNet.say(channel, line, false);
            scroll = 0;
            return;
        }
        int space = line.indexOf(' ');
        String word = (space < 0 ? line : line.substring(0, space)).toLowerCase(Locale.ROOT);
        String rest = space < 0 ? "" : line.substring(space + 1).trim();

        if (W_ME.contains(word)) {
            if (!rest.isEmpty()) ChatNet.say(channel, rest, true);
        } else if (W_DM.contains(word)) {
            dm(rest);
        } else if (W_CLOSE.contains(word)) {
            if (channel.dm) {
                Chat.Channel closing = channel;
                step(0 - 1);
                Chat.close(closing);
            } else {
                Chat.note(channel, "a room stays open; /quit leaves irc");
            }
        } else if (W_QUIT.contains(word)) {
            done = true;
        } else {
            Chat.note(channel, "no command " + word + " - /me, /dm <friend> [message], /close, /quit");
        }
    }

    private void dm(String rest) {
        int space = rest.indexOf(' ');
        String who = space < 0 ? rest : rest.substring(0, space);
        String text = space < 0 ? "" : rest.substring(space + 1).trim();
        Friend friend = who.isEmpty() ? null : FriendList.find(who);
        if (friend == null) {
            Chat.note(channel, who.isEmpty() ? "usage: /dm <friend> [message]" : "nobody on your friends list called " + who);
            return;
        }
        open(Chat.conversation(friend.user));
        if (!text.isEmpty()) ChatNet.say(channel, text, false);
    }

    // ---- drawing --------------------------------------------------------

    /** Draws itself into the terminal's content box. */
    void render(GuiGraphicsExtractor g, Font font, long now, int x, int y, int w, int h, int mouseX, int mouseY, List<Hit> hits) {
        if (Guest.is() && !Chat.extraRoomShown()) {
            Guest.draw(g, font, x, y, w, h);
            return;
        }
        // A conversation closed from under it, by another terminal's irc, or a room hidden.
        if (!Chat.channels().contains(channel)) open(fallback());
        Chat.watch(channel);

        g.fill(x, y, x + w, y + h, 0x60050608);
        g.fill(x, y, x + w, y + HEADER_H, 0x30000000);
        String keys = "tab next  /dm <friend>  /me  /close";
        int keysW = font.width(keys);
        g.text(font, font.plainSubstrByWidth("irc - " + ChatNet.site(channel) + " - " + ChatNet.state(channel), Math.max(0, w - keysW - PAD * 3)), x + PAD, y + 2, GOLD, false);
        if (keysW + PAD * 2 < w) g.text(font, keys, x + w - PAD - keysW, y + 2, FAINT, false);

        int ty = y + HEADER_H + 1;
        int tx = x + PAD;
        for (Chat.Channel each : Chat.channels()) {
            int unread = Chat.unread(each);
            boolean on = each == channel;
            String label = each.label() + (unread > 0 ? " (" + unread + ")" : "");
            tx = tab(g, font, label, tx, ty, on ? GOLD : unread > 0 ? CYAN : DIM, on ? 0x40EFC648 : 0x16FFFFFF, mouseX, mouseY, hits, each) + 3;
        }

        int top = ty + TAB_H + 2;
        int inputY = y + h - ROW_H - 3;
        boolean people = !channel.dm && w > 3 * USERS_W;
        int textW = w - 2 * PAD - (people ? USERS_W : 0);
        if (people) users(g, font, x + w - USERS_W, top, inputY - 2);

        lay(font, textW);
        int visible = Math.max(1, (inputY - 2 - top) / ROW_H);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visible)));
        int first = Math.max(0, rows.size() - visible - scroll);
        int ry = top;
        for (int i = first; i < rows.size() && i < first + visible; i++) {
            Row row = rows.get(i);
            int rx = x + PAD;
            if (!row.time.isEmpty()) {
                g.text(font, row.time, rx, ry, FAINT, false);
                rx += font.width(row.time) + 4;
            } else {
                rx += font.width("00:00") + 4;
            }
            if (!row.nick.isEmpty()) {
                g.text(font, row.nick, rx, ry, row.nickColor, false);
                rx += font.width(row.nick) + 4;
            }
            g.text(font, row.text, rx, ry, row.color, false);
            ry += ROW_H;
        }
        if (rows.isEmpty()) {
            String empty = channel.dm ? "nothing said with " + channel.name + " yet"
                : channel.source == null && Guest.is() ? Guest.NOTE : "nothing said here yet";
            g.text(font, empty, x + PAD, top, FAINT, false);
        }

        g.fill(x, inputY - 2, x + w, inputY - 1, 0x1CFFFFFF);
        String prompt = channel.label() + " > ";
        g.text(font, prompt, x + PAD, inputY, GOLD, false);
        int room = w - 2 * PAD - font.width(prompt) - 6;
        String typed = input.toString();
        // The end of a long line is what is being typed.
        String shown = font.width(typed) <= room ? typed : font.plainSubstrByWidth(new StringBuilder(typed).reverse().toString(), room);
        if (font.width(typed) > room) shown = new StringBuilder(shown).reverse().toString();
        int ix = x + PAD + font.width(prompt);
        g.text(font, shown, ix, inputY, FG, false);
        if (now / 500L % 2L == 0L) g.fill(ix + font.width(shown) + 1, inputY - 1, ix + font.width(shown) + 2, inputY + 8, FG);
    }

    private int tab(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, int fill, int mouseX, int mouseY,
                    List<Hit> hits, Chat.Channel to) {
        int w = font.width(text) + 8;
        boolean hot = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + TAB_H - 1;
        g.fill(x, y, x + w, y + TAB_H - 1, hot ? 0x50EFC648 : fill);
        g.text(font, text, x + 4, y + 2, hot ? 0xFFFFFFFF : color, false);
        Span span = new Span(text, color);
        span.hint = to.dm ? "your conversation with " + to.name : "the " + to.name + " room on " + ChatNet.site(to);
        span.action = () -> open(to);
        hits.add(new Hit(x, y, x + w, y + TAB_H - 1, null, span, -1, null, ACT_NONE));
        return x + w;
    }

    private void users(GuiGraphicsExtractor g, Font font, int x, int top, int bottom) {
        g.fill(x - 3, top, x - 2, bottom, 0x1CFFFFFF);
        List<String> users = Chat.users(channel);
        g.text(font, users.size() + " here", x, top, DIM, false);
        int uy = top + ROW_H + 1;
        for (String user : users) {
            if (uy + ROW_H > bottom) break;
            g.text(font, font.plainSubstrByWidth(user, USERS_W - PAD), x, uy, user.startsWith("guest-") ? FAINT : tint(user), false);
            uy += ROW_H;
        }
    }

    // ---- laying the lines out ---------------------------------------------

    /** The channel's lines as rows no wider than the text column; made again only when something changed. */
    private void lay(Font font, int width) {
        int version = Chat.version(channel);
        if (rowsOf == channel && rowsVersion == version && rowsWidth == width) return;
        rowsOf = channel;
        rowsVersion = version;
        rowsWidth = width;

        List<Row> out = new ArrayList<>();
        String me = ChatNet.nick(channel);
        for (Chat.Line line : Chat.lines(channel)) {
            Row row = new Row();
            row.time = line.time;
            String text = line.body;
            if (line.kind == Chat.NOTE) {
                row.color = DIM;
                text = "* " + text;
            } else if (line.kind == Chat.ACTION) {
                row.color = tint(line.nick);
                text = "* " + line.nick + " " + text;
            } else {
                boolean mine = line.kind == Chat.MINE || line.nick.equalsIgnoreCase(me);
                row.nick = "<" + (line.nick.isEmpty() ? "you" : line.nick) + ">";
                row.nickColor = mine ? GOLD : tint(line.nick);
                row.color = FG;
            }
            int room = Math.max(40, width - font.width("00:00") - 4 - (row.nick.isEmpty() ? 0 : font.width(row.nick) + 4));
            // A line is four hundred characters at most: a dozen rows is every one of them.
            for (int part = 0; part < 12 && !text.isEmpty(); part++) {
                String piece = font.plainSubstrByWidth(text, room);
                if (piece.isEmpty()) break;
                int cut = piece.length();
                if (cut < text.length()) {
                    int space = piece.lastIndexOf(' ');
                    if (space > cut / 2) cut = space + 1;
                }
                Row next = part == 0 ? row : new Row();
                next.color = row.color;
                next.text = text.substring(0, cut);
                out.add(next);
                text = text.substring(cut);
            }
        }
        rows = out;
    }

    /** A colour a name keeps: the same nick is always the same one of a few. */
    private static int tint(String nick) {
        int[] inks = {CYAN, GREEN, 0xFFFF6AC1, AMBER, 0xFFB48EFF, 0xFF7FE0D0};
        return inks[Math.floorMod(nick.toLowerCase(Locale.ROOT).hashCode(), inks.length)];
    }
}
