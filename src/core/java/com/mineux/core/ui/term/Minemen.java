package com.mineux.core.ui.term;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What {@link ProfileLoader} reads off a player's pages on minemen.club: {@code /player/<name>}
 * for the global ELO, and {@code /player/<name>/practice/modern} for every ladder. HTML again,
 * by the class names it had on 2026-10-05; a part the site has since changed comes back empty.
 */
final class Minemen {

    private static final Pattern STATUS = Pattern.compile("profile-status-dot\"></span>\\s*([A-Za-z ]+?)\\s*</span>");
    private static final Pattern SEEN = Pattern.compile("class=\"profile-last-seen\"[^>]*data-relative-time=\"(\\d+)\"");
    private static final Pattern RANK = Pattern.compile("class=\"profile-rank\"[^>]*>\\s*([^<]+?)\\s*<");
    private static final Pattern JOINED = Pattern.compile("<span>Joined ([^<]+)</span>");
    private static final Pattern VIEWS = Pattern.compile("<span>([\\d,]+) views / month</span>");
    private static final Pattern GLOBAL = Pattern.compile("Global ELO</span>\\s*<span class=\"pw-summary-value\">\\s*([\\d,]+)\\s*<");
    private static final Pattern WORLD = Pattern.compile("class=\"pw-rank-number\">\\s*#?([\\d,]+)\\s*<");
    private static final Pattern FRIENDS = Pattern.compile("var friendsData = (\\[.*?\\]);", Pattern.DOTALL);
    private static final Pattern LADDER = Pattern.compile(
        "practice-kit-card\"[\\s\\S]*?<h3>([^<]+)</h3>[\\s\\S]*?<span>([\\d,]+) ELO</span>[\\s\\S]*?W/L:</span>\\s*<span>([\\d,]+) / ([\\d,]+)</span>"
            + "[\\s\\S]*?Wins:</span>\\s*<span>([\\d,]+)</span>");
    private static final Pattern POINT = Pattern.compile("practice-chart-point\"[^>]*data-elo=\"([\\d,]+)\"");

    private Minemen() {}

    static String status(String page) {
        return first(STATUS, page);
    }

    /** When they were last on, in epoch ms; 0 when the page does not say. */
    static long seen(String page) {
        String at = first(SEEN, page);
        try {
            return at.isEmpty() ? 0L : Long.parseLong(at);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** Their rank on the server: Default, or what they bought. */
    static String rank(String page) {
        return first(RANK, page);
    }

    static String joined(String page) {
        return first(JOINED, page);
    }

    static String views(String page) {
        return first(VIEWS, page);
    }

    static String globalElo(String page) {
        return first(GLOBAL, page);
    }

    /** Their place in the world by global ELO; empty for a player the site has not placed. */
    static String world(String page) {
        return first(WORLD, page);
    }

    static List<String> friends(String page) {
        List<String> out = new ArrayList<>();
        String json = first(FRIENDS, page);
        if (json.isEmpty()) return out;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonArray()) return out;
            for (JsonElement friend : parsed.getAsJsonArray()) {
                if (!friend.isJsonObject()) continue;
                String name = DiscordCommands.string(friend.getAsJsonObject(), "username");
                if (name != null) out.add(name);
            }
        } catch (RuntimeException e) {
            return out;
        }
        return out;
    }

    /** Every ladder as name, ELO, ranked wins, ranked losses, casual wins: five strings a ladder, commas gone. */
    static List<String> ladders(String page) {
        List<String> out = new ArrayList<>();
        Matcher m = LADDER.matcher(page);
        while (m.find()) {
            out.add(m.group(1).trim());
            for (int i = 2; i <= 5; i++) out.add(m.group(i).replace(",", ""));
        }
        return out;
    }

    /** The ELO chart's points, oldest first. */
    static float[] elo(String page) {
        List<String> points = new ArrayList<>();
        Matcher m = POINT.matcher(page);
        while (m.find()) points.add(m.group(1).replace(",", ""));
        float[] out = new float[points.size()];
        for (int i = 0; i < out.length; i++) out[i] = number(points.get(i));
        return out;
    }

    static int number(String text) {
        try {
            return Integer.parseInt(text.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String first(Pattern pattern, String page) {
        Matcher m = pattern.matcher(page);
        return m.find() ? m.group(1) : "";
    }
}
