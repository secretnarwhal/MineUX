package com.mineux.core.ui.term;

import com.google.gson.JsonObject;
import com.mineux.core.ui.term.DiscordCommands.Status;
import com.mineux.core.ui.term.Term.Line;
import com.mineux.core.util.Words;
import net.minecraft.util.Util;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;

import static com.mineux.core.ui.term.Term.*;

/**
 * {@code lookup <player>} (or {@code whois}): everything public about a player. On a desk that
 * is a window, {@link LookupPane}; anywhere there is none to open one on, it is a row a source.
 * The UUID is Mojang's; the name history, tiers, stats and whether they are online are off
 * their mcpvp.com page ({@link McPvp}); the Discord ID is the tier lists', as {@code discord} gives it.
 * <p>
 * NameMC answers anything that is not a browser with a Cloudflare challenge, so nothing is
 * read from it: the last row links to the player's page there, and mcpvp's, to open.
 * <p>
 * The rows are all printed at once and filled in by one worker, as {@link DiscordCommands}
 * fills its line; a source that fails says so on its own row and the rest still answer.
 */
final class LookupCommands {

    private static final String MOJANG = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String MCPVP = "https://www.mcpvp.com/@";
    private static final String NAMEMC = "https://namemc.com/profile/";

    private static final int MAX_NAMES = 6;

    private static final Set<String> W_LOOKUP = Words.of("lookup whois");

    private final Shell shell;

    LookupCommands(Shell shell) {
        this.shell = shell;
    }

    /** Who the links row is about, once Mojang has said. */
    private static final class Found {
        volatile String name;
        volatile String uuid;
    }

    /** @return false for a command that is not one of these */
    boolean run(String cmd, List<String> args) {
        if (!W_LOOKUP.contains(cmd)) return false;
        if (args.isEmpty()) shell.error("usage: lookup <player>");
        else lookup(args.get(0));
        return true;
    }

    /** Whether {@code cmd} is {@code lookup}, whose argument completes as an online player. */
    static boolean completesPlayers(String cmd) {
        return W_LOOKUP.contains(cmd);
    }

    private void lookup(String player) {
        if (!DiscordCommands.validName(player)) {
            shell.error("lookup: a minecraft name is 1-16 letters, digits or _");
            return;
        }

        // On a desk it is a window (LookupPane), with far more in it than these rows have room for.
        if (shell.openWindow(LookupPane.KIND + player)) {
            shell.line().t("lookup ", GOLD).t(player, FG).t(" - opened in a window", DIM);
            return;
        }

        Status head = DiscordCommands.row(shell, "lookup ", GOLD, "looking up " + player + "...");
        Status names = DiscordCommands.row(shell, "  names    ", DIM, "...");
        Status tiers = DiscordCommands.row(shell, "  tiers    ", DIM, "...");
        Status stats = DiscordCommands.row(shell, "  stats    ", DIM, "...");
        Status discord = DiscordCommands.row(shell, "  discord  ", DIM, "...");

        Found found = new Found();
        Line links = shell.line().t("  open     ", DIM);
        links.live(() -> found.name == null ? "" : "mcpvp", () -> CYAN, null, "open their mcpvp.com page in the browser");
        links.last().action = () -> open(found.name == null ? null : MCPVP + found.name);
        links.live(() -> found.uuid == null ? "" : "  namemc", () -> CYAN, null, "open their namemc.com page in the browser");
        links.last().action = () -> open(found.uuid == null ? null : NAMEMC + found.uuid);

        DiscordCommands.work(() -> fill(player, found, head, names, tiers, stats, discord));
    }

    private static void open(String url) {
        if (url != null) Util.getPlatform().openUri(url);
    }

    private static void fill(String player, Found found, Status head, Status names, Status tiers, Status stats, Status discord) {
        String id;
        String name;
        try {
            JsonObject mojang = DiscordCommands.get(MOJANG + player);
            id = DiscordCommands.string(mojang, "id");
            name = DiscordCommands.string(mojang, "name");
        } catch (Throwable e) {
            head.set("failed: " + DiscordCommands.why(e), RED);
            none(names, tiers, stats, discord);
            return;
        }
        if (id == null) {
            head.set("no minecraft player called " + player, RED);
            none(names, tiers, stats, discord);
            return;
        }
        if (name == null) name = player;

        String uuid = DiscordCommands.dashed(id);
        head.answer(name + "  " + uuid, uuid);
        found.name = name;
        found.uuid = uuid;

        mcpvp(name, names, tiers, stats);

        try {
            String linked = DiscordCommands.discordOf(id);
            if (linked == null) discord.set("none linked on subtiers or mctiers", DIM);
            else discord.answer(linked, linked);
        } catch (Throwable e) {
            discord.set(DiscordCommands.why(e), AMBER);
        }
    }

    private static void none(Status names, Status tiers, Status stats, Status discord) {
        names.set("-", FAINT);
        tiers.set("-", FAINT);
        stats.set("-", FAINT);
        discord.set("-", FAINT);
    }

    private static void mcpvp(String name, Status names, Status tiers, Status stats) {
        String page;
        try {
            page = page(name);
        } catch (Throwable e) {
            names.set(DiscordCommands.why(e), AMBER);
            tiers.set("-", FAINT);
            stats.set("-", FAINT);
            return;
        }
        if (page == null) {
            names.set("no mcpvp.com page", DIM);
            tiers.set("-", FAINT);
            stats.set("-", FAINT);
            return;
        }

        List<String> history = McPvp.names(page);
        if (history.isEmpty()) names.set("none listed on mcpvp", DIM);
        else names.set(joined(history, MAX_NAMES), FG);

        List<String> ranked = McPvp.tiers(page);
        if (ranked.isEmpty()) tiers.set("none listed on mcpvp", DIM);
        else tiers.set(joined(ranked, ranked.size()), FG);

        List<String> numbers = McPvp.stats(page);
        String online = McPvp.online(page);
        if (numbers.isEmpty()) {
            stats.set(online.isEmpty() ? "none listed on mcpvp" : online.toLowerCase(java.util.Locale.ROOT), DIM);
            return;
        }
        String text = McPvp.stat(numbers, "kills") + " kills, " + McPvp.stat(numbers, "deaths") + " deaths (" + McPvp.stat(numbers, "kdr") + " kdr), "
            + McPvp.stat(numbers, "wins") + " wins, " + McPvp.stat(numbers, "losses") + " losses (" + McPvp.stat(numbers, "win_rate") + "), streak "
            + McPvp.stat(numbers, "current_streak") + ", best " + McPvp.stat(numbers, "best_streak");
        if (!online.isEmpty()) text = text + ", " + online.toLowerCase(java.util.Locale.ROOT);
        stats.set(text, FG);
    }

    /** The player's mcpvp page; null when there is none, which the site says by sending you to its home page. */
    private static String page(String name) throws Exception {
        HttpResponse<String> response = DiscordCommands.send(MCPVP + name, "text/html");
        if (response == null) return null;
        String path = response.uri().getPath();
        if (path == null || !path.startsWith("/@")) return null;
        return response.body();
    }

    /** The first {@code max} of {@code parts}, comma separated, and how many more there were. */
    private static String joined(List<String> parts, int max) {
        StringBuilder text = new StringBuilder();
        int shown = Math.min(max, parts.size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) text.append(", ");
            text.append(parts.get(i));
        }
        if (parts.size() > shown) text.append(", +").append(parts.size() - shown).append(" more");
        return text.toString();
    }
}
