package com.mineux.core.social;

import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.DisplaySlot;

import java.util.Locale;

/**
 * What you are playing, worked out from what the server has already told this client.
 * <p>
 * <b>This has to happen here and cannot happen on the VPS.</b> The auth server has no connection
 * to the game server; the only thing in a position to tell an anarchy server from a practice
 * server is the client that just joined one. So the client decides and the service relays the
 * answer untouched.
 * <p>
 * Three sources, cheapest first, and the first that answers wins:
 * <ol>
 *   <li>the address -- a known server is simply known, and this is exact where it applies;</li>
 *   <li>the scoreboard sidebar title -- nearly every minigame network writes its mode there,
 *       which is what makes this work on servers nobody has heard of;</li>
 *   <li>the world itself -- singleplayer, or a realm, or just "multiplayer".</li>
 * </ol>
 * The table is deliberately small and deliberately data. It is a guess shown next to somebody's
 * name, not a fact anything depends on, so being wrong costs a wrong word on a banner.
 */
public final class Gamemode {

    private Gamemode() {}

    /**
     * Known addresses, as {@code "match|label"} rows in one flat array.
     * <p>
     * Flat and parsed at use rather than an array of pairs: obfuscated builds must avoid arrays of
     * arrays (see {@code NameTable}), and a table this size is not worth a type. The match is a
     * substring of the normalised address, so {@code 2b2t} catches every port and subdomain.
     */
    private static final String[] KNOWN = {
        "2b2t.org|anarchy",
        "9b9t.com|anarchy",
        "constantiam.net|anarchy",
        "oldfag.org|anarchy",
        "6b6t.org|anarchy",
        "hypixel.net|hypixel",
        "mc.hypixel.net|hypixel",
        "minemen.club|practice",
        "pvp.land|practice",
        "mcpvp.com|practice",
        "lunar.gg|practice",
        "loyisa.cn|practice",
        "anarchy|anarchy",
        "practice|practice",
        "lifesteal|lifesteal",
        "crystal|crystal pvp",
    };

    /**
     * Words a sidebar title is searched for, as {@code "needle|label"}. Checked in order, so the
     * more specific ones come first -- "crystal pvp" before "pvp".
     */
    private static final String[] SIDEBAR = {
        "crystal|crystal pvp",
        "lifesteal|lifesteal",
        "anarchy|anarchy",
        "bedwars|bedwars",
        "skywars|skywars",
        "skyblock|skyblock",
        "duels|duels",
        "practice|practice",
        "factions|factions",
        "survival|survival",
        "prison|prison",
        "kitpvp|kitpvp",
        "uhc|uhc",
        "smp|smp",
        "pvp|pvp",
    };

    /**
     * What this client is playing, or empty when it is not playing anything.
     * <p>
     * Called once a second at most, from the presence tick, and only when the server changed --
     * so the scoreboard read here is nowhere near a hot path.
     */
    public static String detect(Minecraft mc) {
        if (mc == null || mc.level == null) return "";

        String address = ServerName.current(mc);
        if (address.isEmpty()) return mc.hasSingleplayerServer() ? "singleplayer" : "";

        String known = byAddress(address);
        if (!known.isEmpty()) return known;

        String sidebar = bySidebar(mc);
        if (!sidebar.isEmpty()) return sidebar;

        return "multiplayer";
    }

    private static String byAddress(String address) {
        String host = ServerName.normalise(address);
        for (String row : KNOWN) {
            int bar = row.indexOf('|');
            if (bar <= 0) continue;
            if (host.contains(row.substring(0, bar))) return row.substring(bar + 1);
        }
        return "";
    }

    /**
     * The sidebar's title, matched against the word list.
     * <p>
     * Wrapped whole: the scoreboard API is the kind of thing that moves between versions, and a
     * guess at a label is never worth a crash in the presence tick.
     */
    private static String bySidebar(Minecraft mc) {
        try {
            if (mc.level == null) return "";
            Scoreboard scoreboard = mc.level.getScoreboard();
            Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
            if (objective == null) return "";

            String title = objective.getDisplayName().getString().toLowerCase(Locale.ROOT);
            if (title.isBlank()) return "";

            for (String row : SIDEBAR) {
                int bar = row.indexOf('|');
                if (bar <= 0) continue;
                if (title.contains(row.substring(0, bar))) return row.substring(bar + 1);
            }
        } catch (RuntimeException | LinkageError e) {
            return "";
        }
        return "";
    }
}
