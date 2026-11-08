package com.mineux.core.social;

/**
 * The wire vocabulary of the friends service: the paths this client calls and the names of the
 * events the stream sends back.
 * <p>
 * String constants rather than an enum, and in one place rather than inline at each use. The
 * obfuscator a host may run renames enum constants and every enum's {@code values()}, so an enum
 * that reaches a config file or a {@code valueOf} is a crash waiting for the next build -- see
 * the precedent in {@code TrailConfig.blendMode}.
 * <p>
 * The service is {@code /api/v1/friends} on the same origin as the auth API, served by the
 * MineUX server. Everything this client says is an
 * ordinary POST; everything it is told arrives on one long-lived server-sent-events stream.
 * SSE rather than a websocket because it is plain HTTP and rides the existing {@code /api/}
 * nginx block with no change to it.
 */
public final class FriendOps {

    private FriendOps() {}

    /** Appended to {@code AuthConfig.apiBase}. */
    public static final String BASE = "/friends";

    // ---- what the client asks for --------------------------------------

    public static final String STREAM = "/stream";
    public static final String LIST = "/list";
    public static final String PRESENCE = "/presence";
    public static final String REQUEST = "/request";
    public static final String ACCEPT = "/accept";
    public static final String DECLINE = "/decline";
    public static final String REMOVE = "/remove";
    public static final String GRANTS = "/grants";
    public static final String DM = "/dm";
    public static final String REMOTE_JOIN = "/remote/join";
    public static final String REMOTE_COMMAND = "/remote/command";

    // ---- what the stream says ------------------------------------------

    /** First frame on a fresh stream: who the server thinks you are, and your uid. */
    public static final String EV_HELLO = "hello";

    /** The whole friends list, every time any of it changes. No delta frame, on purpose. */
    public static final String EV_FRIENDS = "friends";

    /** Everyone waiting for you to accept them. */
    public static final String EV_REQUESTS = "requests";

    /** Somebody has just asked to be your friend. */
    public static final String EV_REQUEST = "request";

    /** A direct message. */
    public static final String EV_DM = "dm";

    /** A friend is driving your client, with permission you gave them. See {@code Remote}. */
    public static final String EV_REMOTE = "remote";

    /** The bell changed: {@code {unread}}. The list itself is fetched ({@link Notices}). */
    public static final String EV_NOTIFY = "notify";

    /** The stream is over: signed out, or signed in somewhere else. */
    public static final String EV_BYE = "bye";

    // ---- the kinds of remote instruction --------------------------------

    public static final String REMOTE_KIND_JOIN = "join";
    public static final String REMOTE_KIND_COMMAND = "command";
}
