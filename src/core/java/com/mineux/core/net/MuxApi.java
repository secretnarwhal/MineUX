package com.mineux.core.net;

/**
 * Where the MineUX API lives and who we are on it. Owned by the account/greeter work: the
 * social client, Screens' relay and the greeter all read it, none of them keeps its own copy of the address or the token.
 * <p>
 * The address is, first to last: the system property {@value #BASE_PROPERTY}, the greeter's
 * dotfile ({@code api = } in {@code dotfiles/ly/config.ini}), then {@link #DEFAULT_BASE}.
 * {@code com.mineux.core.account.Account} sets it from those before anything signs in.
 * <p>
 * Signing in and out happens in {@code com.mineux.core.account.Account}, which keeps this in
 * step with the remembered session; everything else only reads.
 */
public final class MuxApi {

    /** The public MineUX API. */
    public static final String DEFAULT_BASE = "https://mineux.com/api";

    /** {@code -Dmineux.api=http://127.0.0.1:8090/api}: wins over the dotfile, for testing. */
    public static final String BASE_PROPERTY = "mineux.api";

    private static volatile String base = DEFAULT_BASE;
    private static volatile String token;
    private static volatile String user;
    private static volatile int generation;

    private MuxApi() {}

    /** {@code https://host/api}, no trailing slash. */
    public static String base() {
        return base;
    }

    /** The same base as a WebSocket address: {@code wss://host/api}. */
    public static String wsBase() {
        String b = base;
        if (b.startsWith("https://")) return "wss://" + b.substring(8);
        if (b.startsWith("http://")) return "ws://" + b.substring(7);
        return b;
    }

    /**
     * The website that goes with the API, for "register at ...": the base without its
     * {@code /api} path, e.g. {@code https://mineux.com}.
     */
    public static String site() {
        String b = base;
        if (b.endsWith("/api")) return b.substring(0, b.length() - 4);
        return b;
    }

    public static void setBase(String value) {
        base = normalise(value);
    }

    /**
     * Picks the address: the system property, else {@code dotfile} (the greeter's {@code api}
     * key, empty or null for none), else {@link #DEFAULT_BASE}.
     *
     * @return the address now in use
     */
    public static String configure(String dotfile) {
        String property = System.getProperty(BASE_PROPERTY);
        if (property != null && !property.isBlank()) setBase(property);
        else if (dotfile != null && !dotfile.isBlank()) setBase(dotfile);
        else setBase(DEFAULT_BASE);
        return base;
    }

    /** No surrounding blanks, no trailing slash. */
    public static String normalise(String value) {
        String v = value.trim();
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    /** The session token, null for a guest. */
    public static String token() {
        return token;
    }

    /** The signed-in MineUX user name, null for a guest. */
    public static String user() {
        return user;
    }

    public static boolean signedIn() {
        return token != null;
    }

    /**
     * Goes up by one on every sign-in and sign-out, so a client polling the API (the friends
     * stream, Screens) can notice that whoever it connected as is gone and reconnect.
     */
    public static int generation() {
        return generation;
    }

    public static void signIn(String name, String sessionToken) {
        user = name;
        token = sessionToken;
        generation++;
    }

    public static void signOut() {
        user = null;
        token = null;
        generation++;
    }
}
