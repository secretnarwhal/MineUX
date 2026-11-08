package com.mineux.core.social;

import com.mineux.core.DeskExtensions;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What has been said: MineUX's chatroom ({@code #public}, the one on mineux.com), the rooms an
 * extension added from servers of its own ({@link ChatRoomSource}, one tab each, while shown),
 * and a conversation with each friend one has been opened with, as the terminal's {@code irc}
 * shows them a tab each.
 * <p>
 * Only the lines and who is in a room; the talking to the servers is {@link ChatNet}'s, and it
 * writes in here from its own threads, so everything is under one lock and the view reads copies.
 * A channel's {@code version} goes up with every change to it, which is what the view redraws by.
 */
public final class Chat {

    public static final String PUBLIC = "#public";

    /** What kind of line: somebody's, an action ({@code /me}), your own in a conversation, or the client's own word. */
    public static final int MSG = 0, ACTION = 1, MINE = 2, NOTE = 3;

    private static final int KEPT = 300;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    public static final class Line {
        public final long id;
        public final String time;
        public final String nick;
        public final String body;
        public final int kind;

        Line(long id, String time, String nick, String body, int kind) {
            this.id = id;
            this.time = time;
            this.nick = nick;
            this.body = body;
            this.kind = kind;
        }
    }

    public static final class Channel {
        public final String name;
        /** A conversation with a friend, by their account name, rather than a room. */
        public final boolean dm;
        final List<Line> lines = new ArrayList<>();
        final List<String> users = new ArrayList<>();
        int unread;
        int version;
        /** Where an extension's room comes from; null for MineUX's own room and the conversations. */
        public final ChatRoomSource source;
        /** A conversation's history has been asked for. */
        boolean asked;

        Channel(String name, boolean dm, ChatRoomSource source) {
            this.name = name;
            this.dm = dm;
            this.source = source;
        }

        /** What its tab says. */
        public String label() {
            return dm ? "@" + name : name;
        }
    }

    private static final List<Channel> CHANNELS = new ArrayList<>();

    /** The channel on show, and when it last was: lines that come to it are read as they come. */
    private static String watching = "";
    private static long watchedAt;

    private Chat() {}

    // ---- channels ---------------------------------------------------------

    /** MineUX's room first, then each extension's as it was added (a room is made once, never dropped). */
    private static void rooms() {
        if (CHANNELS.isEmpty()) CHANNELS.add(new Channel(PUBLIC, false, null));
        List<ChatRoomSource> sources = DeskExtensions.chatRooms();
        if (sources.size() == extraRooms) return;
        for (ChatRoomSource source : sources) {
            if (roomOf(source) == null) CHANNELS.add(new Channel("#" + DeskExtensions.chatRoomName(source), false, source));
        }
        extraRooms = sources.size();
    }

    private static int extraRooms;

    private static Channel roomOf(ChatRoomSource source) {
        for (Channel c : CHANNELS) if (!c.dm && c.source == source) return c;
        return null;
    }

