package com.mineux.core.ui.term;

import com.mineux.core.util.Words;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static com.mineux.core.ui.term.Term.*;

/**
 * {@code hyprctl}: the window manager from the terminal, as Hyprland's own is. {@code dispatch}
 * runs any dispatcher a bind can ({@code hyprctl dispatch movetoworkspace 3}), {@code reload}
 * rereads {@code hyprland.conf}, {@code keyword} sets a value until the game closes,
 * {@code binds} lists the keys and {@code configerrors} what the file got wrong.
 */
final class HyprCommands {

    private final Shell shell;

    HyprCommands(Shell shell) {
        this.shell = shell;
    }

    private static final Set<String> W_DISPATCH = Words.of("dispatch");
    private static final Set<String> W_RELOAD = Words.of("reload");
    private static final Set<String> W_KEYWORD = Words.of("keyword");
    private static final Set<String> W_BINDS = Words.of("binds");
    private static final Set<String> W_ERRORS = Words.of("configerrors errors");
    private static final Set<String> W_WORKSPACES = Words.of("workspaces");
    private static final Set<String> W_CLIENTS = Words.of("clients");

    /** Every subcommand, for completion and usage. */
    static final String[] SUBS = {"dispatch", "reload", "keyword", "binds", "configerrors", "workspaces", "clients"};

    void run(List<String> args) {
        String sub = args.isEmpty() ? "" : args.get(0).toLowerCase(Locale.ROOT);
        if (W_DISPATCH.contains(sub)) {
            dispatch(args);
        } else if (W_RELOAD.contains(sub)) {
            HyprConf conf = HyprConf.reload();
            if (conf.errors.isEmpty()) shell.ok("ok - " + HyprConf.path());
            else shell.error(conf.errors.size() + " config error" + (conf.errors.size() == 1 ? "" : "s") + " - hyprctl configerrors");
        } else if (W_KEYWORD.contains(sub)) {
            keyword(args);
        } else if (W_BINDS.contains(sub)) {
            binds();
        } else if (W_ERRORS.contains(sub)) {
            errors();
        } else if (W_WORKSPACES.contains(sub)) {
            workspaces();
        } else if (W_CLIENTS.contains(sub)) {
            clients();
        } else {
            usage();
        }
    }

    private void usage() {
        shell.line().t("hyprctl", GOLD).t(" - the window manager; ", DIM).run("dotfiles hypr", CYAN, "dotfiles hypr", "the config file")
            .t(" configures it", DIM);
        // Usage and what it does, in turn: flat, as obfuscated builds need their tables.
        String[] rows = {
            "dispatch <dispatcher> [arg]", "run one: hyprctl dispatch workspace 2",
            "reload", "reread hyprland.conf",
            "keyword <key> <value>", "set a value until the game closes",
            "binds", "every key the window manager answers to",
            "configerrors", "what hyprland.conf got wrong",
            "workspaces", "every workstation and what is on it",
            "clients", "every window, on every desk",
        };
        for (int i = 0; i + 1 < rows.length; i += 2) {
            shell.line().t("  ").fill(rows[i], CYAN, "hyprctl " + rows[i].split(" ")[0] + " ", null)
                .at(Shell.COL_DESC + 40).t(rows[i + 1], DIM);
        }
        StringBuilder names = new StringBuilder();
        for (String d : Hypr.names()) names.append(d).append(' ');
        shell.line().t("  dispatchers: ", FAINT).t(names.toString().trim(), DIM);
    }

    private void dispatch(List<String> args) {
        if (args.size() < 2) {
            shell.error("hyprctl dispatch <dispatcher> [argument]");
            return;
        }
        String dispatcher = args.get(1).toLowerCase(Locale.ROOT);
        String arg = args.size() > 2 ? String.join(" ", args.subList(2, args.size())) : "";
        if (!Hypr.known(dispatcher)) {
            shell.error("hyprctl: unknown dispatcher '" + dispatcher + "'");
            return;
        }
        // The window mode is the game window's, not the desktop's: it works from a bound key in game too.
        String error = Objects.equals("windowmode", dispatcher) ? WindowMode.request(arg) : shell.hypr(dispatcher, arg);
        if (error != null) shell.error(error);
        else shell.ok("ok");
    }

    private void keyword(List<String> args) {
        if (args.size() < 3) {
            shell.error("hyprctl keyword <key> <value>, e.g. hyprctl keyword general:layout dwindle");
            return;
        }
        String value = String.join(" ", args.subList(2, args.size()));
        String error = HyprConf.keyword(args.get(1), value);
        if (error != null) shell.error("hyprctl: " + error);
        else shell.ok(args.get(1) + " = " + value + " (until the game closes)");
    }

    private void binds() {
        HyprConf conf = HyprConf.get();
        shell.line().t("binds", GOLD).t(" - SUPER is ", DIM).t(Shell.superKey(), GOLD).t("; from ", DIM)
            .t(String.valueOf(HyprConf.path().getFileName()), DIM);
        for (HyprConf.Bind b : conf.binds) {
            String cmd = b.arg.isEmpty() ? b.dispatcher : b.dispatcher + " " + b.arg;
            shell.line().t("  " + b.keys, CYAN).at(Shell.COL_DESC + 24)
                .run(cmd, FG, "hyprctl dispatch " + cmd, "run it now");
        }
    }

