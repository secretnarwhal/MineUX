package com.mineux.core.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The bell: the notifications the server keeps for this account ({@code /v1/notifications}),
 * newest first. A friend's messages (one row per friend while it is unread) and the changelog
 * posts. Reading one greys it; only {@link #clear} takes them away.
 * <p>
 * The list is fetched, never streamed: the friends stream only says that it changed
 * ({@code notify}, with the unread count), and a fetch follows. Without a stream (friends off,
 * not entitled) the bar's bell asks again every minute while the desktop is open.
 * <p>
 * Every field is replaced whole, so the render thread only ever reads a finished list. Every
 * call here returns at once; the network is on a worker.
 */
public final class Notices {

    private Notices() {}

    public static final String KIND_DM = "dm";
    public static final String KIND_CHANGELOG = "changelog";
    /** Somebody asked to be your friend; ref = them. */
    public static final String KIND_REQUEST = "request";
    /** Somebody accepted your request; ref = them. */
    public static final String KIND_FRIEND = "friend";

    private static final String BASE = "/notifications";
    private static final long STALE_MS = 60_000L;

    /** One notification, as the server last described it. */
    public static final class Item {
        public final int id;
        public final String kind;
        public final String title;
        public final String body;
        /** A message's sender; a changelog post's number. */
        public final String ref;
        public final int count;
        /** ISO-8601. */
        public final String at;
        public final boolean read;

        Item(int id, String kind, String title, String body, String ref, int count, String at, boolean read) {
            this.id = id;
            this.kind = kind;
            this.title = title;
            this.body = body;
            this.ref = ref;
            this.count = count;
            this.at = at;
            this.read = read;
        }

        Item asRead() {
            return new Item(id, kind, title, body, ref, count, at, true);
        }

        public boolean dm() {
            return java.util.Objects.equals(KIND_DM, kind);
        }

        public boolean request() {
            return java.util.Objects.equals(KIND_REQUEST, kind);
        }

        public boolean friend() {
            return java.util.Objects.equals(KIND_FRIEND, kind);
        }
    }

    private static volatile List<Item> items = List.of();
    private static volatile int unread;
    private static volatile boolean loaded;
    private static volatile String error = "";
    private static volatile long fetchedAt;
    private static volatile boolean fetching;
    /** A change was announced while a fetch was under way: fetch once more after it. */
    private static volatile boolean again;

    public static List<Item> all() {
        return items;
    }

    public static int unread() {
        return unread;
    }

    public static boolean loaded() {
        return loaded;
    }

    public static String error() {
        return error;
    }

    // ---- what the stream says --------------------------------------------

    /** The stream's count ({@code hello}, {@code notify}): the list behind it is fetched. */
    static void onCount(int count) {
        unread = Math.max(0, count);
        refresh();
    }

    // ---- asking ------------------------------------------------------------

    /** A fetch, unless one is under way. */
    public static synchronized void refresh() {
        if (fetching) {
            again = true;
            return;
        }
        fetching = true;
        FriendNet.async(Notices::fetchLoop);
    }

    /** A fetch, and another for each change announced meanwhile; a few at most. */
    private static void fetchLoop() {
        boolean more = true;
        for (int round = 0; round < 4 && more; round++) {
            fetch();
            synchronized (Notices.class) {
                more = again;
                again = false;
            }
        }
        fetching = false;
    }

    /** A fetch if the list is a minute old: the bar calls this every frame. */
    public static void refreshIfStale() {
        if (System.currentTimeMillis() - fetchedAt > STALE_MS) {
            fetchedAt = System.currentTimeMillis();
            refresh();
        }
    }

    private static void fetch() {
        String[] why = {""};
        JsonObject answer = FriendNet.quiet(BASE, null, why);
        fetchedAt = System.currentTimeMillis();
        if (answer == null) {
            error = why[0];
            return;
        }
        JsonArray raw = FriendNet.array(answer, "notifications");
        List<Item> list = new ArrayList<>(raw.size());
        for (JsonElement element : raw) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            list.add(new Item(
                FriendNet.integer(row, "id"),
                FriendNet.string(row, "kind"),
                FriendNet.string(row, "title"),
                FriendNet.string(row, "body"),
                FriendNet.string(row, "ref"),
                Math.max(1, FriendNet.integer(row, "count")),
                FriendNet.string(row, "at"),
                FriendNet.bool(row, "read")));
        }
        items = List.copyOf(list);
        unread = FriendNet.integer(answer, "unread");
        loaded = true;
        error = "";
    }

    /** Another account signed in, or none: the last one's bell is emptied and read again. */
    static void forget() {
        items = List.of();
        unread = 0;
        loaded = false;
        error = "";
        fetchedAt = 0L;
    }

    /** Greys one at once, then tells the server. */
    public static void read(int id) {
        List<Item> now = items;
        List<Item> next = new ArrayList<>(now.size());
        boolean changed = false;
        for (Item item : now) {
            if (item.id == id && !item.read) {
                next.add(item.asRead());
                changed = true;
            } else {
                next.add(item);
            }
        }
        if (!changed) return;
        items = List.copyOf(next);
        unread = Math.max(0, unread - 1);
        JsonObject body = new JsonObject();
        body.addProperty("id", id);
        send(BASE + "/read", body);
    }

    public static void readAll() {
        List<Item> next = new ArrayList<>();
        for (Item item : items) next.add(item.read ? item : item.asRead());
        items = List.copyOf(next);
        unread = 0;
        JsonObject body = new JsonObject();
        body.addProperty("all", true);
        send(BASE + "/read", body);
    }

    public static void clear() {
        items = List.of();
        unread = 0;
        send(BASE + "/clear", new JsonObject());
    }

    /** Every unread message row from one friend, read: their conversation was opened. */
    public static void readFrom(String user) {
        for (Item item : items) {
            if (!item.read && item.dm() && item.ref.equalsIgnoreCase(user)) read(item.id);
        }
    }

    /**
     * An admin's changelog post, into everyone's bell. {@code done} hears how it went, on the
     * worker: "posted to N accounts", or why not.
     */
    public static void post(String title, String body, java.util.function.Consumer<String> done) {
        JsonObject json = new JsonObject();
        json.addProperty("title", title);
        json.addProperty("body", body);
        FriendNet.async(() -> {
            String[] why = {""};
            JsonObject answer = FriendNet.quiet(BASE + "/changelog", json, why);
            done.accept(answer == null ? why[0] : "posted to " + FriendNet.integer(answer, "sent") + " accounts");
            refresh();
        });
    }

    private static void send(String path, JsonObject body) {
        FriendNet.async(() -> {
            String[] why = {""};
            if (FriendNet.quiet(path, body, why) == null) error = why[0];
            refresh();
        });
    }
}
