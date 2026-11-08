package com.mineux.core.ui.term;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What {@link ProfileLoader} reads off mcpvp.com. The site has no documented API, so this is
 * its player page's HTML, by the class names and data attributes it had on 2026-10-05, and
 * the JSON its own scripts fetch (friends, rating history, matches); a part the site has since
 * changed comes back empty rather than wrong.
 */
final class McPvp {

    private static final Pattern NAME = Pattern.compile(
        "class=\"history-name\">\\s*([^<]+?)\\s*</span>\\s*<span\\s+class=\"history-date\">\\s*([^<]*?)\\s*<");
    private static final Pattern OVERALL = Pattern.compile("class=\"overall-tier[^\"]*\"\\s+data-tier-code=\"([A-Z0-9]+)\"");
    private static final Pattern KIT = Pattern.compile(
        "data-label=\"([^\"]+)\"[^>]*>\\s*<img[^>]*>\\s*<span[^>]*data-tier-code=\"([A-Z0-9]+)\"");
    private static final Pattern STAT = Pattern.compile("data-stat=\"([a-z_]+)\"[^>]*>\\s*([^<\\s][^<]*?)\\s*<");
    private static final Pattern ONLINE = Pattern.compile("activity-online-pill[^\"]*\"[^>]*>\\s*([^<]*?)\\s*<");
    private static final Pattern COMMUNITY = Pattern.compile("data-profile-community>\\s*(\\{.*?\\})\\s*</script>", Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("<[^>]*>");

    private McPvp() {}

    /** Every name the account has had, newest first, each with the date it was taken when there is one. */
    static List<String> names(String page) {
        List<String> names = new ArrayList<>();
        Matcher m = NAME.matcher(page);
        while (m.find()) names.add(m.group(2).isEmpty() ? m.group(1) : m.group(1) + " (" + m.group(2) + ")");
        return names;
    }

    /** The overall tier, then each ranked kit's: {@code overall HT3}, {@code End Game MT6}. */
    static List<String> tiers(String page) {
        List<String> tiers = new ArrayList<>();
        Matcher overall = OVERALL.matcher(page);
        if (overall.find()) tiers.add("overall " + overall.group(1));
        Matcher kit = KIT.matcher(page);
        while (kit.find()) tiers.add(kit.group(1) + " " + kit.group(2));
        return tiers;
    }

    /** The stats tab's numbers as the page keys them: kills, deaths, kdr, wins, losses, win_rate... */
    static List<String> stats(String page) {
        List<String> stats = new ArrayList<>();
        Matcher m = STAT.matcher(page);
        while (m.find()) {
            stats.add(m.group(1));
            stats.add(m.group(2));
        }
        return stats;
    }

    /** The value of {@code key} in a list of key, value, key, value, or {@code ?}. */
    static String stat(List<String> stats, String key) {
        for (int i = 0; i + 1 < stats.size(); i += 2) {
            if (stats.get(i).equals(key)) return stats.get(i + 1);
        }
        return "?";
    }

    /** {@code Online} or {@code Offline}, as the activity card says; empty when it says neither. */
    static String online(String page) {
        Matcher m = ONLINE.matcher(page);
        return m.find() ? m.group(1) : "";
    }

    /**
     * The page's own data about the player: its id for the JSON routes ({@code playerId}),
     * whether they are banned now ({@code ban}), what client they were last seen on and its
     * mods ({@code client}). Null when the page carries none.
     */
    static JsonObject community(String page) {
        Matcher m = COMMUNITY.matcher(page);
        if (!m.find()) return null;
        try {
            JsonElement json = JsonParser.parseString(m.group(1));
            return json.isJsonObject() ? json.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The site only says whether a ban is in force now; it publishes no history of them. */
    static String ban(JsonObject community) {
        JsonElement ban = community == null ? null : community.get("ban");
        if (ban == null || !ban.isJsonPrimitive()) return "unknown";
        return ban.getAsBoolean() ? "banned" : "not banned";
    }

    /** The client as key, value, key, value: Client, Game Version, Platform. */
    static List<String> client(JsonObject community) {
        List<String> out = new ArrayList<>();
        JsonObject values = object(object(community, "client"), "values");
        if (values == null) return out;
        for (String key : values.keySet()) {
            String value = DiscordCommands.string(values, key);
            if (value == null) continue;
            out.add(key);
            out.add(value);
        }
        return out;
    }

    /** The mods the client reported, by name; one the server's allowed list has disabled says so. */
    static List<String> mods(JsonObject community) {
        List<String> out = new ArrayList<>();
        JsonObject client = object(community, "client");
        JsonElement mods = client == null ? null : client.get("mods");
        if (mods == null || !mods.isJsonArray()) return out;
        for (JsonElement mod : mods.getAsJsonArray()) {
            if (!mod.isJsonObject()) continue;
            String name = DiscordCommands.string(mod.getAsJsonObject(), "name");
            if (name == null) continue;
            JsonElement disabled = mod.getAsJsonObject().get("disabled");
            boolean off = disabled != null && disabled.isJsonPrimitive() && disabled.getAsBoolean();
            out.add(off ? name + " (disabled)" : name);
        }
        return out;
    }

    /** The rows of one of the activity card's lists ({@code activity-top}, {@code activity-recent}), as text. */
    static List<String> activity(String page, String list) {
        List<String> rows = new ArrayList<>();
        int from = page.indexOf("id=\"" + list + "\"");
        if (from < 0) return rows;
        int end = page.indexOf("</ul>", from);
        if (end < 0) return rows;
        String[] parts = page.substring(from, end).split("<li class=\"activity-row\"");
        for (int i = 1; i < parts.length; i++) {
            // The leading number is the row's place in the list, which the window shows anyway.
            String text = TAG.matcher("<" + parts[i]).replaceAll(" ").replaceAll("\\s+", " ").trim().replaceFirst("^\\d+ ", "");
            if (!text.isEmpty()) rows.add(text);
        }
        return rows;
    }

    /** The names in one page of {@code /people}, and (first in the list) how many there are in all. */
    static List<String> people(JsonObject json) {
        List<String> out = new ArrayList<>();
        JsonElement total = json == null ? null : json.get("total");
        out.add(total != null && total.isJsonPrimitive() ? total.getAsString() : "0");
        for (JsonObject item : objects(json, "items")) {
            String name = DiscordCommands.string(item, "name");
            if (name != null) out.add(name);
        }
        return out;
    }

    /**
     * The overall ranking over time, oldest first: the values of {@code rating-history}'s
     * first series. Empty for a player with no history.
     */
    static float[] history(JsonObject json) {
        if (json == null) return new float[0];
        for (String kit : json.keySet()) {
            JsonObject metrics = object(object(json, kit), "metrics");
            if (metrics == null) continue;
            for (String metric : metrics.keySet()) {
                List<JsonObject> points = objects(metrics, metric);
                float[] values = new float[points.size()];
                for (int i = 0; i < values.length; i++) {
                    JsonElement value = points.get(i).get("value");
                    values[i] = value != null && value.isJsonPrimitive() ? value.getAsFloat() : 0f;
                }
                return values;
            }
        }
        return new float[0];
    }

    /** Matches as date, kit, map, score, result, opponent, six strings a match. */
    static List<String> matches(JsonObject json, String player) {
        List<String> out = new ArrayList<>();
        for (JsonObject record : objects(json, "records")) {
            String played = DiscordCommands.string(record, "playedAt");
            out.add(played == null || played.length() < 10 ? "?" : played.substring(0, 10));
            out.add(orDash(DiscordCommands.string(record, "kit")));
            out.add(orDash(DiscordCommands.string(record, "map")));
            out.add(orDash(DiscordCommands.string(record, "score")));
            JsonElement win = record.get("win");
            out.add(win == null || !win.isJsonPrimitive() ? "?" : win.getAsBoolean() ? "W" : "L");
            String opponent = "-";
            JsonElement players = record.get("players");
            if (players != null && players.isJsonArray()) {
                for (JsonElement p : players.getAsJsonArray()) {
                    if (p.isJsonPrimitive() && !p.getAsString().equalsIgnoreCase(player)) opponent = p.getAsString();
                }
            }
            out.add(opponent);
        }
        return out;
    }

    private static String orDash(String text) {
        return text == null ? "-" : text;
    }

    static JsonObject object(JsonObject json, String key) {
        JsonElement value = json == null ? null : json.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    static List<JsonObject> objects(JsonObject json, String key) {
        List<JsonObject> out = new ArrayList<>();
        JsonElement value = json == null ? null : json.get(key);
        if (value == null || !value.isJsonArray()) return out;
        JsonArray array = value.getAsJsonArray();
        for (JsonElement e : array) if (e.isJsonObject()) out.add(e.getAsJsonObject());
        return out;
    }
}
