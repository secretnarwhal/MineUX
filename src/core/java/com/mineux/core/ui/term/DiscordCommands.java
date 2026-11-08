package com.mineux.core.ui.term;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineux.core.ui.term.Term.Line;
import com.mineux.core.ui.term.Term.Suggestion;
import com.mineux.core.util.Words;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static com.mineux.core.ui.term.Term.*;

/**
 * Discord lookups through the tier lists, ported from furrito: {@code discord <player>} finds the
 * Discord account linked to a player's SubTiers or MCTiers profile, {@code rdiscord <id>} (or {@code rd})
 * the player linked to a Discord ID. Plain HTTP, so they work on the main menu as well as in
 * a world.
 * <p>
 * The lookups run on a worker and report on one line whose text it updates, as
 * {@link AccountCommands} does -- the scrollback is only ever added to on the client thread.
 * The answer is a span a click copies.
 */
final class DiscordCommands {

    private static final String MOJANG = "https://api.mojang.com/users/profiles/minecraft/";
    /**
     * The same API on both, each with its own players. SubTiers is asked first, then MCTiers,
     * which moved from mctiers.com (dead since 2026-10-04) to mctiers.net.
     */
    private static final String SUBTIERS = "https://subtiers.net/api/v2/profile/";
    private static final String MCTIERS = "https://mctiers.net/api/v2/profile/";

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private static final Set<String> W_DISCORD = Words.of("discord");
    private static final Set<String> W_RDISCORD = Words.of("rdiscord rd");

    private final Shell shell;

    DiscordCommands(Shell shell) {
        this.shell = shell;
    }

    /** One line a worker writes its progress to, and the answer a click on it copies. */
    static final class Status {
        volatile String text;
        volatile int color = DIM;
        volatile String copy;
        volatile boolean copied;

        Status(String text) {
            this.text = text;
        }

        void set(String text, int color) {
            this.text = text;
            this.color = color;
        }

        void answer(String text, String copy) {
            this.copy = copy;
            set(text, GREEN);
        }
    }

    /** @return false for a command that is not one of these */
    boolean run(String cmd, List<String> args) {
        if (W_DISCORD.contains(cmd)) {
            if (args.isEmpty()) shell.error("usage: discord <player>");
            else discord(args.get(0));
        } else if (W_RDISCORD.contains(cmd)) {
            if (args.isEmpty()) shell.error("usage: rdiscord <discord id>");
            else reverse(args.get(0));
        } else {
            return false;
        }
        return true;
    }

    /** Whether {@code cmd} is {@code discord}, whose argument completes as an online player. */
    static boolean completesPlayers(String cmd) {
        return W_DISCORD.contains(cmd);
    }

