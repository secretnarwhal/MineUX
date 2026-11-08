package com.mineux.core.ui.term;

import com.mineux.core.ui.term.Term.Suggestion;
import com.mineux.core.util.Words;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.mineux.core.ui.term.Term.*;

/**
 * Tab completion for muxsh: what could finish the word under the cursor.
 * <p>
 * Its own class rather than part of {@link Shell}, to keep that one small: the obfuscator
 * inflates every string and branch, and a class grown too big is written out unobfuscated
 * with signatures nothing else matches any more -- see {@code Shell#exec}.
 */
final class Completion {

    private final Shell shell;

    Completion(Shell shell) {
        this.shell = shell;
    }

    private static final Set<String> W_CD = Words.of("cd ls ll tree man cat gui");
    private static final Set<String> W_TOGGLE = Words.of("toggle t");
    private static final Set<String> W_WS = Words.of("ws workstation");
    private static final Set<String> W_ART = Words.of("art");
    private static final Set<String> W_BIND = Words.of("bind");
    private static final Set<String> W_ACCOUNTS = Words.of("accounts account acc alts");
    private static final Set<String> W_PLAY = Words.of("play");
    private static final Set<String> W_JOIN = Words.of("join connect");
    private static final Set<String> W_SERVERS = Words.of("servers server");
    private static final Set<String> W_VANILLA = Words.of("vanilla");
    private static final Set<String> W_DOTFILES = Words.of("dotfiles dotfile dots");
    private static final Set<String> W_UNBIND = Words.of("unbind");
    private static final Set<String> W_HYPRCTL = Words.of("hyprctl hypr");

