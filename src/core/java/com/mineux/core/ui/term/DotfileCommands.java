package com.mineux.core.ui.term;

import com.mineux.core.greeter.Dotfile;
import com.mineux.core.greeter.LyConfig;
import com.mineux.core.ui.term.Term.Line;
import net.minecraft.util.Util;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.mineux.core.ui.term.Term.*;

/**
 * {@code dotfiles}: the files under {@code config/mineux/dotfiles}, read and edited from
 * the terminal. {@code dotfiles} lists them, {@code dotfiles ly} shows one,
 * {@code dotfiles ly stream_color=#ffaa00} sets a key, {@code dotfiles open} opens the folder.
 * <p>
 * Each dotfile checks its own values (see {@link LyConfig#set}); this only finds the file and
 * prints. For now there is the greeter's; the list grows as more of the client moves into
 * dotfiles.
 */
final class DotfileCommands {

    /** Every dotfile: its name here, its path under dotfiles/, and what it configures. */
    static final String[][] FILES = {
        {"ly", "ly/config.ini", "the greeter: animation, stream colour, tint, api"},
        {"hypr", HyprConf.FILE, "the window manager: gaps, borders, animations, layout, binds"},
    };

    private final Shell shell;

    DotfileCommands(Shell shell) {
        this.shell = shell;
    }

    void run(List<String> args) {
        if (args.isEmpty() || args.get(0).equalsIgnoreCase("ls")) {
            list();
            return;
        }

        String name = args.get(0).toLowerCase(Locale.ROOT);
        if (name.equals("open")) {
            Util.getPlatform().openPath(Dotfile.directory());
            shell.ok("opened " + Dotfile.directory());
            return;
        }
        if (name.equals("hypr") || name.equals("hyprland")) {
            hypr();
            return;
        }
        if (!name.equals("ly") && !name.equals("login")) {
            shell.error("dotfiles: no dotfile '" + args.get(0) + "' - ls to list them");
            return;
        }

        if (args.size() == 1) {
            show();
            return;
        }

        List<String> rest = args.subList(1, args.size());
        for (int i = 0; i < rest.size(); i++) {
            String arg = rest.get(i);
            int sep = Shell.firstSeparator(arg);
            String key, value;
            if (sep > 0) {
                key = arg.substring(0, sep);
                value = arg.substring(sep + 1);
            } else if (i + 1 < rest.size()) {
                key = arg;
                value = rest.get(++i);
            } else {
                shell.error("dotfiles: " + arg + " needs a value, e.g. " + arg + "=...");
                continue;
            }

            String error = LyConfig.set(key, value);
            if (error != null) {
                shell.error("dotfiles: " + error);
            } else {
                String canonical = LyConfig.canonicalKey(key);
                String now = LyConfig.file().read().getOrDefault(canonical, value);
                shell.line().t("> ", GREEN).t("ly", GOLD).t("/", DIM).t(canonical, FG).t(" = ", DIM).t(now, CYAN)
                    .t("   next time the greeter opens", FAINT);
            }
        }
    }

    private void list() {
        shell.line().t("dotfiles", GOLD).t(" - " + Dotfile.directory(), DIM);
        for (String[] file : FILES) {
            shell.line().t("  ").run(file[1], BLUE, "dotfiles " + file[0], "show it")
                .at(Shell.COL_DESC + 24).t(file[2], DIM);
        }
        shell.line().t("  ", DIM).run("dotfiles open", CYAN, "dotfiles open", "open the folder").t(" to edit them by hand", DIM);
    }

    /** hyprland.conf is sections and repeated lines rather than keys, so it is edited by hand. */
    private void hypr() {
        HyprConf conf = HyprConf.get();
        shell.line().t(HyprConf.FILE, GOLD).t(" - edit it by hand; a save applies at once", DIM);
        shell.line().t("  " + HyprConf.path(), FAINT);
        shell.line().t("  layout ", DIM).t(conf.layout, CYAN).t("  gaps ", DIM).t(conf.gapsIn + " " + conf.gapsOut, CYAN)
            .t("  border ", DIM).t(String.valueOf(conf.borderSize), CYAN).t("  binds ", DIM).t(String.valueOf(conf.binds.size()), CYAN)
            .t("  animations ", DIM).t(conf.animations ? "on" : "off", CYAN);
        shell.line().t("  ", DIM).run("dotfiles open", CYAN, "dotfiles open", "open the folder").t("  ", DIM)
            .run("hyprctl binds", CYAN, "hyprctl binds", "list the keys").t("  ", DIM)
            .run("hyprctl configerrors", conf.errors.isEmpty() ? CYAN : RED, "hyprctl configerrors", "what it got wrong")
            .t(conf.errors.isEmpty() ? "" : "  (" + conf.errors.size() + ")", RED);
    }

    private void show() {
        Map<String, String> values = LyConfig.file().read();
        shell.line().t("ly/config.ini", GOLD).t(" - the greeter; click a value to change it", DIM);
        String[] keys = LyConfig.KEYS;
        for (int i = 0; i + 1 < keys.length; i += 2) {
            String key = keys[i];
            String value = values.getOrDefault(key, "");
            Line line = shell.line().t("  " + key, FG).at(Shell.COL_DESC + 24);
            line.fill(value.isEmpty() ? "(default)" : value, CYAN, "dotfiles ly " + key + "=", "set " + key);
            if (key.equals("stream_color")) {
                Integer color = LyConfig.parseColor(value);
                if (color != null) line.t(" ###", 0xFF000000 | color);
            }
            line.at(Shell.COL_DESC + 150).t(keys[i + 1], FAINT);
        }
    }
}
