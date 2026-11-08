package com.mineux.core.ui.term;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The per-kit rating factors on a player's mcpvp.com page: what the site's own stats tab
 * shows for a kit under "Tier Information" -- skill rating, confidence, strength of
 * competition, opponent variety, challenge performance, each a 0-100 score and a line saying
 * what it is made of. They are in the page as one JSON object, {@code statsTierInfo}.
 */
final class McPvpKits {

    /** The factors every kit has, in the site's order. */
    static final int FACTORS = 5;
    /** Strings a kit: name, tier, rank, wins, losses, matches, then a score and its detail a factor. */
    static final int STRIDE = 6 + FACTORS * 2;

    private static final Pattern INFO = Pattern.compile("const statsTierInfo = (\\{.*?\\});", Pattern.DOTALL);

    private McPvpKits() {}

    /** Every kit the player has a match in, the most played first, {@link #STRIDE} strings each. */
    static List<String> kits(String page) {
        List<String> out = new ArrayList<>();
        Matcher m = INFO.matcher(page);
        if (!m.find()) return out;
        JsonObject info;
        try {
            JsonElement json = JsonParser.parseString(m.group(1));
            if (!json.isJsonObject()) return out;
            info = json.getAsJsonObject();
        } catch (RuntimeException e) {
            return out;
        }

        List<Integer> played = new ArrayList<>();
        for (String key : info.keySet()) {
            JsonObject kit = McPvp.object(info, key);
            if (kit == null) continue;
            int matches = Minemen.number(text(kit, "matches"));
            List<JsonObject> factors = McPvp.objects(kit, "factors");
            if (matches <= 0 || factors.size() < FACTORS) continue;

            // Most played first: put in ahead of the first kit with fewer matches.
            int at = 0;
            while (at < played.size() && played.get(at) >= matches) at++;
            played.add(at, matches);
            out.addAll(at * STRIDE, row(label(key), kit, factors));
        }
        return out;
    }

    private static List<String> row(String name, JsonObject kit, List<JsonObject> factors) {
        List<String> row = new ArrayList<>();
        row.add(name);
        row.add(text(kit, "tier"));
        row.add(text(kit, "rank"));
        row.add(text(kit, "wins"));
        row.add(text(kit, "losses"));
        row.add(text(kit, "matches"));
        for (int i = 0; i < FACTORS; i++) {
            row.add(text(factors.get(i), "progress"));
            row.add(text(factors.get(i), "value"));
        }
        return row;
    }

    /** {@code end_game} as {@code End Game}. */
    static String label(String key) {
        StringBuilder out = new StringBuilder();
        boolean start = true;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '_') {
                out.append(' ');
                start = true;
            } else {
                out.append(start ? Character.toUpperCase(c) : c);
                start = false;
            }
        }
        return out.toString();
    }

    /** A field as text, whatever it is; {@code -} for one that is missing or null. */
    private static String text(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonPrimitive()) return "-";
        return value.getAsString();
    }
}
