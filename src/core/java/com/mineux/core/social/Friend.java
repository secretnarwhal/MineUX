package com.mineux.core.social;

/**
 * One friend, as the server last described them.
 * <p>
 * Two sets of permissions and the difference is the whole security model. {@link #allowJoin} and
 * {@link #allowCommand} are what <em>you</em> have allowed <em>them</em> to do to your client;
 * they are written by you and nobody else, and they are what your client checks before obeying
 * anything. {@link #theyAllowJoin} and {@link #theyAllowCommand} are the mirror -- what they have
 * allowed you -- and exist only so the interface can grey out a button that would be refused.
 * <b>Nothing may ever decide whether to obey an instruction by reading the "they allow" pair.</b>
 * Those two are a hint from the server about somebody else's settings; the real check happens on
 * the server against the receiver's own row, and again here against {@link #allowJoin} and
 * {@link #allowCommand}.
 * <p>
 * Instances are replaced wholesale when a {@code friends} frame arrives, never mutated, so a
 * reader that grabbed the list never sees a half-updated friend.
 */
public final class Friend {

    /** The account's number, shown on their profile. Stable and unique. */
    public final int uid;

    /** The MineUX account name. Authenticated and unique. */
    public final String user;

    /** The name you have given them, or empty for none. */
    public final String nickname;

    /** When you became friends, ISO-8601, for the profile. */
    public final String since;

    /** Online and sharing presence with you. A friend who muted you reads as offline. */
    public final boolean online;

    /** The server they are on, or empty. */
    public final String server;

    /** What they are playing, as their own client worked it out. Display only. */
    public final String gamemode;

    /** Their Minecraft name, or empty when they are not in a world. */
    public final String mc;

    /** That account's UUID while they are in a world, or empty. */
    public final String uuid;

    /**
     * The Minecraft account they last played on, and when (ISO-8601): for their skin and "last
     * seen as". Empty unless they share presence with you.
     */
    public final String lastMc;
    public final String lastUuid;
    public final String lastAt;

    // ---- what I have allowed them ---------------------------------------

    public final boolean allowJoin;
    public final boolean allowCommand;
    public final boolean sharing;
    public final boolean muted;

    /**
     * Screens with them: you let them see your shared screens and want theirs. Yours alone to
     * set; it only does anything while {@link #theyScreens} is on too (the relay checks both).
     */
    public final boolean screens;

    // ---- what they have allowed me (a hint for the interface only) -------

    public final boolean theyAllowJoin;
    public final boolean theyAllowCommand;

    /** Whether they have screens on for you. Shown only: it is theirs to set. */
    public final boolean theyScreens;

    Friend(int uid, String user, String nickname, String since, boolean online, String server,
           String gamemode, String mc, String uuid, String lastMc, String lastUuid, String lastAt,
           boolean allowJoin, boolean allowCommand, boolean sharing,
           boolean muted, boolean screens, boolean theyAllowJoin, boolean theyAllowCommand, boolean theyScreens) {
        this.uid = uid;
        this.user = user;
        this.nickname = nickname;
        this.since = since;
        this.online = online;
        this.server = server;
        this.gamemode = gamemode;
        this.mc = mc;
        this.uuid = uuid;
        this.lastMc = lastMc;
        this.lastUuid = lastUuid;
        this.lastAt = lastAt;
        this.allowJoin = allowJoin;
        this.allowCommand = allowCommand;
        this.sharing = sharing;
        this.muted = muted;
        this.screens = screens;
        this.theyAllowJoin = theyAllowJoin;
        this.theyAllowCommand = theyAllowCommand;
        this.theyScreens = theyScreens;
    }

    /**
     * This friend with one of your settings for them changed, before the server has said so: what
     * a toggle shows the moment it is clicked. The field names are the service's.
     */
    Friend with(String field, boolean on) {
        boolean join = java.util.Objects.equals("remoteJoin", field) ? on : allowJoin;
        boolean command = java.util.Objects.equals("remoteCommand", field) ? on : allowCommand;
        boolean share = java.util.Objects.equals("sharePresence", field) ? on : sharing;
        boolean mute = java.util.Objects.equals("muted", field) ? on : muted;
        boolean screen = java.util.Objects.equals("screens", field) ? on : screens;
        return new Friend(uid, user, nickname, since, online, server, gamemode, mc, uuid, lastMc, lastUuid,
            lastAt, join, command, share, mute, screen, theyAllowJoin, theyAllowCommand, theyScreens);
    }

    /** The Minecraft name to show and draw: the one they are on, else the one they were last on. */
    public String skinName() {
        return mc.isEmpty() ? lastMc : mc;
    }

    /** The UUID for their skin: the account they are on, else the one they were last on. */
    public String skinUuid() {
        return mc.isEmpty() ? lastUuid : uuid;
    }

    /** What to call them: the name you gave them, else their account name. */
    public String display() {
        return nickname.isEmpty() ? user : nickname;
    }

    /** Whether they are somewhere you could follow them to. */
    public boolean somewhere() {
        return online && !server.isEmpty();
    }

    /** "2b2t.org - anarchy", or just the server, or empty. For one line of a banner. */
    public String where() {
        if (server.isEmpty()) return "";
        return gamemode.isEmpty() ? server : server + " - " + gamemode;
    }
}
