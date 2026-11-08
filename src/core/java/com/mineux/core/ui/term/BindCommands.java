package com.mineux.core.ui.term;

import java.util.ArrayList;
import java.util.List;

import static com.mineux.core.ui.term.Term.*;

/**
 * {@code bind} and {@code unbind}: keys that run commands in game ({@link Binds} fires them).
 */
final class BindCommands {

    private final Shell shell;

    BindCommands(Shell shell) {
        this.shell = shell;
    }

    void bind(List<String> args) {
        if (args.isEmpty()) {
            listBinds();
            return;
        }

        String key = Binds.canonical(args.get(0));
        if (key == null) {
            shell.error("bind: unknown key '" + args.get(0) + "' - a letter, a digit, f1-f25, mouse1-8, rshift...");
            return;
        }

        Workstations.Bind existing = findBind(key);
        if (args.size() == 1) {
            if (existing != null) {
                bindRow(existing);
            } else {
                shell.line().t("nothing on ", DIM).t(key, MAGENTA).t(" - ", DIM)
                    .fill("bind " + key + " \"<command>\"", CYAN, "bind " + key + " \"", null);
            }
            return;
        }

        String command = String.join(" ", args.subList(1, args.size())).strip();
        List<Workstations.Bind> binds = new ArrayList<>(List.of(Workstations.state().binds));
        binds.remove(existing);
        Workstations.Bind bind = new Workstations.Bind();
        bind.key = key;
        bind.command = command;
        binds.add(bind);
        Workstations.state().binds = binds.toArray(new Workstations.Bind[0]);
        Workstations.saveState();

        shell.line().t("> ", GREEN).t(key, MAGENTA).t(" runs ", DIM).t(command, FG)
            .t(existing != null ? "  (was " + existing.command + ")" : "", FAINT);
    }

    void unbind(List<String> args) {
        if (args.isEmpty()) {
            shell.error("usage: unbind <key>");
            return;
        }

        for (String arg : args) {
            String key = Binds.canonical(arg);
            Workstations.Bind existing = key == null ? null : findBind(key);
            if (existing == null) {
                shell.error("unbind: nothing is bound to '" + arg + "'");
                continue;
            }
            List<Workstations.Bind> binds = new ArrayList<>(List.of(Workstations.state().binds));
            binds.remove(existing);
            Workstations.state().binds = binds.toArray(new Workstations.Bind[0]);
            Workstations.saveState();
            shell.ok("unbound " + key + " (was " + existing.command + ")");
        }
    }

    private static Workstations.Bind findBind(String key) {
        for (Workstations.Bind bind : Workstations.state().binds) {
            if (key.equals(Binds.canonical(bind.key))) return bind;
        }
        return null;
    }

    void listBinds() {
        Workstations.Bind[] binds = Workstations.state().binds;
        if (binds.length == 0) {
            shell.line().t("nothing bound yet - e.g. ", DIM).fill("bind r \"toggle aim\"", CYAN, "bind r \"toggle aim\"", null);
            return;
        }
        shell.line().t("binds", GOLD).t(" - in game, with no screen open; click one to edit it", DIM);
        for (Workstations.Bind bind : binds) bindRow(bind);
    }

    void bindRow(Workstations.Bind bind) {
        String edit = "bind " + bind.key + " \"" + bind.command + "\"";
        shell.line().t("  ").t(bind.key, MAGENTA).at(Shell.COL_STATE).fill(bind.command, FG, edit, "edit it")
            .t("  ").run("[unbind]", RED, "unbind " + bind.key, "unbind " + bind.key);
    }

}