    /**
     * What could finish the word under the cursor. {@code before} is everything typed up to
     * the start of that word; {@code token} is the word so far.
     */
    List<Suggestion> complete(String before, String token) {
        // Only the last command of a chain is being typed.
        List<String> chained = Shell.chain(before);
        before = chained.get(chained.size() - 1);

        List<String> prev = Shell.tokenize(before);
        List<Suggestion> all = new ArrayList<>();

        if (prev.isEmpty()) {
            firstWord(token, all);
        } else {
            String cmd = prev.get(0).toLowerCase(Locale.ROOT);
            if (ExtCommands.replacing(cmd) != null || com.mineux.core.DeskExtensions.hiddenBuiltIns().contains(cmd)) {
                List<Suggestion> inner = extension(cmd, prev, token, all);
                if (inner != null) return inner;
            } else if (W_CD.contains(cmd)) {
                if (prev.size() == 1) paths(token, all, false);
            } else if (W_TOGGLE.contains(cmd)) {
                paths(token, all, true);
            } else if (W_WS.contains(cmd)) {
                if (prev.size() == 1) {
                    for (String s : Shell.WS_SUBS) all.add(new Suggestion(s, s, "", CYAN));
                } else if (prev.size() == 2 && !prev.get(1).equals("new")) {
                    for (String n : Workstations.names()) all.add(new Suggestion(n, n, n.equals(Workstations.current()) ? "open" : "", MAGENTA));
                }
            } else if (W_ART.contains(cmd)) {
                if (prev.size() == 1) {
                    for (String s : ArtCommands.SUBS) all.add(new Suggestion(s, s, "", CYAN));
                    for (String n : shell.artNames()) all.add(new Suggestion(n, n, "", GOLD));
                } else if (prev.size() == 2 && prev.get(1).equals("palette")) {
                    for (String p : ArtView.PALETTES) all.add(new Suggestion(p, p, "", ArtView.swatch(p)));
                } else if (prev.size() == 2 && (prev.get(1).equals("glitch") || prev.get(1).equals("motion"))) {
                    all.add(new Suggestion("on", "on", "", GREEN));
                    all.add(new Suggestion("off", "off", "", DIM));
                }
            } else if (W_BIND.contains(cmd)) {
                if (prev.size() == 1) {
                    keys(all);
                } else {
                    // The command being bound completes as though it were typed on its own.
                    String rest = afterTokens(before, 2).stripLeading();
                    if (rest.startsWith("\"")) rest = rest.substring(1);
                    boolean quote = token.startsWith("\"");
                    List<Suggestion> inner = complete(rest, quote ? token.substring(1) : token);
                    if (!quote) return inner;
                    List<Suggestion> quoted = new ArrayList<>();
                    for (Suggestion x : inner) quoted.add(new Suggestion("\"" + x.insert(), x.label(), x.note(), x.color()));
                    return quoted;
                }
            } else if (W_ACCOUNTS.contains(cmd)) {
                accountArgs(prev, all);
            } else if (W_PLAY.contains(cmd)) {
                if (prev.size() == 1) {
                    java.util.List<net.minecraft.world.level.storage.LevelSummary> worlds = Worlds.list();
                    for (int i = 0; i < worlds.size(); i++) {
                        String name = worlds.get(i).getLevelName();
                        // By name where it is one word, by number where a name has spaces in it.
                        all.add(new Suggestion(name.contains(" ") ? String.valueOf(i + 1) : name, (i + 1) + " " + name, Worlds.mode(worlds.get(i)), GREEN));
                    }
                }
            } else if (W_JOIN.contains(cmd)) {
                if (prev.size() == 1) {
                    // Pinged as soon as they are offered, so each one's icon turns up beside it.
                    Servers.pingUnpinged();
                    java.util.List<net.minecraft.client.multiplayer.ServerData> servers = Servers.all();
                    for (int i = 0; i < servers.size(); i++) {
                        String name = servers.get(i).name;
                        all.add(new Suggestion(name.contains(" ") ? String.valueOf(i + 1) : name, (i + 1) + " " + name,
                            servers.get(i).ip, GREEN, servers.get(i)));
                    }
                }
            } else if (W_SERVERS.contains(cmd)) {
                if (prev.size() == 1) for (String sub : new String[] {"add", "rm", "refresh"}) all.add(new Suggestion(sub, sub, "", CYAN));
            } else if (W_VANILLA.contains(cmd)) {
                MenuButtons menu = shell.menu();
                if (prev.size() == 1 && menu != null) {
                    for (MenuButtons.Entry entry : menu.entries()) all.add(new Suggestion(entry));
                }
            } else if (W_DOTFILES.contains(cmd)) {
                if (prev.size() == 1) {
                    for (String[] f : DotfileCommands.FILES) all.add(new Suggestion(f[0], f[0], f[2], BLUE));
                    all.add(new Suggestion("open", "open", "open the folder", CYAN));
                } else if (prev.get(1).equalsIgnoreCase("ly")) {
                    String[] keys = com.mineux.core.greeter.LyConfig.KEYS;
                    for (int i = 0; i + 1 < keys.length; i += 2) all.add(new Suggestion(keys[i] + "=", keys[i], keys[i + 1], FG));
                }
            } else if (DiscordCommands.completesPlayers(cmd) || LookupCommands.completesPlayers(cmd)) {
                if (prev.size() == 1) DiscordCommands.players(all);
            } else if (W_HYPRCTL.contains(cmd)) {
                HyprCommands.complete(prev, all);
            } else if (W_UNBIND.contains(cmd)) {
                for (Workstations.Bind bind : Workstations.state().binds) {
                    all.add(new Suggestion(bind.key, bind.key, bind.command, MAGENTA));
                }
            } else if (ExtCommands.command(cmd) != null || ExtCommands.program(cmd) != null) {
                List<Suggestion> inner = extension(cmd, prev, token, all);
                if (inner != null) return inner;
            } else {
                Node node = Shell.isAssignment(cmd) || shell.cwd().setting(cmd) != null || Setting.parseBool(cmd, false) != null
                    ? shell.cwd() : shell.resolve(prev.get(0));
                if (node != null) settingArgs(node, token, all);
            }
        }

        return filter(all, token);
    }

