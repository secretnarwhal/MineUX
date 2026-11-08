package com.mineux.core.social;

import com.google.gson.JsonObject;

/**
 * What the rest of the client is allowed to ask the friends service to do.
 * <p>
 * {@link FriendNet} keeps its transport package-private on purpose -- the terminal has no
 * business building a request -- and this is the door through it. Everything except
 * {@link #connect} and {@link #disconnect} is a blocking network call and must be made from a
 * worker thread; the terminal's {@code DiscordCommands#work} is what every caller uses.
 */
public final class FriendTalk {

    private FriendTalk() {}

    /** Opens the stream, if it is not open. Returns at once. */
    public static void connect() {
        FriendConfig config = FriendConfig.get();
        if (!config.enabled) {
            config.enabled = true;
            config.save();
        }
        FriendNet.start();
    }

    /** Closes the stream. Your friends see you go offline. */
    public static void disconnect() {
        FriendNet.stop();
    }

    /** One call. Blocking. Null with the reason on {@link FriendList#callError()}. */
    public static JsonObject post(String path, JsonObject body) {
        return FriendNet.post(path, body);
    }

    /**
     * One call off the calling thread, for a window that cannot block: {@code done} hears null
     * when it worked, else why not -- on the worker.
     */
    public static void postLater(String path, JsonObject body, java.util.function.Consumer<String> done) {
        FriendNet.async(() -> {
            JsonObject answer = FriendNet.post(path, body);
            String why = FriendList.callError();
            done.accept(answer != null ? null : why.isEmpty() ? "failed" : why);
        });
    }

    /** One friend's name in a body, which most of the endpoints take and nothing else. */
    public static JsonObject who(String who) {
        JsonObject body = new JsonObject();
        body.addProperty("who", who);
        return body;
    }

    /** The conversation with one friend, newest last. Blocking. */
    public static JsonObject conversation(String who) {
        return FriendNet.get(FriendOps.DM + "/" + who);
    }

    /**
     * One of your settings for a friend ({@code remoteJoin}, {@code remoteCommand},
     * {@code sharePresence}, {@code muted}, {@code screens}), shown changed at once and sent. Returns at once;
     * {@code done} hears null when the server took it, else why not -- and then the list is
     * fetched again, so the toggle goes back to the truth.
     */
    public static void grant(String who, String field, boolean on, java.util.function.Consumer<String> done) {
        Friend friend = FriendList.find(who);
        if (friend != null) FriendList.patch(friend.with(field, on));
        JsonObject body = who(friend != null ? friend.user : who);
        body.addProperty(field, on);
        FriendNet.async(() -> {
            JsonObject answer = FriendNet.post(FriendOps.GRANTS, body);
            if (answer == null) {
                String why = FriendList.callError();
                JsonObject list = FriendNet.get(FriendOps.LIST);
                if (list != null) FriendList.onFriends(FriendEvents.parse(FriendNet.array(list, "friends")));
                if (done != null) done.accept(why.isEmpty() ? "failed" : why);
            } else if (done != null) {
                done.accept(null);
            }
        });
    }

    /** The whole list, outside the stream. Blocking. Used by the terminal's refresh. */
    public static JsonObject list() {
        return FriendNet.get(FriendOps.LIST);
    }
}
