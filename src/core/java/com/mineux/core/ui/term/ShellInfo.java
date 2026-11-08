package com.mineux.core.ui.term;

import com.mineux.core.net.MuxApi;
import com.mineux.core.ui.term.Term.Line;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.mineux.core.ui.term.Term.*;

/**
 * The commands that only print: {@code help}, {@code keys}, {@code history},
 * {@code whoami} and {@code neofetch}. Kept out of {@link Shell} for its size -- see
 * {@code Shell#exec}.
 */
final class ShellInfo {

    private final Shell shell;

    ShellInfo(Shell shell) {
        this.shell = shell;
    }

    void help() {
        if (shell.menu() != null) {
            menuButtons();
            shell.line();
        }
        shell.line().t(com.mineux.core.MineUX.shellName(), GOLD).t(" - commands (click to use)", DIM);
        for (String[] c : ExtCommands.builtInRows()) {
            shell.line().t("  ").fill(c[0], CYAN, c[0] + " ", c[1]).at(Shell.COL_STATE).t(c[1], DIM);
        }
        if (!com.mineux.core.Extensions.commands().isEmpty() || !com.mineux.core.DeskExtensions.programs().isEmpty()) {
            shell.line();
            shell.line().t("added", GOLD).t(" - commands and programs from what is installed", DIM);
            for (com.mineux.core.ShellCommand c : com.mineux.core.Extensions.commands()) {
                shell.line().t("  ").fill(c.name(), CYAN, c.name() + " ", c.help()).at(Shell.COL_STATE).t(c.help(), DIM);
            }
            for (com.mineux.core.TermProgram.Type p : com.mineux.core.DeskExtensions.programs()) {
                // A program a command of the same name opens is listed once, as the command.
                if (com.mineux.core.ui.term.ExtCommands.command(p.name()) != null) continue;
                shell.line().t("  ").fill(p.name(), CYAN, p.name(), p.help()).at(Shell.COL_STATE).t(p.help() + " (ctrl+c quits)", DIM);
            }
        }
        shell.line();
        shell.line().t("  <module>", GOLD).at(Shell.COL_STATE).t("show it, e.g. ", DIM).fill("rpc", CYAN, "rpc", "show rpc");
        shell.line().t("  <module> on|off", GOLD).at(Shell.COL_STATE + 24).t("flip its switch", DIM);
        shell.line().t("  <module> key:value", GOLD).at(Shell.COL_STATE + 24).fill("rpc server:off", CYAN, "rpc server:off", null);
        shell.line().t("  key:value", GOLD).at(Shell.COL_STATE).t("inside a module (after cd)", DIM);
        shell.line().t("  a;b", GOLD).at(Shell.COL_STATE).t("run several, e.g. ", DIM).fill("cd rpc; ls", CYAN, "cd rpc; ls", null);
        shell.line().t("  bind <key> \"<cmd>\"", GOLD).at(Shell.COL_STATE + 24).t("e.g. ", DIM)
            .fill("bind r \"toggle rpc\"", CYAN, "bind r \"toggle rpc\"", null);
        vulkanNote(shell);
    }

    /** Every command there is to type, as {name, help}: the shell's own, then what extensions added. */
    static List<String[]> allRows() {
        List<String[]> out = new java.util.ArrayList<>(ExtCommands.builtInRows());
        for (com.mineux.core.ShellCommand c : com.mineux.core.Extensions.commands()) out.add(new String[] {c.name(), c.help()});
        for (com.mineux.core.TermProgram.Type p : com.mineux.core.DeskExtensions.programs()) {
            if (ExtCommands.command(p.name()) == null) out.add(new String[] {p.name(), p.help()});
        }
        return out;
    }

    /** The one line that tells a Vulkan player why some settings are missing; nothing on OpenGL. */
    static void vulkanNote(Shell shell) {
        String note = Modules.vulkanNote();
        if (note == null) return;
        shell.line();
        shell.line().t("vulkan", GOLD).t(" - " + note, DIM);
    }

    void shortcuts() {
        String sup = Shell.superKey().toUpperCase(Locale.ROOT);
        // The mouse, and the two keys that are not binds: shortcut and meaning, in turn.
        String[] rows = {
            sup + "+drag", "move: near an edge to go beside, the middle to swap, a group's tabs to join",
            sup + "+right drag", "resize (in dwindle, the split under it)",
            "title bar drag", "move a window (worlds, servers, readouts)",
            "corner drag", "resize a window",
            "wheel on tabs", "step through a group's tabs",
            "in game", sup + "+number switches workstation; the hotbar ignores numbers while " + sup + " is down; "
                + sup + "+K / " + sup + "+L fade the pinned windows",
            "click", "focuses a window and brings it to the top (hovering does neither)",
            "esc", "close the desktop",
            "alt+esc", "in a world: to the esc desk",
        };
        shell.line().t("window manager", GOLD).t(" - SUPER is ", DIM).t(Shell.superKey(), GOLD)
            .t(" (", DIM).fill("super", CYAN, "super ", "change it").t("); keys from ", DIM)
            .run("dotfiles hypr", CYAN, "dotfiles hypr", "the config file");
        for (HyprConf.Bind b : HyprConf.get().binds) {
            String what = b.arg.isEmpty() ? b.dispatcher : b.dispatcher + " " + b.arg;
            shell.line().t("  " + b.keys.replace("SUPER", sup), CYAN).at(Shell.COL_STATE + 24).t(what, DIM);
        }
        for (int i = 0; i + 1 < rows.length; i += 2) shell.line().t("  " + rows[i], CYAN).at(Shell.COL_STATE + 24).t(rows[i + 1], DIM);
    }