    /** Whether a channel's tab is there: an extension's room only while its source says so. */
    static boolean shown(Channel channel) {
        ChatRoomSource source = channel.source;
        if (source == null) return true;
        try {
            return source.shown();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Channel conversationOf(String user) {
        rooms();
        for (Channel c : CHANNELS) if (c.dm && c.name.equalsIgnoreCase(user)) return c;
        Channel made = new Channel(user, true, null);
        CHANNELS.add(made);
        return made;
    }

    /** Every tab: the rooms on show first, then the conversations. */
    public static synchronized List<Channel> channels() {
        rooms();
        List<Channel> out = new ArrayList<>();
        for (Channel c : CHANNELS) if (!c.dm && shown(c)) out.add(c);
        for (Channel c : CHANNELS) if (c.dm) out.add(c);
        return out;
    }

    /** A room on show by name ({@code #public}), or null. */
    public static synchronized Channel room(String name) {
        rooms();
        for (Channel c : CHANNELS) if (!c.dm && c.name.equalsIgnoreCase(name) && shown(c)) return c;
        return null;
    }

    /** Whether an extension's room is on show: irc has something to offer without a MineUX sign-in. */
    public static synchronized boolean extraRoomShown() {
        rooms();
        for (Channel c : CHANNELS) if (!c.dm && c.source != null && shown(c)) return true;
        return false;
    }

    /** MineUX's own room by name, for its stream: never an extension's, whatever the name. */
    static synchronized Channel ownRoom(String name) {
        rooms();
        for (Channel c : CHANNELS) if (!c.dm && c.source == null && c.name.equalsIgnoreCase(name)) return c;
        return null;
    }

    /** An extension's room, made if it was not, for its stream. */
    static synchronized Channel extraRoom(ChatRoomSource source) {
        rooms();
        return roomOf(source);
    }

    /** The conversation with a friend, opened if it was not. */
    public static synchronized Channel conversation(String user) {
        return conversationOf(user);
    }

    /**
     * Another MineUX account signed in, or none: the conversations go, MineUX's room is emptied to
     * be read again. An extension's rooms follow their own sign-in ({@link ChatNet}), not this one.
     */
    static synchronized void forget() {
        CHANNELS.removeIf(c -> c.dm);
        for (Channel c : CHANNELS) if (c.source == null) empty(c);
    }

    /** A room's stream closed: what it said and who was there go, to be read again. */
    static synchronized void empty(Channel c) {
        c.lines.clear();
        c.users.clear();
        c.unread = 0;
        c.asked = false;
        c.version++;
    }

    /** Closes a conversation's tab; a room stays. */
    public static synchronized void close(Channel channel) {
        if (channel.dm) CHANNELS.remove(channel);
    }

    // ---- reading ----------------------------------------------------------

    public static synchronized List<Line> lines(Channel channel) {
        return new ArrayList<>(channel.lines);
    }

    public static synchronized List<String> users(Channel channel) {
        return new ArrayList<>(channel.users);
    }

    public static synchronized int version(Channel channel) {
        return channel.version;
    }

    public static synchronized int unread(Channel channel) {
        return channel.unread;
    }

    /** Lines nobody has looked at, in every tab. */
    public static synchronized int unread() {
        int total = 0;
        for (Channel c : CHANNELS) if (shown(c)) total += c.unread;
        return total;
    }

    /** Called by the view each frame for the tab on show: it is read. */
    public static synchronized void watch(Channel channel) {
        watching = channel.label();
        watchedAt = System.currentTimeMillis();
        channel.unread = 0;
    }

    private static boolean watched(Channel channel) {
        return Objects.equals(watching, channel.label()) && System.currentTimeMillis() - watchedAt < 1000L;
    }

    // ---- writing, from the network's threads --------------------------------

    static synchronized void add(Channel channel, long id, String at, String nick, String body, int kind) {
        channel.lines.add(new Line(id, clock(at), nick, body, kind));
        while (channel.lines.size() > KEPT) channel.lines.remove(0);
        if (kind != NOTE && kind != MINE && !watched(channel)) channel.unread++;
        channel.version++;
    }

    /** The client's own word in a channel: a failure, somebody coming or going. */
    public static synchronized void note(Channel channel, String text) {
        channel.lines.add(new Line(0L, clock(""), "", text, NOTE));
        while (channel.lines.size() > KEPT) channel.lines.remove(0);
        channel.version++;
    }

    static synchronized void clear(Channel channel) {
        channel.lines.clear();
        channel.version++;
    }

    static synchronized void remove(Channel channel, long id) {
        channel.lines.removeIf(line -> line.id == id && id != 0L);
        channel.version++;
    }

    static synchronized void users(Channel channel, List<String> users) {
        channel.users.clear();
        channel.users.addAll(users);
        channel.version++;
    }

    static synchronized void user(Channel channel, String nick, boolean here) {
        channel.users.remove(nick);
        if (here) channel.users.add(nick);
        channel.version++;
    }

    /** Marks everything in a channel read: what a history brought is not news. */
    static synchronized void watchQuietly(Channel channel) {
        channel.unread = 0;
        channel.version++;
    }

    static synchronized boolean ask(Channel channel) {
        if (channel.asked) return false;
        channel.asked = true;
        return true;
    }

    /** A message from a friend, off the friends stream; its tab is opened for it. */
    public static void dmIn(String from, String text) {
        Channel channel = conversation(from);
        // Not yet read from the server: the history, when the tab is looked at, has this line in it.
        if (!asked(channel)) {
            bump(channel);
            return;
        }
        add(channel, 0L, "", from, text, MSG);
    }

    private static synchronized boolean asked(Channel channel) {
        return channel.asked;
    }

    private static synchronized void bump(Channel channel) {
        channel.unread++;
        channel.version++;
    }

    /** The server's time as the clock here has it; now, for none. */
    private static String clock(String at) {
        try {
            return CLOCK.format(at == null || at.isEmpty() ? Instant.now() : Instant.parse(at));
        } catch (RuntimeException e) {
            return CLOCK.format(Instant.now());
        }
    }
}
