package com.mineux.core.ui.term;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything {@code lookup} has found out about one player, filled in by {@link ProfileLoader}'s
 * workers a source at a time and read by the window ({@link LookupPane}) every frame. Every
 * field is replaced whole, never changed in place, so the client thread only ever sees a
 * finished value.
 * <p>
 * A source's {@code Note} is what to show in its place -- that it is loading, that the player
 * is not on it, why it failed -- and null once its fields are the answer.
 * <p>
 * Public, read-only, for an extension that shows a player's standing ({@link #of}, the fields
 * marked public): never write to it.
 */
public final class Profile {

    /** Strings a kit takes in {@link #kits}: name, tier, rank, wins, losses, then its factors. */
    public static final int KIT_STRIDE = McPvpKits.STRIDE;
    /** Strings a ladder takes in {@link #ladders}. */
    public static final int LADDER_STRIDE = 5;

    /** How long a lookup is reused for before the same name is fetched again. */
    private static final long FRESH_MS = 120_000L;

    private static final Map<String, Profile> ALL = new ConcurrentHashMap<>();

    /** The player as typed, until Mojang gives the name its own capitals. */
    final String asked;
    final long startedAt = System.currentTimeMillis();

    // ---- mojang ---------------------------------------------------------
    volatile String name;
    /** Dashed. */
    volatile String uuid;
    public volatile String note = "looking up...";
    /** Mojang said there is nobody by this name, or could not be asked: nothing else is. */
    public volatile boolean failed;

    // ---- discord (subtiers, mctiers) ------------------------------------
    volatile String discord;
    volatile String discordNote = "...";

    // ---- mcpvp.com ------------------------------------------------------
    public volatile String mcpvpNote = "loading...";
    volatile List<String> names = List.of();
    public volatile List<String> tiers = List.of();
    /** The kits they have played, most played first, {@link McPvpKits#STRIDE} strings each. */
    public volatile List<String> kits = List.of();
    /** Key, value, key, value. */
    volatile List<String> stats = List.of();
    volatile String online = "";
    volatile String ban = "unknown";
    /** Key, value, key, value: Client, Game Version, Platform. */
    volatile List<String> client = List.of();
    volatile List<String> mods = List.of();
    volatile List<String> activityTop = List.of();
    volatile List<String> activityRecent = List.of();
    /** Null until fetched; the count is the site's, which may be more than the names loaded. */
    volatile List<String> friends;
    volatile int friendsTotal;
    volatile List<String> following;
    volatile int followingTotal;
    /** Date, kit, map, score, result, opponent: six strings a match. Null until fetched. */
    public volatile List<String> matches;
    /** The overall ranking over time, oldest first; lower is better. */
    public volatile float[] rankHistory = new float[0];

    // ---- minemen.club ---------------------------------------------------
    public volatile String minemenNote = "loading...";
    volatile String mmStatus = "";
    volatile long mmSeen;
    volatile String mmRank = "";
    volatile String mmJoined = "";
    volatile String mmViews = "";
    public volatile String mmGlobal = "";
    public volatile String mmWorld = "";
    /** Name, ELO, ranked wins, ranked losses, casual wins: five strings a ladder. */
    public volatile List<String> ladders = List.of();
    volatile List<String> mmFriends = List.of();
    public volatile float[] eloHistory = new float[0];

    private Profile(String asked) {
        this.asked = asked;
    }

    /** The lookup of {@code player}: the one already running or just finished, or a new one. */
    public static Profile of(String player) {
        String key = player.toLowerCase(Locale.ROOT);
        Profile known = ALL.get(key);
        if (known != null && System.currentTimeMillis() - known.startedAt < FRESH_MS) return known;
        Profile fresh = new Profile(player);
        ALL.put(key, fresh);
        ProfileLoader.start(fresh);
        return fresh;
    }

    /** Whether {@code player} could be a Minecraft name at all, so worth looking up. */
    public static boolean validName(String player) {
        return DiscordCommands.validName(player);
    }

    /** A lookup nothing is fetching, as one is before any source has answered: for the self-test. */
    static Profile blank(String player) {
        return new Profile(player);
    }

    /** What to call them: Mojang's spelling once it is known. */
    String shown() {
        String known = name;
        return known == null ? asked : known;
    }
}