    void history() {
        List<String> history = Workstations.state().history;
        int from = Math.max(0, history.size() - 30);
        for (int i = from; i < history.size(); i++) {
            shell.line().t(String.format(Locale.ROOT, "%4d  ", i + 1), FAINT).fill(history.get(i), FG, history.get(i), "run again");
        }
    }

    void whoami() {
        String account = MuxApi.user();
        shell.line().t(shell.user(), GREEN).t("  minecraft", DIM);
        if (MuxApi.signedIn()) {
            shell.line().t(account, GOLD).t("  " + Shell.brand() + " account  ", DIM).run("logout", CYAN, "logout", "sign out and open the greeter");
        } else {
            shell.line().t("guest", GOLD).t("  not signed in to " + Shell.brand() + ": friends, irc and the bell wait for a sign-in  ", DIM)
                .run("login", CYAN, "login", "open the greeter");
        }
    }

    void neofetch() {
        String[][] info = Fetch.rows(shell.user(), shell.menu());

        // The campfire is drawn by the terminal down the left of these lines (Line#fireRows).
        for (int i = 0; i < info.length; i++) {
            Line line = shell.line().at(Fetch.INFO_X);
            if (i == 0) line.fireRows = info.length + 1;
            if (info[i][1].isEmpty()) line.t(info[i][0], i == 0 ? GREEN : FAINT);
            else line.t(info[i][0], GOLD).t(": ", DIM).t(info[i][1], FG);
        }

        Line swatch = shell.line().at(Fetch.INFO_X);
        for (int color : Fetch.COLORS) swatch.t("### ", color);
    }

    /**
     * The main menu's {@code ls}: its buttons, every command, and the modules -- everything
     * there is to type. The Esc menu's is its buttons alone.
     */
    void menuListing() {
        menuButtons();
        if (shell.menu().pause()) return;
        shell.line();

        shell.line().t("commands", GOLD).t(" - ", DIM).fill("help", CYAN, "help", "what each one does").t(" says what they do", DIM);
        List<String[]> commands = allRows();
        grid(commands.size(), 6, 76, (line, i) -> line.fill(commands.get(i)[0], CYAN, commands.get(i)[0] + " ", commands.get(i)[1]));
        shell.line();

        List<Node> modules = Modules.root().children;
        shell.line().t("modules", GOLD).t(" - cd into one, or type its name", DIM);
        grid(modules.size(), 6, 76, (line, i) -> {
            Node node = modules.get(i);
            line.run(node.name + "/", node.toggle != null && node.on() ? GREEN : BLUE, "cd " + node.name + "; ls", node.desc);
        });
    }

    /**
     * The menu's buttons: vanilla's in a grid, then each other mod's on a line of its own --
     * its icon, the command, and whose button it is and what it says.
     * <p>
     * On the main menu, Singleplayer and Multiplayer here fold the worlds or the servers open
     * right under the grid ({@link Drawer}), above the other mods' buttons; typed, the same
     * commands open a window instead.
     */
    private void menuButtons() {
        List<MenuButtons.Entry> vanilla = new ArrayList<>();
        List<MenuButtons.Entry> mods = new ArrayList<>();
        for (MenuButtons.Entry entry : shell.menu().entries()) (entry.mod() == null ? vanilla : mods).add(entry);

        Drawer worlds = null, servers = null;
        if (!shell.menu().pause()) {
            worlds = new Drawer(PlayCommands::worldLines, Worlds::refresh);
            servers = new Drawer(PlayCommands::serverLines, Servers::refresh);
            worlds.partner = servers;
            servers.partner = worlds;
        }
        Drawer worldsDrawer = worlds, serversDrawer = servers;

        shell.line().t(shell.menu().where(), GOLD).t(" - click one, or type it", DIM);
        grid(vanilla.size(), 4, 100, (line, i) -> {
            MenuButtons.Entry entry = vanilla.get(i);
            line.icon(entry).run(entry.name(), entry.enabled() ? MAGENTA : FAINT, entry.name(), entry.label());
            Drawer drawer = entry.name().equals("singleplayer") ? worldsDrawer : entry.name().equals("multiplayer") ? serversDrawer : null;
            if (drawer != null && entry.enabled()) {
                line.last().action = drawer::toggle;
                line.last().hint = "click: the " + (drawer == worldsDrawer ? "worlds" : "servers") + " here | type " + entry.name() + ": a window";
            }
        });
        if (worlds != null) {
            shell.line().drawer = worlds;
            shell.line().drawer = servers;
        }
        if (mods.isEmpty()) return;

        shell.line();
        shell.line().t("from mods", GOLD).t(" - buttons other mods added", DIM);
        for (MenuButtons.Entry entry : mods) {
            Line line = shell.line().at(8).icon(entry).run(entry.name(), entry.enabled() ? MAGENTA : FAINT, entry.name(), entry.describe());
            line.at(Shell.COL_DESC).t(entry.mod().name(), GOLD);
            if (!MenuButtons.slug(entry.label()).equals(entry.name())) line.t("  " + entry.label(), DIM);
        }
    }

    /** {@code count} cells, {@code perRow} to a line, {@code width} px apart. */
    private void grid(int count, int perRow, int width, java.util.function.ObjIntConsumer<Line> cell) {
        Line line = null;
        for (int i = 0; i < count; i++) {
            if (i % perRow == 0) line = shell.line();
            line.at(8 + i % perRow * width);
            cell.accept(line, i);
        }
    }
}