    void firstWord(String token, List<Suggestion> all) {
        if (token.contains("/")) {
            paths(token, all, false);
            return;
        }

        if (Shell.firstSeparator(token) > 0) {
            settingArgs(shell.cwd(), token, all);
            return;
        }

        MenuButtons menu = shell.menu();
        if (menu != null) {
            for (MenuButtons.Entry entry : menu.entries()) all.add(new Suggestion(entry));
        }

        for (Node child : shell.cwd().children) all.add(new Suggestion(child.name, child.name + "/", child.desc, BLUE));
        if (shell.cwd().toggle != null) {
            all.add(new Suggestion("on", "on", "switch " + shell.cwd().name + " on", GREEN));
            all.add(new Suggestion("off", "off", "switch " + shell.cwd().name + " off", RED));
        }
        settingKeys(shell.cwd(), token, all, "");
        for (String[] c : ExtCommands.builtInRows()) all.add(new Suggestion(c[0], c[0], c[1], CYAN));
        for (com.mineux.core.ShellCommand c : com.mineux.core.Extensions.commands()) {
            all.add(new Suggestion(c.name(), c.name(), c.help(), CYAN));
            for (String alias : c.aliases()) all.add(new Suggestion(alias, alias, c.name() + ": " + c.help(), CYAN));
        }
        for (com.mineux.core.TermProgram.Type p : com.mineux.core.DeskExtensions.programs()) {
            all.add(new Suggestion(p.name(), p.name(), p.help(), CYAN));
            for (String alias : p.aliases()) all.add(new Suggestion(alias, alias, p.name() + ": " + p.help(), CYAN));
        }
        if (shell.cwd().parent != null) all.add(new Suggestion("..", "..", "up", BLUE));

        List<Node> nodes = new ArrayList<>();
        Modules.root().walk(nodes);
        for (Node n : nodes) {
            if (n.parent != shell.cwd()) all.add(new Suggestion(n.name, n.name, n.desc, GOLD));
        }
    }

    /**
     * An extension's command or program after its name: its own completions
     * ({@link com.mineux.core.ShellCommand#complete}). One that throws completes nothing.
     *
     * @return what the rest of the line completes to, when the command said it is a command of its own; null to use {@code all}
     */
    private List<Suggestion> extension(String cmd, List<String> prev, String token, List<Suggestion> all) {
        Out out = new Out(all, token);
        try {
            com.mineux.core.ShellCommand command = ExtCommands.command(cmd);
            if (command != null) command.complete(java.util.Collections.unmodifiableList(prev), token, out);
            else {
                com.mineux.core.TermProgram.Type type = ExtCommands.program(cmd);
                if (type != null) type.complete(java.util.Collections.unmodifiableList(prev), token, out);
            }
        } catch (RuntimeException | LinkageError e) {
            com.mineux.core.MineUX.LOGGER.warn("{}: completing {} failed", com.mineux.core.MineUX.shellName(), cmd, e);
            all.clear();
            return null;
        }
        return out.inner == null ? null : complete(out.inner, token);
    }

    /** What an extension's completion writes into: this completion's list. */
    private final class Out extends com.mineux.core.CompletionOut {
        private final List<Suggestion> all;
        private final String token;
        String inner;

        Out(List<Suggestion> all, String token) {
            this.all = all;
            this.token = token;
        }

        @Override
        public void modules(boolean switchesOnly) {
            paths(token, all, switchesOnly);
        }

        @Override
        public void add(String insert, String label, String note, int color) {
            if (insert != null) all.add(new Suggestion(insert, label == null ? insert : label, note == null ? "" : note, color));
        }

        @Override
        public void asCommand(String line) {
            inner = line == null ? "" : line;
        }

        @Override
        public void keys() {
            Completion.keys(all);
        }
    }

