package com.mineux.core.ui.term;

import com.mineux.core.ui.term.Term.Line;
import com.mineux.core.util.Words;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelSummary;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.mineux.core.ui.term.Term.*;

/**
 * Singleplayer and multiplayer on the main menu: {@code worlds} and {@code servers} open their
 * windows on the desk, {@code play} and {@code join} go in, {@code newworld} makes one,
 * {@code servers add|rm} edits the list. The windows' lines are built here too, so a click on
 * one runs the same command as typing it.
 */
final class PlayCommands {

    private static final DateTimeFormatter PLAYED = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.ROOT)
        .withZone(ZoneId.systemDefault());

    private final Shell shell;

    PlayCommands(Shell shell) {
        this.shell = shell;
    }

    private static final Set<String> W_WORLDS = Words.of("worlds world");
    private static final Set<String> W_SERVERS = Words.of("servers server");
    private static final Set<String> W_PLAY = Words.of("play");
    private static final Set<String> W_JOIN = Words.of("join connect");
    private static final Set<String> W_DISCONNECT = Words.of("disconnect dc leave");
    private static final Set<String> W_NEWWORLD = Words.of("newworld new-world create");

    /** @return false for a command that is not one of these */
    boolean run(String cmd, List<String> args) {
        if (W_WORLDS.contains(cmd)) {
            worlds(args);
        } else if (W_SERVERS.contains(cmd)) {
            servers(args);
        } else if (W_PLAY.contains(cmd)) {
            play(args);
        } else if (W_JOIN.contains(cmd)) {
            join(args);
        } else if (W_DISCONNECT.contains(cmd)) {
            disconnect();
        } else if (W_NEWWORLD.contains(cmd)) {
            Screen title = title();
            if (title != null) Worlds.create(title);
        } else {
            return false;
        }
        return true;
    }

    /** The title screen, when this is the main menu's terminal; an error and null anywhere else. */
    private Screen title() {
        MenuButtons menu = shell.menu();
        if (menu == null || menu.pause()) {
            shell.error("only from the home desk - leave the world first");
            return null;
        }
        return menu.title();
    }

    private void worlds(List<String> args) {
        if (title() == null) return;
        Worlds.refresh();
        if (!shell.openWindow("worlds")) shell.error("worlds: no desk to open it on");
    }

    private static final Set<String> W_ADD = Words.of("add new");
    private static final Set<String> W_RM = Words.of("rm remove del delete");
    private static final Set<String> W_REFRESH = Words.of("refresh ping");

    private void servers(List<String> args) {
        String sub = args.isEmpty() ? "" : args.get(0).toLowerCase(Locale.ROOT);
        if (W_ADD.contains(sub)) {
            if (args.size() < 3) {
                shell.error("usage: servers add <name> <address>");
                return;
            }
            String error = Servers.add(args.get(1), args.get(2));
            if (error != null) shell.error("servers: " + error);
            else shell.ok("added " + args.get(1) + " (" + args.get(2) + ")");
        } else if (W_RM.contains(sub)) {
            ServerData server = args.size() < 2 ? null : Servers.find(args.get(1));
            if (server == null) {
                shell.error("servers: no server '" + (args.size() < 2 ? "" : args.get(1)) + "'");
                return;
            }
            Servers.remove(server);
            shell.ok("removed " + server.name);
        } else if (W_REFRESH.contains(sub)) {
            Servers.refresh();
        } else {
            Servers.refresh();
            // On the main menu, a window; in game, printed here, to click to join.
            if (!shell.openWindow("servers")) {
                for (Line line : serverLines()) shell.out.add(line);
            }
        }
    }

    private void play(List<String> args) {
        Screen title = title();
        if (title == null) return;
        if (args.isEmpty()) {
            worlds(args);
            return;
        }
        String which = String.join(" ", args);
        if (Worlds.list().isEmpty() && !Worlds.loading()) Worlds.refresh();
        LevelSummary world = Worlds.find(which);
        if (world == null) {
            shell.error("play: no world '" + which + "' - " + (Worlds.loading() ? "still reading the saves, try again" : "worlds lists them"));
            return;
        }
        String error = Worlds.play(world, title);
        if (error != null) shell.error("play: " + error);
    }

    /**
     * {@code join <number|name|address>}: a saved server, or straight to any address. Works in
     * game too, leaving the world first.
     */
    private void join(List<String> args) {
        if (args.isEmpty()) {
            servers(args);
            return;
        }
        MenuButtons menu = shell.menu();
        MenuButtons.Entry multiplayer = menu == null ? null : menu.find("multiplayer");
        if (multiplayer != null && !multiplayer.enabled()) {
            shell.error("join: multiplayer is disabled for this account");
            return;
        }
        Screen title = menu != null && !menu.pause() ? menu.title() : new TitleScreen();
        ServerData server = Servers.find(args.get(0));
        if (server != null) {
            Servers.join(server, title);
            return;
        }
        String error = Servers.joinAddress(args.get(0), title);
        if (error != null) shell.error("join: " + error + " - servers lists the saved ones");
    }

    /** {@code disconnect}: out of the world or server, back to the main menu. */
    private void disconnect() {
        if (Minecraft.getInstance().level == null) {
            shell.error("disconnect: not in a world");
            return;
        }
        Servers.leave();
    }

    // ---- the windows' contents -----------------------------------------

    static List<Line> worldLines() {
        List<Line> out = new ArrayList<>();
        out.add(new Line().run("[+ new world]", GREEN, "newworld", "make a world")
            .t("  ").run("[refresh]", CYAN, "worlds", "read the saves again")
            .t("  ").run("[vanilla]", DIM, "vanilla singleplayer", "vanilla's world list"));
        out.add(new Line());

        List<LevelSummary> worlds = Worlds.list();
        if (Worlds.error() != null) out.add(new Line().t("  " + Worlds.error(), RED));
        else if (worlds.isEmpty()) out.add(new Line().t(Worlds.loading() ? "  reading the saves..." : "  no worlds yet", FAINT));

        for (int i = 0; i < worlds.size(); i++) {
            LevelSummary world = worlds.get(i);
            boolean playable = !world.isLocked() && !world.isDisabled();
            Line line = new Line().t(String.format(Locale.ROOT, "%2d ", i + 1), FAINT);
            line.run(world.getLevelName(), playable ? FG : FAINT, "play " + (i + 1), "play " + world.getLevelName());
            line.at(150).t(Worlds.mode(world), world.isHardcore() ? RED : AMBER);
            if (world.hasCommands()) line.t(" cheats", MAGENTA);
            line.at(240).t(world.getLastPlayed() > 0 ? PLAYED.format(Instant.ofEpochMilli(world.getLastPlayed())) : "-", DIM);
            if (world.isLocked()) line.t("  in use", RED);
            else if (world.requiresManualConversion()) line.t("  needs converting", RED);
            out.add(line);
        }
        return out;
    }

    static List<Line> serverLines() {
        List<Line> out = new ArrayList<>();
        out.add(new Line().fill("[+ add]", GREEN, "servers add ", "servers add <name> <address>")
            .t("  ").fill("[direct]", CYAN, "join ", "join <address>")
            .t("  ").run("[refresh]", CYAN, "servers refresh", "ping them again")
            .t("  ").run("[vanilla]", DIM, "vanilla multiplayer", "vanilla's server list"));
        out.add(new Line());

        List<ServerData> servers = Servers.all();
        if (servers.isEmpty()) out.add(new Line().t("  no servers saved - [+ add] or [direct]", FAINT));

        for (int i = 0; i < servers.size(); i++) {
            ServerData server = servers.get(i);
            Line line = new Line().t(String.format(Locale.ROOT, "%2d ", i + 1), FAINT).icon(server);
            line.run(server.name, FG, "join " + (i + 1), "join " + server.ip);
            line.at(140).t(server.ip, DIM);
            line.at(280);
            ServerData.State state = server.state();
            if (state == ServerData.State.SUCCESSFUL) {
                line.t(server.ping + "ms", server.ping < 80 ? GREEN : server.ping < 200 ? AMBER : RED);
                if (server.players != null) line.t("  " + server.players.online() + "/" + server.players.max(), CYAN);
            } else if (state == ServerData.State.INCOMPATIBLE) {
                line.t("other version", AMBER);
            } else if (state == ServerData.State.UNREACHABLE) {
                line.t("offline", RED);
            } else {
                line.t("pinging...", FAINT);
            }
            String motd = Servers.motd(server);
            if (!motd.isEmpty() && state == ServerData.State.SUCCESSFUL) line.at(380).t(motd, DIM);
            out.add(line);
        }
        return out;
    }
}