    /** The players in the tab list, by their real names. Nothing off a server. */
    static void players(List<Suggestion> all) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            String name = info.getProfile().name();
            if (name != null && !name.isEmpty()) all.add(new Suggestion(name, name, "", GREEN));
        }
    }

    private Status status(String label, String first) {
        return row(shell, label + " ", GOLD, first);
    }

    /** A line of {@code label}, then what a worker makes of {@code first}; {@link LookupCommands} stacks them. */
    static Status row(Shell shell, String label, int labelColor, String first) {
        Status status = new Status(first);
        // [copy] reads as nothing, and so takes no room to click, until there is an answer.
        Line line = shell.line().t(label, labelColor).live(() -> status.text, () -> status.color, null, null)
            .live(() -> status.copy == null ? "" : status.copied ? "  [copied]" : "  [copy]",
                () -> status.copied ? GREEN : CYAN, null, "copy it to the clipboard");
        line.last().action = () -> {
            String copy = status.copy;
            if (copy == null) return;
            Minecraft.getInstance().keyboardHandler.setClipboard(copy);
            status.copied = true;
        };
        return status;
    }

    // ---- discord <player> -----------------------------------------------

    private void discord(String player) {
        if (!validName(player)) {
            shell.error("discord: a minecraft name is 1-16 letters, digits or _");
            return;
        }

        Status status = status("discord", "looking up " + player + "...");
        work(() -> lookUpDiscord(player, status));
    }

    private static void lookUpDiscord(String player, Status status) {
        try {
            JsonObject mojang = get(MOJANG + player);
            String id = string(mojang, "id");
            if (id == null) {
                status.set("no minecraft player called " + player, RED);
                return;
            }
            String name = string(mojang, "name");
            if (name == null) name = player;

            String discord = discordOf(id);
            if (discord == null) status.set(name + " has no discord linked on subtiers or mctiers", AMBER);
            else status.answer(name + "'s discord id: " + discord, discord);
        } catch (Throwable e) {
            failed(status, e);
        }
    }

    /** The Discord ID linked to a Mojang (undashed) UUID on either list; null for no profile or none linked. */
    static String discordOf(String id) throws Exception {
        return string(either(dashed(id), "discord_id"), "discord_id");
    }

    /**
     * The profile at {@code path} on the first list that has one with {@code key} in it. One
     * list being down is nothing while the other answers, even to say nobody; both down is
     * the first one's failure.
     */
    private static JsonObject either(String path, String key) throws Exception {
        JsonObject profile = null;
        Exception down = null;
        try {
            profile = get(SUBTIERS + path);
        } catch (Exception e) {
            down = e;
        }
        if (string(profile, key) != null) return profile;
        try {
            JsonObject other = get(MCTIERS + path);
            if (other != null) return other;
        } catch (Exception e) {
            if (down != null) throw down;
        }
        return profile;
    }

    // ---- rdiscord <id> --------------------------------------------------

    private void reverse(String discord) {
        if (!validId(discord)) {
            shell.error("rdiscord: a discord id is a number, like 630872658027872273");
            return;
        }

        Status status = status("rdiscord", "looking up discord id " + discord + "...");
        work(() -> lookUpPlayer(discord, status));
    }

    private static void lookUpPlayer(String discord, Status status) {
        try {
            JsonObject profile = either("by-discord/" + discord, "name");
            if (profile == null) {
                status.set("no subtiers or mctiers profile linked to discord id " + discord, AMBER);
                return;
            }
            String name = string(profile, "name");
            if (name == null) status.set("a profile, but no name, for discord id " + discord, AMBER);
            else status.answer("discord id " + discord + " is " + name, name);
        } catch (Throwable e) {
            failed(status, e);
        }
    }

    // ---- plumbing -------------------------------------------------------

    /**
     * A {@link Runnable}, not a functional interface of our own: the obfuscator adds
     * its key argument to such an interface's method but not to the lambda made for it, so
     * calling one throws AbstractMethodError. JDK interfaces are left alone.
     */
    static void work(Runnable lookup) {
        Thread worker = new Thread(lookup, "mineux-discord");
        worker.setDaemon(true);
        worker.start();
    }

    /** Throwable, not Exception: an Error would end the worker silently, the line stuck on "looking up". */
    private static void failed(Status status, Throwable e) {
        com.mineux.core.MineUX.LOGGER.warn("discord lookup failed", e);
        status.set("failed: " + why(e), RED);
    }

    static String why(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /** The JSON object at {@code url}; null for a 404 or 204, which both APIs answer "nobody" with. */
    static JsonObject get(String url) throws Exception {
        HttpResponse<String> response = send(url, "application/json");
        if (response == null) return null;
        JsonElement json = JsonParser.parseString(response.body());
        return json.isJsonObject() ? json.getAsJsonObject() : null;
    }

    /**
     * The 200 answer from {@code url}, redirects followed; null for a 404 or 204. A failure
     * names the host and what went wrong with it: the HTTP client's own exception for a name
     * that does not resolve has no message at all.
     */
    static HttpResponse<String> send(String url, String accept) throws Exception {
        String host = URI.create(url).getHost();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(8))
            .header("Accept", accept)
            .GET()
            .build();
        HttpResponse<String> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new IllegalStateException(host + " timed out", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(unresolved(e) ? host + " does not resolve (the site is down)" : "could not reach " + host, e);
        }
        int code = response.statusCode();
        if (code == 404 || code == 204) return null;
        if (code != 200) throw new IllegalStateException("http " + code + " from " + host);
        return response;
    }

    private static boolean unresolved(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.nio.channels.UnresolvedAddressException || cause instanceof java.net.UnknownHostException) return true;
        }
        return false;
    }

    /** Checked before either goes into a URL. */
    static boolean validName(String player) {
        return player.matches("[A-Za-z0-9_]{1,16}");
    }

    static boolean validId(String discord) {
        return discord.matches("[0-9]{5,25}");
    }

    /** A string field, or null when it is missing or null. */
    static String string(JsonObject json, String key) {
        if (json == null) return null;
        JsonElement value = json.get(key);
        if (value == null || value.isJsonNull()) return null;
        String text = value.getAsString();
        return text.isEmpty() ? null : text;
    }

    /** Mojang's undashed UUID, as the tier lists take it. */
    static String dashed(String id) {
        if (id.length() != 32) return id;
        return id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16) + "-" + id.substring(16, 20) + "-" + id.substring(20);
    }
}
