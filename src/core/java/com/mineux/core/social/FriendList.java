package com.mineux.core.social;

import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Who your friends are and where they are, right now. The one place anything else asks.
 * <p>
 * Everything here is written by {@link FriendNet}'s stream thread and read by the tick thread,
 * the render thread and the terminal. The list is swapped as a whole immutable list rather than
 * mutated, and every scalar is volatile, so a reader never sees half an update and nothing needs
 * a lock on the path that matters -- {@link #isFriend}, which runs once per candidate entity per
 * pass of any module that skips friends.
 */
public final class FriendList {

    private FriendList() {}

    private static final int LOG_LINES = 160;

    private static volatile boolean connected;
    private static volatile String error = "";

    /** Why the last one-off call failed. Never the stream's state: a refused call is not a drop. */
    private static volatile String callError = "";

    private static volatile String selfUser = "";
    private static volatile int selfUid;
    private static volatile int unread;

    /** Replaced wholesale, never edited. */
    private static volatile List<Friend> friends = List.of();

    /** Who is waiting for you to accept them. */
    private static volatile List<String> requests = List.of();

    /** Who you have asked and are waiting on. */
    private static volatile List<String> sent = List.of();

    /**
     * Lower-cased Minecraft names of online friends who are on the same server as you, flat.
     * Derived once per list change so the hot check is a short array walk with no allocation.
     * A flat array rather than a Set: the obfuscator is happiest with plain arrays, the list is
     * never more than a couple of hundred long, and a walk beats hashing a name.
     */
    private static volatile String[] nearbyNames = new String[0];

    /** {@code FriendConfig.friendly}, copied here once a tick. See {@link #isFriend}. */
    private static volatile boolean friendly = true;

    /** The address this client is on, as the tick last saw it. Used to derive {@link #nearbyNames}. */
    private static volatile String here = "";

    private static final Deque<String> LOG = new ArrayDeque<>();
    private static volatile int logVersion;

    // ---- reads -----------------------------------------------------------

    public static boolean connected() {
        return connected;
    }

    public static String error() {
        return error;
    }

    /** Why the last one-off call ({@link FriendTalk#post} and friends) failed. */
    public static String callError() {
        return callError;
    }

    static void onCallFailed(String why) {
        callError = why == null ? "" : why;
    }

    public static String selfUser() {
        return selfUser;
    }

    public static int selfUid() {
        return selfUid;
    }

    public static int unread() {
        return unread;
    }

    public static List<Friend> all() {
        return friends;
    }

    public static List<String> requests() {
        return requests;
    }

    public static List<String> sent() {
        return sent;
    }

    /** Whether this account is still waiting for you to answer them. */
    public static boolean asked(String who) {
        for (String each : requests) if (each.equalsIgnoreCase(who)) return true;
        return false;
    }

    public static int onlineCount() {
        int count = 0;
        for (Friend friend : friends) if (friend.online) count++;
        return count;
    }

    /** A friend by account name, nickname or uid (written {@code #12} or {@code 12}), or null. */
    public static Friend find(String who) {
        if (who == null || who.isEmpty()) return null;
        String asked = who.startsWith("#") ? who.substring(1) : who;

        for (Friend friend : friends) {
            if (friend.user.equalsIgnoreCase(asked)) return friend;
            if (!friend.nickname.isEmpty() && friend.nickname.equalsIgnoreCase(asked)) return friend;
            if (!friend.mc.isEmpty() && friend.mc.equalsIgnoreCase(asked)) return friend;
        }
        int uid = uid(asked);
        if (uid > 0) {
            for (Friend friend : friends) if (friend.uid == uid) return friend;
        }
        return null;
    }

    /**
     * {@code asked} as a uid, or -1.
     * <p>
     * Its own method, with nothing in the {@code try} but the parse, because the obfuscator's
     * control-flow pass rewrites a {@code try} that contains a loop with a {@code return} in it
     * into bytecode with an illegal exception table -- a self-test of the obfuscated build catches it,
     * but only after a build. The rule: keep the guarded region to the
     * call itself and use its result outside.
     */
    private static int uid(String asked) {
        try {
            return Integer.parseInt(asked);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static synchronized List<String> log() {
        return new ArrayList<>(LOG);
    }

    public static int logVersion() {
        return logVersion;
    }

    /**
     * Whether this entity is a friend standing on the same server as you.
     * <p>
     * {@code Friends#isFriend} ors this in, so every combat module in the client stops targeting
     * the people on your friends list without anybody maintaining a second list by hand.
     * <p>
     * Deliberately cheap and deliberately total: off, no friends, or nobody on this server all
     * answer false on a volatile read and an array-length check, because this is called for every
     * candidate entity on every targeting pass.
     * <p>
     * The match is by Minecraft name, which the friend's own client reported. That is a thing a
     * hostile peer could lie about -- but they would have to be on your friends list to say it at
     * all, and the worst they can buy is being left alone by your aura. Trusting it anywhere else
     * would not be acceptable; here it is the whole point.
     */
    public static boolean isFriend(LivingEntity entity) {
        String[] names = nearbyNames;
        if (names.length == 0 || entity == null || !friendly) return false;

        String who = entity.getName().getString().toLowerCase(Locale.ROOT);
        for (String each : names) {
            if (each.equals(who)) return true;
        }
        return false;
    }

    // ---- writes, from the stream and the tick -----------------------------

    static void setFriendly(boolean value) {
        friendly = value;
    }

    /** The tick tells us where this client is, so "on the same server" can be derived. */
    static void setHere(String address) {
        if (!here.equals(address)) {
            here = address;
            deriveNearby();
        }
    }

    static void onConnected(String user, int uid, int unreadCount) {
        selfUser = user;
        selfUid = uid;
        unread = unreadCount;
        connected = true;
        error = "";
    }

    /** Another account signed in, or none: nothing of the last one's list is kept. */
    static synchronized void forget() {
        connected = false;
        error = "";
        callError = "";
        selfUser = "";
        selfUid = 0;
        unread = 0;
        friends = List.of();
        requests = List.of();
        sent = List.of();
        nearbyNames = new String[0];
        LOG.clear();
        logVersion++;
    }

    static void onDisconnected(String why) {
        connected = false;
        error = why == null ? "" : why;
        // The list is kept, not cleared: it is the last thing known to be true and showing it
        // greyed out is more use than showing nothing while a reconnect backs off.
    }

    static void onFriends(List<Friend> list) {
        friends = List.copyOf(list);
        deriveNearby();
    }

    /** One friend swapped for a changed copy ({@link Friend#with}), until the server's next list. */
    static synchronized void patch(Friend changed) {
        List<Friend> next = new java.util.ArrayList<>(friends.size());
        for (Friend each : friends) next.add(each.user.equalsIgnoreCase(changed.user) ? changed : each);
        friends = List.copyOf(next);
    }

    static void onRequests(List<String> list, List<String> mine) {
        requests = List.copyOf(list);
        sent = List.copyOf(mine);
    }

    static void addUnread(int delta) {
        unread = Math.max(0, unread + delta);
    }

    static void clearUnread() {
        unread = 0;
    }

    private static void deriveNearby() {
        String address = here;
        if (address.isEmpty()) {
            nearbyNames = new String[0];
            return;
        }

        String normalised = ServerName.normalise(address);
        List<String> near = new ArrayList<>(4);
        for (Friend friend : friends) {
            if (!friend.online || friend.mc.isEmpty() || friend.server.isEmpty()) continue;
            if (!ServerName.normalise(friend.server).equals(normalised)) continue;
            near.add(friend.mc.toLowerCase(Locale.ROOT));
        }
        nearbyNames = near.toArray(new String[0]);
    }

    static synchronized void note(String line) {
        LOG.addLast(line);
        while (LOG.size() > LOG_LINES) LOG.removeFirst();
        logVersion++;
    }
}