    /** Each workstation with its number, how many windows it has and which has focus; the special workspace last. */
    private void workspaces() {
        shell.line().t("workspaces", GOLD).t(" - click one to go there", DIM);
        for (String name : Workstations.names()) {
            Desk desk = Desk.peek(name);
            boolean open = name.equals(Workstations.current());
            shell.line().t(open ? "  * " : "    ", GREEN).t(Workstations.slot(name) + " ", FAINT)
                .run(name, open ? MAGENTA : FG, "hyprctl dispatch workspace " + name, "go to " + name)
                .at(Shell.COL_DESC).t(summary(desk), DIM);
        }
        if (!Workstations.hasSpecial()) {
            shell.line().t("    ").run("special", CYAN, "hyprctl dispatch togglespecialworkspace", "make the special workspace and go to it")
                .at(Shell.COL_DESC).t("not made yet", DIM);
        }
        if (!Desk.PINS.isEmpty()) shell.line().t("    pinned", GOLD).at(Shell.COL_DESC).t(Desk.PINS.size() + " shown over the game", DIM);
    }

    private static String summary(Desk desk) {
        if (desk == null) return "not opened yet";
        int n = desk.windows.size();
        Pane f = desk.focused;
        return n + " window" + (n == 1 ? "" : "s") + (f != null && desk.windows.contains(f) ? ", focused: " + f.title() : "");
    }

    /** Every window on every desk that has been opened: what it is and how it sits. */
    private void clients() {
        shell.line().t("clients", GOLD);
        for (String name : Workstations.names()) clientsOf(name, Desk.peek(name));
        clientsOf("esc", Desk.peek(TerminalScreen.ESC_DESK));
    }

    private void clientsOf(String name, Desk desk) {
        if (desk == null || desk.windows.isEmpty()) return;
        shell.line().t("  " + name, MAGENTA);
        for (Pane p : desk.windows) {
            String how = Group.hidden(p) ? "tab (hidden)" : p.floating ? "floating" : "tiled";
            if (p.onAll) how += ", pinned";
            if (p.group != null && !Group.hidden(p)) how += ", group of " + p.group.members.size();
            shell.line().t("    " + p.title(), p == desk.focused ? GREEN : FG).at(Shell.COL_DESC + 24).t(how, DIM)
                .t("  " + Math.round(p.w) + "x" + Math.round(p.h) + " at " + Math.round(p.x) + "," + Math.round(p.y), FAINT);
        }
    }

    // ---- completion -------------------------------------------------------

    private static final Set<String> W_DIRECTIONS = Words.of("movefocus movewindow moveintogroup");
    private static final Set<String> W_WORKSPACE_ARGS = Words.of("workspace movetoworkspace movetoworkspacesilent");

    /** {@code hyprctl}'s arguments: subcommands, dispatchers, their arguments, keyword keys. */
    static void complete(List<String> prev, List<Term.Suggestion> all) {
        if (prev.size() == 1) {
            for (String s : SUBS) all.add(new Term.Suggestion(s, s, "", CYAN));
            return;
        }
        String sub = prev.get(1).toLowerCase(Locale.ROOT);
        if (W_KEYWORD.contains(sub) && prev.size() == 2) {
            for (String k : HyprConf.KEYS.split(" ")) all.add(new Term.Suggestion(k, k, "", FG));
            return;
        }
        if (!W_DISPATCH.contains(sub)) return;
        if (prev.size() == 2) {
            for (String d : Hypr.names()) all.add(new Term.Suggestion(d, d, "", CYAN));
            return;
        }
        if (prev.size() != 3) return;
        String d = prev.get(2).toLowerCase(Locale.ROOT);
        String[] args;
        if (W_DIRECTIONS.contains(d)) args = new String[] {"l", "r", "u", "d"};
        else if (Objects.equals("changegroupactive", d)) args = new String[] {"f", "b"};
        else if (Objects.equals("windowmode", d)) args = new String[] {"toggle", "borderless", "maximize"};
        else if (Objects.equals("exec", d)) args = new String[] {com.mineux.core.MineUX.shellName()};
        else if (Objects.equals("resizeactive", d)) args = new String[] {"40 0", "-40 0", "0 40", "0 -40"};
        else if (W_WORKSPACE_ARGS.contains(d)) args = new String[] {"e+1", "e-1", "previous", "special"};
        else return;
        for (String a : args) all.add(new Term.Suggestion(a, a, "", FG));
        if (W_WORKSPACE_ARGS.contains(d)) {
            for (String n : Workstations.names()) all.add(new Term.Suggestion(n, n, String.valueOf(Workstations.slot(n)), MAGENTA));
        }
    }

    private void errors() {
        List<String> errors = HyprConf.get().errors;
        if (errors.isEmpty()) {
            shell.ok("no config errors");
            return;
        }
        for (String e : errors) shell.error(e);
    }
}
