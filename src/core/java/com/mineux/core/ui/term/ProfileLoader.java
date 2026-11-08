package com.mineux.core.ui.term;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Fills a {@link Profile}: Mojang first, for the UUID and the name's own spelling, then a
 * worker a source -- mcpvp.com, minemen.club, the tier lists' Discord link -- so a slow or
 * broken one holds nothing else up. A source that fails says why in its own note.
 */
final class ProfileLoader {

    private static final String MOJANG = "https://api.mojang.com/users/profiles/minecraft/";
    static final String MCPVP = "https://www.mcpvp.com/";
    static final String MINEMEN = "https://minemen.club/player/";

    /** Pages of friends or following read, 50-odd names each: enough for a window, not a crawl. */
    private static final int PEOPLE_PAGES = 4;

    private ProfileLoader() {}

    static void start(Profile profile) {
        DiscordCommands.work(() -> load(profile));
    }

    private static void load(Profile p) {
        String id;
        try {
            JsonObject mojang = DiscordCommands.get(MOJANG + p.asked);
            id = DiscordCommands.string(mojang, "id");
            String name = DiscordCommands.string(mojang, "name");
            if (id == null) {
                fail(p, "no minecraft player called " + p.asked);
                return;
            }
            p.name = name == null ? p.asked : name;
        } catch (Throwable e) {
            fail(p, DiscordCommands.why(e));
            return;
        }
        p.uuid = DiscordCommands.dashed(id);
        p.note = null;

        DiscordCommands.work(() -> mcpvp(p, id));
        DiscordCommands.work(() -> minemen(p));
        DiscordCommands.work(() -> discord(p, id));
    }

    private static void fail(Profile p, String why) {
        p.note = why;
        p.failed = true;
        p.mcpvpNote = "-";
        p.minemenNote = "-";
        p.discordNote = "-";
    }

    private static void discord(Profile p, String id) {
        try {
            String linked = DiscordCommands.discordOf(id);
            p.discord = linked;
            p.discordNote = linked == null ? "none linked on subtiers or mctiers" : null;
        } catch (Throwable e) {
            p.discordNote = DiscordCommands.why(e);
        }
    }

    // ---- mcpvp.com ------------------------------------------------------

    private static void mcpvp(Profile p, String id) {
        JsonObject community;
        try {
            String page = page(MCPVP + "@" + p.name, "/@");
            if (page == null) {
                p.mcpvpNote = "no mcpvp.com page";
                return;
            }
            community = McPvp.community(page);
            p.names = McPvp.names(page);
            p.tiers = McPvp.tiers(page);
            p.kits = McPvpKits.kits(page);
            p.stats = McPvp.stats(page);
            p.online = McPvp.online(page);
            p.ban = McPvp.ban(community);
            p.client = McPvp.client(community);
            p.mods = McPvp.mods(community);
            p.activityTop = McPvp.activity(page, "activity-top");
            p.activityRecent = McPvp.activity(page, "activity-recent");
            p.mcpvpNote = null;
        } catch (Throwable e) {
            p.mcpvpNote = DiscordCommands.why(e);
            return;
        }

        // The rest are the site's own JSON routes, each its own request: one failing leaves
        // its part of the window empty and the page's part standing.
        JsonElement playerId = community == null ? null : community.get("playerId");
        String data = playerId == null || !playerId.isJsonPrimitive() ? null : MCPVP + "profile-data/" + playerId.getAsString();
        try {
            if (data != null) p.rankHistory = McPvp.history(DiscordCommands.get(data + "/rating-history?kit=overall"));
        } catch (Throwable e) {
            p.rankHistory = new float[0];
        }
        try {
            p.matches = McPvp.matches(DiscordCommands.get(MCPVP + "profile/" + id + "/matches?page=1"), p.name);
        } catch (Throwable e) {
            p.matches = List.of();
        }
        List<String> friends = data == null ? List.of("0") : people(data, "friends");
        p.friendsTotal = Minemen.number(friends.get(0));
        p.friends = friends.subList(1, friends.size());
        List<String> following = data == null ? List.of("0") : people(data, "following");
        p.followingTotal = Minemen.number(following.get(0));
        p.following = following.subList(1, following.size());
    }

    /** The site's total, then the names of the first few pages. */
    private static List<String> people(String data, String type) {
        List<String> out = new ArrayList<>();
        out.add("0");
        try {
            for (int page = 1; page <= PEOPLE_PAGES; page++) {
                List<String> got = McPvp.people(DiscordCommands.get(data + "/people?type=" + type + "&page=" + page));
                out.set(0, got.get(0));
                if (got.size() <= 1) break;
                out.addAll(got.subList(1, got.size()));
                if (out.size() - 1 >= Minemen.number(got.get(0))) break;
            }
        } catch (Throwable e) {
            return out;
        }
        return out;
    }

    // ---- minemen.club ---------------------------------------------------

    private static void minemen(Profile p) {
        try {
            String practice = page(MINEMEN + p.name + "/practice/modern", "/player/");
            if (practice == null) {
                p.minemenNote = "never joined minemen.club";
                return;
            }
            p.mmStatus = Minemen.status(practice);
            p.mmSeen = Minemen.seen(practice);
            p.mmRank = Minemen.rank(practice);
            p.mmJoined = Minemen.joined(practice);
            p.mmViews = Minemen.views(practice);
            p.mmFriends = Minemen.friends(practice);
            p.ladders = Minemen.ladders(practice);
            p.eloHistory = Minemen.elo(practice);
            p.minemenNote = null;
        } catch (Throwable e) {
            p.minemenNote = DiscordCommands.why(e);
            return;
        }
        try {
            String summary = page(MINEMEN + p.name, "/player/");
            if (summary == null) return;
            p.mmGlobal = Minemen.globalElo(summary);
            p.mmWorld = Minemen.world(summary);
        } catch (Throwable e) {
            p.mmGlobal = "";
        }
    }

    /**
     * The HTML at {@code url}; null when there is no such player, which mcpvp says by sending
     * you to its home page and minemen with a 404.
     */
    private static String page(String url, String pathStart) throws Exception {
        HttpResponse<String> response = DiscordCommands.send(url, "text/html");
        if (response == null) return null;
        String path = response.uri().getPath();
        if (path == null || !path.startsWith(pathStart)) return null;
        return response.body();
    }
}