    /** Subcommands as {@code name, note} pairs. */
    private static void subs(List<Suggestion> all, String[] pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) all.add(new Suggestion(pairs[i], pairs[i], pairs[i + 1], CYAN));
    }

    /** {@code accounts}: what to do, then which account. */
    private static void accountArgs(List<String> prev, List<Suggestion> all) {
        if (prev.size() == 1) {
            all.add(new Suggestion("ls", "ls", "the saved accounts", CYAN));
            all.add(new Suggestion("login", "login", "sign in to a microsoft account in your browser", CYAN));
            all.add(new Suggestion("use", "use", "play as one", CYAN));
            all.add(new Suggestion("add", "add", "<name> offline, or <name> <token> (refresh or session)", CYAN));
            all.add(new Suggestion("rm", "rm", "forget one", CYAN));
        }
        if (prev.size() == 1 || (prev.size() == 2 && !prev.get(1).equalsIgnoreCase("add") && !prev.get(1).equalsIgnoreCase("login"))) {
            for (com.mineux.core.mcaccount.Account account : com.mineux.core.mcaccount.AccountsConfig.get().accounts()) {
                all.add(new Suggestion(account.displayName(), account.displayName(), account.kind(), GREEN));
            }
        }
    }

    /** Keys for {@code bind}: the ones already bound, with what they run, then the usual ones. */
    static void keys(List<Suggestion> all) {
        for (Workstations.Bind bind : Workstations.state().binds) {
            all.add(new Suggestion(bind.key, bind.key, "now: " + bind.command, MAGENTA));
        }
        for (String k : Setting.KEY_SUGGESTIONS) {
            if (!k.equals("none")) all.add(new Suggestion(k, k, "", MAGENTA));
        }
    }

    /** What is left of a line after its first {@code count} words, a quoted phrase counting as one. */
    private static String afterTokens(String text, int count) {
        int i = 0, n = text.length();
        for (int t = 0; t < count; t++) {
            while (i < n && Character.isWhitespace(text.charAt(i))) i++;
            boolean quoted = false;
            while (i < n && (quoted || !Character.isWhitespace(text.charAt(i)))) {
                if (text.charAt(i) == '"') quoted = !quoted;
                i++;
            }
        }
        return text.substring(i);
    }

    void settingArgs(Node node, String token, List<Suggestion> all) {
        int sep = Shell.firstSeparator(token);
        if (sep > 0) {
            Setting s = node.setting(token.substring(0, sep));
            if (s != null) values(s, token.substring(0, sep + 1), token.substring(sep + 1), all);
            return;
        }

        if (node.toggle != null) {
            all.add(new Suggestion("on", "on", "switch on", GREEN));
            all.add(new Suggestion("off", "off", "switch off", RED));
        }
        settingKeys(node, token, all, "");
    }

    static void settingKeys(Node node, String token, List<Suggestion> all, String prefix) {
        for (Setting s : node.settings) {
            all.add(new Suggestion(prefix + s.key + ":", s.key, s.display() + "  " + s.desc, FG));
        }
    }

    static void values(Setting s, String prefix, String typed, List<Suggestion> all) {
        switch (s.kind()) {
            case Setting.BOOL -> {
                all.add(new Suggestion(prefix + "on", "on", s.bool() ? "current" : "", GREEN));
                all.add(new Suggestion(prefix + "off", "off", s.bool() ? "" : "current", RED));
            }
            case Setting.KEY -> {
                for (String k : Setting.KEY_SUGGESTIONS) all.add(new Suggestion(prefix + k, k, "", MAGENTA));
            }
            case Setting.STRING -> {
                if (s.options != null) {
                    for (String o : s.slugs()) all.add(new Suggestion(prefix + o, o, o.equals(s.display()) ? "current" : "", AMBER));
                    // A hidden choice is only suggested while it is the one set.
                    String now = s.hiddenNow();
                    if (now != null) all.add(new Suggestion(prefix + Setting.slug(now), Setting.slug(now), "current (no longer offered)", AMBER));
                } else {
                    all.add(new Suggestion(prefix + s.display(), s.display(), "current", AMBER));
                }
            }
            default -> all.add(new Suggestion(prefix + s.display(), s.display(), "now  " + s.hint(), CYAN));
        }
    }

    void paths(String token, List<Suggestion> all, boolean togglesOnly) {
        int slash = token.lastIndexOf('/');
        String dirPart = slash >= 0 ? token.substring(0, slash + 1) : "";
        Node base = slash >= 0 ? (dirPart.equals("/") || dirPart.equals("~/") ? Modules.root() : shell.resolve(dirPart.substring(0, dirPart.length() - 1))) : shell.cwd();

        if (base != null) {
            for (Node child : base.children) {
                if (togglesOnly && child.toggle == null && child.children.isEmpty()) continue;
                all.add(new Suggestion(dirPart + child.name, child.name + "/", child.desc, BLUE));
            }
            if (slash < 0 && base.parent != null && !togglesOnly) all.add(new Suggestion("..", "..", "up", BLUE));
        }

        if (slash < 0) {
            List<Node> nodes = new ArrayList<>();
            Modules.root().walk(nodes);
            for (Node n : nodes) {
                if (n.parent == base) continue;
                if (togglesOnly && n.toggle == null) continue;
                all.add(new Suggestion(n.name, n.name, n.desc, GOLD));
            }
        }
    }

    private static List<Suggestion> filter(List<Suggestion> all, String token) {
        String t = token.toLowerCase(Locale.ROOT);
        List<Suggestion> starts = new ArrayList<>();
        List<Suggestion> contains = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (Suggestion s : all) {
            String insert = s.insert().toLowerCase(Locale.ROOT);
            if (!seen.add(insert)) continue;
            if (insert.equals(t) && !insert.endsWith(":")) continue;
            if (insert.startsWith(t)) starts.add(s);
            else if (t.length() >= 2 && insert.contains(t)) contains.add(s);
        }

        starts.addAll(contains);
        return starts;
    }
}
