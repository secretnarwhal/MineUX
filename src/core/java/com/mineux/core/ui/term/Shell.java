package com.mineux.core.ui.term;

import com.mineux.core.CommandOut;
import com.mineux.core.DeskExtensions;
import com.mineux.core.Extensions;
import com.mineux.core.MineUX;
import com.mineux.core.ShellCommand;
import com.mineux.core.TermProgram;
import com.mineux.core.ui.term.Term.Line;
import com.mineux.core.ui.term.Term.Suggestion;
import com.mineux.core.util.Words;
import net.minecraft.client.Minecraft;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static com.mineux.core.ui.term.Term.*;

/**
 * muxsh: the terminal's command language.
 * <p>
 * Every switch and setting of the module tree, typed: {@code rpc on}, {@code rpc server:off},
 * {@code cd friends}, {@code range:3-3.2}. A module name works from anywhere -- the tree is
 * only there for {@code cd} and {@code ls} to walk -- and a bare {@code key:value} applies to
 * whichever module the prompt is sitting in. Commands and programs an extension added
 * ({@link Extensions#addCommand}, {@link DeskExtensions#addProgram}) are typed the same way.
 */
final class Shell {

    /** What the shell asks of the screen it is drawn on. */
    interface Host {
        /** {@code exit}: close this terminal. */
        void exit();
        /** {@code close}: close the whole desktop. */
        void quit();
        void art(String arg);
        String artName();
        String[] artNames();
        /** Takes over this terminal with a full-window program until a key is pressed. */
        void launch(String app);

        /** Whether {@link #launch} does anything here: a terminal on the desk, not a keybind's shell. */
        default boolean launches() {
            return false;
        }
        void workstationsChanged();

        /**
         * Asks a yes/no question in a floating window of its own ({@link Prompt}); yes runs
         * {@code onYes}. Nothing where there is no screen to ask on.
         */
        default void confirm(String question, String detail, String yes, Runnable onYes) {
        }

        /** The main menu's buttons, when this shell is the main menu's terminal; null anywhere else. */
        default MenuButtons menu() {
            return null;
        }

        /** Opens the main menu's worlds or servers window; false anywhere else. */
        default boolean openWindow(String kind) {
            return false;
        }

        /** {@code hyprctl dispatch}: null when it ran, or why it did not. */
        default String hypr(String dispatcher, String arg) {
            return "hyprctl: only on the desktop";
        }
    }

    static final String APP_PIPES = "pipes", APP_BONSAI = "bonsai", APP_IRC = "irc", APP_FRIENDS = "friends";
    /** What an extension's program is launched as: this, then its name, then {@code :} and its argument. */
    static final String APP_EXT = "ext:";

    /** The modifier the window manager's shortcuts hang off: {@code alt}, {@code ctrl}, {@code win} or {@code shift}. */
    static final String[] SUPER_KEYS = {"alt", "ctrl", "win", "shift"};

    static final int COL_STATE = 78;
    static final int COL_DESC = 116;
    static final int COL_VALUE = 72;
    /** Room for a value like {@code 1000-2130} before the slider beside it. */
    private static final int VALUE_W = 56;
    private static final int SLIDER_W = 100;

    static final String[][] COMMANDS = {
        {"help", "list commands"},
        {"ls", "list a module's settings"},
        {"cd", "enter a module"},
        {"toggle", "list or flip module switches"},
        {"bind", "run a command on a key: bind r \"toggle rpc\""},
        {"unbind", "unbind <key>"},
        {"open", "a module's settings window: open rpc"},
        {"gui", "what typing a module's name or clicking a topbar icon opens: gui commands|topbar window|text"},
        {"ws", "workstations (config profiles)"},
        {"art", "the desktop art: next, list (all of it), palette, folder"},
        {"lookup", "a player in a window: mcpvp and minemen stats, rating cards, graphs, friends, discord: lookup <player>"},
        {"discord", "a player's discord id, from subtiers or mctiers: discord <player>"},
        {"rdiscord", "the player linked to a discord id: rdiscord <id>"},
        {"tree", "the whole module tree"},
        {"pipes.sh", "run pipes.sh in this terminal"},
        {"bonsai", "grow a bonsai in this terminal"},
        {"super", "set the window manager key"},
        {"keys", "window manager shortcuts"},
        {"hyprctl", "the window manager: dispatch, reload, keyword, binds"},
        {"man", "a module in full"},
        {"pwd", "where you are"},
        {"clear", "clear the screen"},
        {"history", "commands you ran"},
        {"neofetch", "system info"},
        {"whoami", "your minecraft account and your MineUX account (or guest)"},
        {"worlds", "singleplayer worlds (home desk)"},
        {"play", "play a world: play <number|name>"},
        {"servers", "multiplayer servers (home desk)"},
        {"join", "join a server: join <number|name|address>"},
        {"disconnect", "leave the world or server"},
        {"vanilla", "a home desk button as vanilla has it"},
        {"dotfiles", "the config files: the greeter, the window manager and more"},
        {"accounts", "your minecraft accounts: login, switch, add, remove"},
        {"friends", "your friends: who is online, what they are playing, and their settings; friends window opens the list, friends open <who> their profile; friends on / friends off is the only switch for the whole feature"},
        {"notifications", "the bell's list: messages and the changelog; notifications read | clear | list"},
        {"changelog", "post into everyone's bell (admins): changelog <title> | <body>, \\n for a new line, quote a ;"},
        {"msg", "write to a friend: msg <who> <message>"},
        {"say", "say something in the server's chat, or run a /command: bind g say gg"},
        {"irc", "the chatroom on mineux.com and your conversations with friends, a tab each, in this terminal; ctrl+c quits: irc [room|@friend]"},
        {"dm", "open a conversation with a friend in irc: dm <friend>; with a message after the name, just send it"},
        {"login", "sign in to a MineUX account: opens the greeter"},
        {"logout", "sign out of your MineUX account and open the greeter"},
        {"exit", "close this terminal"},
        {"close", "close the desktop"},
        {"quit", "exit minecraft (asks first)"},
        {"shutdown", "exit minecraft (asks first)"},
        {"reboot", "restart minecraft (asks first)"},
    };

    static final String[] WS_SUBS = {"ls", "new", "open", "rm", "mv", "save"};

    final List<Line> out = new ArrayList<>();
    private Host host;
    private Node cwd;
    private Node lastDir;
    private final Completion completion = new Completion(this);
    private final BindCommands binds = new BindCommands(this);
    private final ShellInfo info = new ShellInfo(this);
    private final AccountCommands accounts = new AccountCommands(this);
    private final DotfileCommands dotfiles = new DotfileCommands(this);
    private final ArtCommands art = new ArtCommands(this);
    private final PlayCommands play = new PlayCommands(this);
    private final DiscordCommands discord = new DiscordCommands(this);
    private final LookupCommands lookup = new LookupCommands(this);
    private final HyprCommands hyprctl = new HyprCommands(this);
    private final FriendCommands friends = new FriendCommands(this);
    private final NoticeCommands notices = new NoticeCommands(this);

    Shell(Host host) {
        this.host = host;
        this.cwd = Modules.root();
    }

    /** The shell outlives any one screen; each new one takes it over. */
    void setHost(Host host) {
        this.host = host;
    }

    /** Ctrl+C: the line as typed, marked abandoned, not run. */
    void cancel(String input) {
        prompt(line()).t(input, FG).t("^C", DIM);
    }

    Node cwd() {
        return cwd;
    }

    /** The main menu's buttons, when this is the main menu's terminal; null anywhere else. */
    MenuButtons menu() {
        return host.menu();
    }

    boolean openWindow(String kind) {
        return host.openWindow(kind);
    }

    String hypr(String dispatcher, String arg) {
        return host.hypr(dispatcher, arg);
    }

    /**
     * Takes over this terminal with a program ({@code irc:@friend} for one on a tab).
     *
     * @return false where there is no terminal to take over: a keybind's shell, a friend's command
     */
    boolean launch(String app) {
        if (!host.launches()) return false;
        host.launch(app);
        return true;
    }

    /** A yes/no question in a floating window; nothing happens where there is no screen to ask on. */
    void confirm(String question, String detail, String yes, Runnable onYes) {
        host.confirm(question, detail, yes, onYes);
    }

    // ---- output helpers -------------------------------------------------

    Line line() {
        Line line = new Line();
        out.add(line);
        while (out.size() > 600) out.remove(0);
        return line;
    }

    void print(String text, int color) {
        line().t(text, color);
    }

    void error(String text) {
        line().t("! ", RED).t(text, RED);
    }

    void ok(String text) {
        line().t("> ", GREEN).t(text, FG);
    }

    /**
     * The last line of the main menu terminal's header -- the art, the fetch and the welcome --
     * which {@code clear} leaves in place there. Null anywhere else.
     */
    private Line header;

    /** On the main menu, what {@code clear} leaves behind until {@code ls} is run; null otherwise. */
    private Line lsHint;

    /** Clears the screen; on the main menu, everything after the welcome line, and hints at ls. */
    void clear() {
        lsHint = null;
        int keep = header == null ? -1 : out.indexOf(header);
        if (keep < 0) {
            out.clear();
            return;
        }
        out.subList(keep + 1, out.size()).clear();
        if (menu() != null && !menu().pause()) {
            line();
            // ls glows: a slow pulse between its blue and white.
            lsHint = line().t("hint: run ", DIM).live(() -> "ls", () -> {
                double pulse = (Math.sin(System.currentTimeMillis() / 320.0) + 1) / 2;
                return Term.mix(CYAN, 0xFFFFFFFF, (float) (pulse * 0.55));
            }, "ls", "list everything there is to type");
        }
    }

    /** Takes the hint {@code clear} left, now that ls has been run. */
    private void dropLsHint() {
        if (lsHint == null) return;
        int at = out.indexOf(lsHint);
        // The blank line above it goes too.
        if (at > 0 && out.get(at - 1).spans.isEmpty()) out.subList(at - 1, at + 1).clear();
        else out.remove(lsHint);
        lsHint = null;
    }

    String user() {
        String name = null;
        try {
            name = Minecraft.getInstance().getUser().getName();
        } catch (RuntimeException ignored) {
        }
        return name == null || name.isBlank() ? "anon" : name.toLowerCase(Locale.ROOT);
    }

    /** The prompt, as spans: {@code user@mineux:~/rpc$ }. */
    Line prompt(Line line) {
        return line.t(user(), GREEN).t("@", DIM).t(brand(), GOLD).t(":", DIM).t(cwd.path(), CYAN).t("$ ", FG);
    }

    /** The host's brand, as the prompt and the motd show it: {@code mineux}. */
    static String brand() {
        return MineUX.ready() ? MineUX.host().brand() : "mineux";
    }

    /** Rows of scrollback the word art at the top of every terminal takes. */
    static final int ART_ROWS = 10;
    /** On the main menu, where the fetch sits beside the art and needs the height. */
    static final int MENU_ART_ROWS = 14;

    /** Whether the tips have been printed yet this game session; only the first terminal gets them. */
    private static boolean tipsShown;

    /** What a new terminal opens on: the word art, a welcome, and -- once a session -- the tips. */
    void motd() {
        boolean menu = menu() != null;
        Line art = line();
        art.artRows = menu ? MENU_ART_ROWS : ART_ROWS;
        art.fetch = menu;
        for (int i = 1; i < art.artRows; i++) line();

        long born = System.currentTimeMillis();
        String welcome = "welcome to " + brand() + ", ";
        String who = user();
        Line hello = line();
        hello.live(() -> welcome.substring(0, (int) Math.min(welcome.length(), (System.currentTimeMillis() - born) / 35)),
            () -> DIM, null, null);
        hello.live(() -> {
            long typed = (System.currentTimeMillis() - born) / 35 - welcome.length();
            return typed <= 0 ? "" : who.substring(0, (int) Math.min(who.length(), typed));
        }, () -> GREEN, null, null);

        if (menu) header = hello;

        if (menu() != null && menu().pause()) {
            line();
            line().t(brand() + " ", GOLD).t("esc menu - type a button or click one; ", DIM)
                .fill("resume", CYAN, "resume", "back to the game").t(" or esc goes back to the game", DIM);
        } else if (menu() != null) {
            line();
            line().t(brand() + " ", GOLD).t("home - type a command or click one; ", DIM)
                .fill("help", CYAN, "help", "run help").t(" explains them", DIM);
            line().t(superKey() + "+drag", CYAN).t(" moves this terminal, ", DIM).t(superKey() + "+right drag", CYAN)
                .t(" resizes it, ", DIM).t(superKey() + "+f", CYAN).t(" floats it", DIM);
        } else if (!tipsShown) {
            tipsShown = true;
            line();
            line().t(brand() + " ", GOLD).t("terminal - type ", DIM).fill("help", CYAN, "help", "run help")
                .t(" or click anything that glows", DIM);
            line().t("try ", DIM).fill("toggle", CYAN, "toggle", "every module switch")
                .t("  ", DIM).fill("ls", CYAN, "ls", "list modules")
                .t("  ", DIM).fill("neofetch", CYAN, "neofetch", "system info")
                .t("  ", DIM).fill("bonsai", CYAN, "bonsai", "grow a tree");
            line().t("workstation ", DIM).t(Workstations.current(), MAGENTA).t(" - tab completes, up/down for history", DIM);
            String vulkan = Modules.vulkanNote();
            if (vulkan != null) line().t("vulkan", GOLD).t(" - " + vulkan, DIM);
            line().t(superKey() + "+space", CYAN).t(" another terminal, ", DIM).fill("keys", CYAN, "keys", "window manager shortcuts")
                .t(" for the rest", DIM);
        }
        line();
    }

    // ---- running --------------------------------------------------------

    /** The echo line of the last command run, the command, and how many lines it and its output took. */
    private Line lastEcho;
    private String lastCommand;
    private int lastBlock;

    void run(String input) {
        run(input, true);
    }

    /** @param record whether it goes into the history, which a command nobody typed does not */
    void run(String input, boolean record) {
        String trimmed = input.trim();
        // What is shown and kept of it: an account's token is neither.
        String shown = AccountCommands.redact(trimmed);
        boolean secret = !shown.equals(trimmed);

        // The same command again, straight after itself: its old echo and output go, so
        // toggling aim three times reads as the one line that is now true.
        if (!trimmed.isEmpty() && trimmed.equals(lastCommand) && lastEcho != null) {
            int at = out.lastIndexOf(lastEcho);
            if (at >= 0 && out.size() - at == lastBlock) out.subList(at, out.size()).clear();
        }

        Line echo = prompt(line());
        echo.t(secret ? shown : input, FG);
        lastEcho = null;
        lastCommand = null;

        if (trimmed.isEmpty()) return;

        List<String> history = Workstations.state().history;
        if (record && (history.isEmpty() || !history.get(history.size() - 1).equals(shown))) {
            history.add(shown);
            Workstations.saveState();
        }

        for (String part : chain(trimmed)) {
            if (!part.isBlank()) exec(tokenize(part));
        }

        int at = out.lastIndexOf(echo);
        if (at >= 0) {
            lastEcho = echo;
            lastCommand = trimmed;
            lastBlock = out.size() - at;
        }
    }

    /** Runs a command and prints nothing: a click on a switch, which already shows its new state. */
    void quiet(String command) {
        int before = out.size();
        click(command);
        while (out.size() > before) out.remove(out.size() - 1);
    }

    /** Runs a command a click asked for, without echoing a prompt line for it. */
    void click(String command) {
        for (String part : chain(command)) {
            if (!part.isBlank()) exec(tokenize(part));
        }
    }

    /**
     * A line split into its commands at {@code ;} and {@code &&} -- outside quotes, so
     * {@code bind r "toggle aim; toggle mace"} is one command binding two.
     */
    static List<String> chain(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') quoted = !quoted;
            if (!quoted && c == ';') {
                parts.add(current.toString());
                current.setLength(0);
            } else if (!quoted && c == '&' && i + 1 < text.length() && text.charAt(i + 1) == '&') {
                parts.add(current.toString());
                current.setLength(0);
                i++;
            } else {
                current.append(c);
            }
        }

        parts.add(current.toString());
        return parts;
    }

    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean any = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                any = true;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (any) tokens.add(current.toString());
                current.setLength(0);
                any = false;
            } else {
                current.append(c);
                any = true;
            }
        }

        if (any) tokens.add(current.toString());
        return tokens;
    }

    /**
     * Runs one command.
     * <p>
     * Split into a few small dispatchers rather than one switch over every name: the
     * obfuscator turns each string case into a block of decryption code, and one switch over
     * all of them went past the JVM's 64KB method limit. A class that does that is written out
     * unobfuscated, with its old signatures, while every caller is rewritten to pass the
     * obfuscator's extra argument -- so the first call into it throws NoSuchMethodError.
     * Keep each of these to a couple of dozen names.
     */
    private void exec(List<String> t) {
        exec(t, true);
    }

    /**
     * @param extensions whether an extension's command that replaces a built-in
     *                   ({@link ShellCommand#replacesBuiltIn}) may take this word: false when the
     *                   extension itself handed it back ({@link CommandOut#builtIn})
     */
    private void exec(List<String> t, boolean extensions) {
        if (t.isEmpty()) return;

        String cmd = t.get(0).toLowerCase(Locale.ROOT);
        List<String> args = t.subList(1, t.size());

        ShellCommand replaced = extensions ? ExtCommands.replacing(cmd) : null;
        if (replaced != null) {
            runExtension(replaced, t.get(0), args);
            return;
        }
        if (DeskExtensions.hiddenBuiltIns().contains(cmd)) {
            if (!extension(cmd, args)) fallback(t);
            return;
        }

        if (!power(cmd) && !menuButton(cmd) && !files(cmd, args) && !settings(cmd, args) && !desktop(cmd, args) && !misc(cmd, args)
            && !account(cmd, args) && !play.run(cmd, args) && !discord.run(cmd, args) && !lookup.run(cmd, args)
            && !friends.run(cmd, args) && !notices.run(cmd, args) && !extension(cmd, args)) {
            fallback(t);
        }
    }

    /**
     * A command or a program an extension added ({@link Extensions#addCommand},
     * {@link DeskExtensions#addProgram}), by its name. A command that throws is an error line,
     * never a broken terminal.
     */
    private boolean extension(String cmd, List<String> args) {
        ShellCommand command = ExtCommands.command(cmd);
        if (command != null) {
            runExtension(command, cmd, args);
            return true;
        }
        TermProgram.Type type = ExtCommands.program(cmd);
        if (type != null) {
            if (!host.launches()) error(cmd + ": open it in a terminal window");
            else host.launch(APP_EXT + type.name() + ":" + String.join(" ", args));
            return true;
        }
        return false;
    }

    /** An extension's command, typed as {@code typed}; one that throws is an error line, never a broken terminal. */
    private void runExtension(ShellCommand command, String typed, List<String> args) {
        try {
            command.run(typed.toLowerCase(Locale.ROOT), args.toArray(new String[0]), new Out(this));
        } catch (RuntimeException | LinkageError e) {
            MineUX.LOGGER.warn("{}: {} failed", MineUX.shellName(), typed, e);
            error(typed + ": failed - " + e);
        }
    }

    /** {@link CommandOut#launch}: an extension's program in this terminal; false where there is none to take over. */
    boolean launchProgram(String program, String arg) {
        TermProgram.Type type = ExtCommands.program(program);
        if (type == null || !host.launches()) return false;
        host.launch(APP_EXT + type.name() + ":" + (arg == null ? "" : arg));
        return true;
    }

    /** {@link CommandOut#tab}: the module's {@code tab=}, or its nearest parent's; -1 for none. */
    int tabOf(String module) {
        Node node = module == null || module.isBlank() ? cwd : resolve(module);
        while (node != null && node.tab < 0) node = node.parent;
        return node == null ? -1 : node.tab;
    }

    /** What an extension's command writes through: this shell's scrollback. */
    static final class Out extends CommandOut {
        private final Shell shell;

        Out(Shell shell) {
            this.shell = shell;
        }

        @Override
        public void println(String line) {
            shell.print(line, FG);
        }

        @Override
        public void error(String line) {
            shell.error(line);
        }

        @Override
        public void ok(String line) {
            shell.ok(line);
        }

        @Override
        public void print(String line, int color) {
            shell.print(line, color);
        }

        @Override
        public Line line() {
            return shell.line();
        }

        @Override
        public void run(String command) {
            shell.click(command);
        }

        @Override
        public void builtIn(List<String> words) {
            if (words != null && !words.isEmpty()) shell.exec(new ArrayList<>(words), false);
        }

        @Override
        public void module(List<String> words) {
            if (words != null && !words.isEmpty()) shell.fallback(new ArrayList<>(words));
        }

        @Override
        public boolean launch(String program, String arg) {
            return shell.launchProgram(program, arg);
        }

        @Override
        public boolean openWindow(String kind) {
            return shell.openWindow(kind);
        }

        @Override
        public void confirm(String question, String detail, String yes, Runnable onYes) {
            shell.confirm(question, detail, yes, onYes);
        }

        @Override
        public int tab(String module) {
            return shell.tabOf(module);
        }
    }

    private static final Set<String> W_CLOSE = Words.of("close q :q :wq :q!");
    private static final Set<String> W_QUIT = Words.of("quit shutdown poweroff halt");
    private static final Set<String> W_REBOOT = Words.of("reboot restart");

    /**
     * {@code close} closes the desktop; {@code quit} and {@code shutdown} exit the game and
     * {@code reboot} restarts it, each only once a floating prompt has been answered yes --
     * on the main menu too, where quit is also the title screen's own button.
     */
    private boolean power(String cmd) {
        if (W_CLOSE.contains(cmd)) {
            host.quit();
        } else if (W_QUIT.contains(cmd)) {
            Power.quit(host);
        } else if (W_REBOOT.contains(cmd)) {
            Power.reboot(this, host);
        } else {
            return false;
        }
        return true;
    }

    /** A menu button, by its command name: pressed as a click would. */
    private boolean menuButton(String cmd) {
        MenuButtons menu = menu();
        if (menu == null) return false;
        MenuButtons.Entry entry = menu.find(cmd);
        if (entry == null) return false;

        // Esc's disconnect is the shell's own: back to the main menu, not vanilla's server list.
        // `vanilla disconnect` still presses the real one.
        if (entry.name().equals("disconnect")) return false;

        // Singleplayer and multiplayer open as windows on the desk rather than vanilla's screens;
        // `vanilla singleplayer` is still there for anything only vanilla's lists can do.
        if (entry.enabled() && (entry.name().equals("singleplayer") || entry.name().equals("multiplayer"))) {
            String kind = entry.name().equals("singleplayer") ? "worlds" : "servers";
            if (kind.equals("worlds")) Worlds.refresh();
            else Servers.refresh();
            if (openWindow(kind)) {
                line().t("> ", GREEN).t(entry.label(), FG).t("  opened on the desk", DIM);
                return true;
            }
        }
        return pressButton(entry);
    }

    /** {@code vanilla <button>}: the real button, even for the two the desk opens as windows. */
    private boolean vanilla(List<String> args) {
        MenuButtons menu = menu();
        if (menu == null) {
            error("vanilla: only on the home desk");
            return true;
        }
        MenuButtons.Entry entry = args.isEmpty() ? null : menu.find(args.get(0));
        if (entry == null) {
            error("vanilla: which button? " + (args.isEmpty() ? "" : "no '" + args.get(0) + "' - ") + "ls lists them");
            return true;
        }
        return pressButton(entry);
    }

    private boolean pressButton(MenuButtons.Entry entry) {
        MenuButtons menu = menu();
        String error = menu.press(entry);
        if (error != null) error(error);
        else line().t("> ", GREEN).t(entry.label(), FG);
        return true;
    }

    private static final Set<String> W_ACCOUNTS = Words.of("accounts account acc alts");
    private static final Set<String> W_DOTFILES = Words.of("dotfiles dotfile dots");
    private static final Set<String> W_VANILLA = Words.of("vanilla");

    /** The Minecraft account switcher. */
    private boolean account(String cmd, List<String> args) {
        if (W_ACCOUNTS.contains(cmd)) {
            accounts.accounts(args);
        } else if (W_DOTFILES.contains(cmd)) {
            dotfiles.run(args);
        } else if (W_VANILLA.contains(cmd)) {
            vanilla(args);
        } else {
            return false;
        }
        return true;
    }

    private static final Set<String> W_HELP = Words.of("help ? h");
    private static final Set<String> W_CLEAR = Words.of("clear cls");
    private static final Set<String> W_LS_2 = Words.of("ls ll dir la");
    private static final Set<String> W_CD = Words.of("cd");
    private static final Set<String> W_PWD = Words.of("pwd");
    private static final Set<String> W_TREE = Words.of("tree");
    private static final Set<String> W_MAN = Words.of("man cat info");

    /** Walking the module tree. */
    private boolean files(String cmd, List<String> args) {
        if (W_HELP.contains(cmd)) {
            info.help();
        } else if (W_CLEAR.contains(cmd)) {
            clear();
        } else if (W_LS_2.contains(cmd)) {
            dropLsHint();
            // On the main menu, ls at the top is everything there is to type; on the Esc
            // menu, only its buttons.
            // ls -a (or la) lists the advanced settings too.
            List<String> rest = new ArrayList<>(args);
            boolean all = rest.remove("-a") || java.util.Objects.equals("la", cmd);
            if (menu() != null && rest.isEmpty() && cwd.parent == null) info.menuListing();
            else ls(rest.isEmpty() ? cwd : resolve(rest.get(0)), rest.isEmpty() ? "." : rest.get(0), all);
        } else if (W_CD.contains(cmd)) {
            cd(args.isEmpty() ? "~" : args.get(0));
        } else if (W_PWD.contains(cmd)) {
            print(cwd.path(), FG);
        } else if (W_TREE.contains(cmd)) {
            tree(args.isEmpty() ? cwd : resolve(args.get(0)));
        } else if (W_MAN.contains(cmd)) {
            man(args.isEmpty() ? cwd : resolve(args.get(0)), args.isEmpty() ? "." : args.get(0));
        } else {
            return false;
        }
        return true;
    }

    private static final Set<String> W_TOGGLE = Words.of("toggle t");
    private static final Set<String> W_BIND = Words.of("bind");
    private static final Set<String> W_UNBIND = Words.of("unbind");
    private static final Set<String> W_GUI = Words.of("gui panel");
    private static final Set<String> W_WINDOW = Words.of("open settings mod window");

    /** Switches, binds, and settings windows. */
    private boolean settings(String cmd, List<String> args) {
        if (W_TOGGLE.contains(cmd)) {
            toggle(args);
        } else if (W_BIND.contains(cmd)) {
            binds.bind(args);
        } else if (W_UNBIND.contains(cmd)) {
            binds.unbind(args);
        } else if (W_GUI.contains(cmd)) {
            gui(args);
        } else if (W_WINDOW.contains(cmd)) {
            window(args);
        } else {
            return false;
        }
        return true;
    }

    private static final Set<String> W_KEYS = Words.of("keys shortcuts wm");
    private static final Set<String> W_SUPER = Words.of("super");
    private static final Set<String> W_WS = Words.of("ws workstation workstations");
    private static final Set<String> W_ART = Words.of("art");
    private static final Set<String> W_BONSAI = Words.of("bonsai cbonsai");
    private static final Set<String> W_PIPES = Words.of("pipes pipes.sh ./pipes.sh");
    private static final Set<String> W_LOGIN = Words.of("login signin");
    private static final Set<String> W_LOGOUT = Words.of("logout signout");
    private static final Set<String> W_EXIT = Words.of("exit logoff");
    private static final Set<String> W_HYPRCTL = Words.of("hyprctl hypr");

    /** The desktop itself: workstations, the window manager, art and programs. */
    private boolean desktop(String cmd, List<String> args) {
        if (W_KEYS.contains(cmd)) {
            info.shortcuts();
        } else if (W_SUPER.contains(cmd)) {
            superKey(args);
        } else if (W_WS.contains(cmd)) {
            ws(args);
        } else if (W_ART.contains(cmd)) {
            art.run(args);
        } else if (W_BONSAI.contains(cmd)) {
            host.launch(APP_BONSAI);
        } else if (W_PIPES.contains(cmd)) {
            host.launch(APP_PIPES);
        } else if (W_LOGIN.contains(cmd)) {
            SignInCommands.login(this);
        } else if (W_LOGOUT.contains(cmd)) {
            SignInCommands.logout(this);
        } else if (W_EXIT.contains(cmd)) {
            host.exit();
        } else if (W_HYPRCTL.contains(cmd)) {
            hyprctl.run(args);
        } else {
            return false;
        }
        return true;
    }

    private static final Set<String> W_HISTORY = Words.of("history");
    private static final Set<String> W_NEOFETCH = Words.of("neofetch fastfetch fetch uname");
    private static final Set<String> W_WHOAMI = Words.of("whoami");
    private static final Set<String> W_DATE = Words.of("date");
    private static final Set<String> W_ECHO = Words.of("echo");
    private static final Set<String> W_SUDO = Words.of("sudo");
    private static final Set<String> W_RM_2 = Words.of("rm");
    private static final Set<String> W_VIM = Words.of("vim vi nano emacs");
    private static final Set<String> W_SAY = Words.of("say chat");

    /** Everything a shell is expected to answer that has nothing to do with the client. */
    private boolean misc(String cmd, List<String> args) {
        if (W_HISTORY.contains(cmd)) {
            info.history();
        } else if (W_NEOFETCH.contains(cmd)) {
            info.neofetch();
        } else if (W_WHOAMI.contains(cmd)) {
            info.whoami();
        } else if (W_DATE.contains(cmd)) {
            print(LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy", Locale.ROOT)), FG);
        } else if (W_ECHO.contains(cmd)) {
            print(String.join(" ", args), FG);
        } else if (W_SUDO.contains(cmd)) {
            error("user is not in the sudoers file. this incident will be reported.");
        } else if (W_RM_2.contains(cmd)) {
            error("rm: permission denied (nice try)");
        } else if (W_VIM.contains(cmd)) {
            error(cmd + ": use key:value, you animal");
        } else if (W_SAY.contains(cmd)) {
            chat(String.join(" ", args).strip());
        } else {
            return false;
        }
        return true;
    }

    /**
     * {@code say <message>}: a line in the server's chat as if typed in it, or with a leading
     * slash, a server command. Made for {@code bind g say gg}, which a chat macro is.
     * <p>
     * It goes through the connection's own chat and command methods, so it is signed and rate
     * limited exactly as typing it would be -- nothing here builds a chat packet itself.
     */
    private void chat(String message) {
        if (message.isEmpty()) {
            error("usage: say <message>   or   say /<command>");
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.multiplayer.ClientPacketListener connection = mc.getConnection();
        if (connection == null || mc.player == null) {
            error("say: not in a world");
            return;
        }
        // Vanilla's chat box caps a line at 256 characters and the server kicks for longer.
        if (message.length() > 256) message = message.substring(0, 256);
        if (message.startsWith("/")) {
            String command = message.substring(1).strip();
            if (command.isEmpty()) {
                error("say: / on its own is not a command");
                return;
            }
            connection.sendCommand(command);
        } else {
            connection.sendChat(message);
        }
        ok("said " + message);
    }

    /**
     * Anything that is not a command: {@code key:value} for the module the prompt is in,
     * {@code on}/{@code off} for its switch, or a module by name followed by what to do to it.
     */
    /**
     * A module's name and what follows it, as if typed as the whole command: its settings window,
     * or a setting set. For a command that shares its name with a module ({@code hold}) and hands
     * back what is not one of its own words.
     */
    void module(List<String> t) {
        fallback(t);
    }

    private void fallback(List<String> t) {
        String first = t.get(0);

        if (isAssignment(first) || (Setting.parseBool(first, false) != null && cwd.toggle != null)) {
            apply(cwd, t);
            return;
        }

        Node node = resolve(first);
        if (node != null) {
            if (t.size() == 1) {
                if (node.settings.isEmpty() && node.children.isEmpty() && node.toggle != null) {
                    // Nothing but a switch (pauseguard): its name alone is the command that flips it.
                    apply(node, List.of("toggle"));
                } else if (node.parent != null && com.mineux.core.ui.GuiChoice.commandsOpenWindows()
                    && host.openWindow(ModuleWindow.KIND + node.path().substring(2))) {
                    // Its settings window, on its page; where there is no desk for one, the text below.
                    // `gui commands text` (dotfiles/ui/ui.conf) keeps it to the text.
                } else if (node.settings.isEmpty() && node.toggle == null && !node.children.isEmpty()) {
                    ls(node, first);
                } else {
                    man(node, first);
                }
            } else {
                apply(node, t.subList(1, t.size()));
            }
            return;
        }

        Setting setting = cwd.setting(first);
        if (setting != null) {
            apply(cwd, t);
            return;
        }

        Line line = line().t(com.mineux.core.MineUX.shellName() + ": command not found: ", RED).t(first, FG);
        String near = nearest(first);
        if (near != null) line.t("  did you mean ", DIM).fill(near, CYAN, near, "use this");
    }

    static boolean isAssignment(String token) {
        int colon = Math.max(token.indexOf(':'), token.indexOf('='));
        return colon > 0;
    }

    /** Applies {@code on}, {@code off}, {@code key:value}, {@code key value} and bare boolean keys, in order. */
    private void apply(Node node, List<String> args) {
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);

            Boolean state = node.toggle != null ? Setting.parseBool(arg, node.on()) : null;
            if (state != null && node.setting(arg) == null) {
                String error = node.toggle.set(state ? "on" : "off");
                if (error != null) error(error);
                else switched(node);
                continue;
            }

            String key;
            String value;
            int sep = firstSeparator(arg);

            if (sep > 0) {
                key = arg.substring(0, sep);
                value = arg.substring(sep + 1);
            } else {
                key = arg;
                value = null;
            }

            Setting setting = node.setting(key);
            if (setting == null) {
                Line line = line().t("! ", RED).t(node.name + " has no setting '" + key + "'", RED);
                line.t("  ", DIM).run("ls " + rel(node), CYAN, "ls " + rel(node), "see what it has");
                continue;
            }

            if (value == null) {
                if (i + 1 < args.size() && node.setting(args.get(i + 1)) == null && firstSeparator(args.get(i + 1)) < 0) {
                    value = args.get(++i);
                } else if (setting.kind() == Setting.BOOL) {
                    value = "toggle";
                } else {
                    showSetting(node, setting);
                    continue;
                }
            }

            if (value.isEmpty()) {
                showSetting(node, setting);
                continue;
            }

            if (setting.asks(value)) {
                // Never switched on without the question on screen and answered yes.
                Setting asked = setting;
                line().t("  ", FAINT).t("waiting for you to confirm: " + setting.shown(), AMBER);
                confirm(setting.question(), setting.warning.get(), "allow", () -> asked.set("on"));
                continue;
            }

            String error = setting.set(value);
            if (error != null) {
                error(node.name + "/" + error);
            } else {
                line().t("> ", GREEN).t(node.name, GOLD).t("/", DIM).t(setting.key, FG).t(" = ", DIM)
                    .t(setting.display(), valueColor(setting));
            }
        }
    }

    static int firstSeparator(String arg) {
        int colon = arg.indexOf(':');
        int eq = arg.indexOf('=');
        if (colon < 0) return eq;
        if (eq < 0) return colon;
        return Math.min(colon, eq);
    }

    private void switched(Node node) {
        line().t("> ", GREEN).t(node.path().substring(2), GOLD).t(" is now ", DIM)
            .t(node.on() ? "ON" : "off", node.on() ? GREEN : RED);
    }

    private void showSetting(Node node, Setting s) {
        line().t(node.name, GOLD).t("/", DIM).t(s.key, FG).t(" = ", DIM).t(s.display(), valueColor(s))
            .t("   " + s.hint(), FAINT).t("  " + s.desc, DIM);
    }

    // ---- listing --------------------------------------------------------

    private void ls(Node node, String arg) {
        ls(node, arg, false);
    }

    private void ls(Node node, String arg, boolean all) {
        if (node == null) {
            error("ls: no such module '" + arg + "'");
            return;
        }

        if (node.toggle != null) {
            Line line = line().t(node.path().equals("~") ? "~" : node.name, GOLD).at(COL_STATE);
            stateSpan(line, node);
            line.at(COL_DESC).t(node.desc, DIM);
        } else if (node.parent != null) {
            line().t(node.name, GOLD).at(COL_DESC).t(node.desc, DIM);
        }

        for (Node child : node.children) {
            Line line = line().t("  ");
            line.run(child.name + "/", BLUE, "cd " + rel(child) + "; ls", "cd " + child.path());
            line.at(COL_STATE);
            if (child.toggle != null) stateSpan(line, child);
            else line.t("  -", FAINT);
            line.at(COL_DESC).t(child.desc, DIM);
        }

        // Sliders push the descriptions out; the whole listing keeps them in one column.
        boolean sliders = false;
        for (Setting s : node.settings) sliders |= s.slider;
        int descCol = sliders ? COL_VALUE + 12 + VALUE_W + SLIDER_W + 8 : COL_DESC + 24;
        int hidden = 0;
        for (Setting s : node.settings) {
            if (s.advanced && !all) hidden++;
            else if (!s.advanced) settingRow(node, s, descCol);
        }
        if (all) {
            boolean head = false;
            for (Setting s : node.settings) {
                if (!s.advanced) continue;
                if (!head) line().t("  advanced", CYAN).at(COL_DESC).t("the defaults already have these right", DIM);
                head = true;
                settingRow(node, s, descCol);
            }
        } else if (hidden > 0) {
            line().t("  + " + hidden + " advanced", FAINT).at(COL_DESC)
                .run("ls -a " + rel(node), CYAN, "ls -a " + rel(node), "list them too");
        }

        if (node.settings.isEmpty() && node.children.isEmpty()) line().t("  (nothing to type here)", DIM);
    }

    private void settingRow(Node node, Setting s, int descCol) {
        boolean bool = s.kind() == Setting.BOOL;
        String prefix = rel(node) + " ";

        Line line = line().t("  ");
        line.fill(s.key, FG, prefix + s.key + ":", s.desc + "  " + s.hint());
        line.at(COL_VALUE + 12);

        if (bool) {
            line.live(() -> s.bool() ? "[ON ]" : "[off]", () -> s.bool() ? GREEN : RED,
                prefix + s.key + ":toggle", "click to flip");
            line.last().quiet = true;
        } else {
            Line target = line.live(s::display, () -> valueColor(s), null, s.hint());
            target.last().fill = prefix + s.key + ":";
            if (s.slider) {
                line.at(COL_VALUE + 12 + VALUE_W).slider(s, SLIDER_W,
                    "drag to set " + s.key + (s.field2 != null ? " (a handle at each end)" : "") + ", or click the number to type it");
            }
        }

        line.at(descCol).t(s.desc, DIM);
    }

    private void stateSpan(Line line, Node node) {
        line.live(() -> node.on() ? "[ON ]" : "[off]", () -> node.on() ? GREEN : RED,
            "toggle " + rel(node), "click to flip");
        line.last().quiet = true;
    }

    private static int valueColor(Setting s) {
        return switch (s.kind()) {
            case Setting.BOOL -> s.bool() ? GREEN : RED;
            case Setting.KEY -> MAGENTA;
            case Setting.STRING -> AMBER;
            default -> CYAN;
        };
    }

    private void man(Node node, String arg) {
        if (node == null) {
            error("man: no such module '" + arg + "'");
            return;
        }

        Line head = line().t("== ", FAINT).t(node.path(), GOLD).t(" ==  ", FAINT).t(node.desc, DIM);
        if (node.parent != null) head.t("  ").run("[window]", CYAN, "open " + rel(node), "open its settings window");

        ls(node, arg);
    }

    private void tree(Node node) {
        if (node == null) {
            error("tree: no such module");
            return;
        }

        line().t(node.path(), GOLD);
        treeRows(node, "");
    }

    private void treeRows(Node node, String indent) {
        for (int i = 0; i < node.children.size(); i++) {
            Node child = node.children.get(i);
            boolean last = i == node.children.size() - 1;

            Line line = line().t(indent + (last ? "`-- " : "|-- "), FAINT);
            line.run(child.name, BLUE, "cd " + rel(child) + "; ls", child.desc);
            if (child.toggle != null) {
                line.t(" ");
                stateSpan(line, child);
            }
            line.t("  " + child.settings.size() + " settings", FAINT);

            treeRows(child, indent + (last ? "    " : "|   "));
        }
    }

    private void toggle(List<String> args) {
        if (args.isEmpty()) {
            line().t("switches", GOLD).t(" - click to flip, or ", DIM).fill("toggle <module>", CYAN, "toggle ", null);
            for (Node node : Modules.toggleable()) {
                Line line = line().t("  ");
                stateSpan(line, node);
                line.t(" ").run(node.path().substring(2), FG, "cd " + rel(node) + "; ls", "cd " + node.name);
                line.at(COL_DESC + 24).t(node.desc, DIM);
            }
            return;
        }

        for (String arg : args) {
            Node node = resolve(arg);
            if (node == null) {
                error("toggle: no such module '" + arg + "'");
            } else if (node.toggle == null) {
                error("toggle: " + node.name + " has no switch");
            } else {
                String error = node.toggle.set("toggle");
                if (error != null) error(error);
                else switched(node);
            }
        }
    }

    private void cd(String arg) {
        if (arg.equals("-")) {
            if (lastDir != null) backTo(lastDir, cwd);
            return;
        }

        Node node = resolve(arg);
        if (node == null) {
            error("cd: no such module: " + arg);
            return;
        }

        lastDir = cwd;
        cwd = node;
    }

    /** {@code cd -}: both directories as parameters, never a local copy of cwd (HoistCheck). */
    private void backTo(Node last, Node current) {
        cwd = last;
        lastDir = current;
    }

    /** {@code open <module>}: its settings window, where there is a desk for one; its listing otherwise. */
    private void window(List<String> args) {
        Node node = args.isEmpty() ? cwd : resolve(args.get(0));
        if (node == null || node.parent == null) {
            error("open: which module? try open rpc");
            return;
        }
        if (!host.openWindow(ModuleWindow.KIND + node.path().substring(2))) man(node, node.name);
    }

    /** {@code gui commands|topbar [window|text]}; on its own, what each is now. */
    private void gui(List<String> args) {
        if (!args.isEmpty() && (args.get(0).equalsIgnoreCase("commands") || args.get(0).equalsIgnoreCase("topbar"))) {
            guiDisplay(args.get(0).toLowerCase(Locale.ROOT), args.size() > 1 ? args.get(1) : null);
            return;
        }
        boolean commands = com.mineux.core.ui.GuiChoice.commandsOpenWindows();
        boolean topbar = com.mineux.core.ui.GuiChoice.topbarOpensWindows();
        line().t("gui commands ", CYAN).t(commands ? "window" : "text", GOLD).t("  typing a module's name", DIM);
        line().t("gui topbar   ", CYAN).t(topbar ? "window" : "text", GOLD).t("  clicking a topbar icon", DIM);
    }

    /** {@code gui commands|topbar window|text}: what typing a module's name, or clicking its icon, does. */
    private void guiDisplay(String what, String mode) {
        boolean windows = what.equals("commands") ? com.mineux.core.ui.GuiChoice.commandsOpenWindows()
            : com.mineux.core.ui.GuiChoice.topbarOpensWindows();
        if (mode == null) {
            // On its own: flips it.
            mode = windows ? com.mineux.core.ui.GuiChoice.TEXT : com.mineux.core.ui.GuiChoice.WINDOW;
        }
        com.mineux.core.ui.GuiChoice.setDisplay(what, mode.toLowerCase(Locale.ROOT));
        windows = what.equals("commands") ? com.mineux.core.ui.GuiChoice.commandsOpenWindows()
            : com.mineux.core.ui.GuiChoice.topbarOpensWindows();
        String doing = what.equals("commands") ? "typing a module's name " : "clicking a topbar icon ";
        ok(doing + (windows ? "opens its settings window" : "lists it in the terminal") + " - saved in dotfiles/ui/ui.conf");
    }

    private static final Set<String> W_LS = Words.of("ls list");
    private static final Set<String> W_NEW = Words.of("new add create +");
    private static final Set<String> W_OPEN = Words.of("open switch use cd");
    private static final Set<String> W_RM = Words.of("rm del delete remove");
    private static final Set<String> W_MV = Words.of("mv rename");
    private static final Set<String> W_SAVE = Words.of("save");

    private void ws(List<String> args) {
        String sub = args.isEmpty() ? "ls" : args.get(0).toLowerCase(Locale.ROOT);
        String error = null;

        if (W_LS.contains(sub)) {
            line().t("workstations", GOLD).t(" - click to open", DIM);
            List<String> names = Workstations.names();
            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i);
                boolean open = name.equals(Workstations.current());
                line().t(open ? "  * " : "    ", GREEN).t(Workstations.isSpecial(name) ? "  " : Workstations.slot(name) + " ", FAINT)
                    .run(name, open ? MAGENTA : FG, "ws open " + name, "open " + name)
                    .t(open ? "  (open)" : "", DIM);
            }
            return;
        } else if (W_NEW.contains(sub)) {
            String name = args.size() > 1 ? args.get(1) : Workstations.freshName();
            error = Workstations.create(name);
            if (error == null) ok("created and opened workstation " + name + " (a copy of the last one)");
        } else if (W_OPEN.contains(sub)) {
            if (args.size() < 2) {
                error = "usage: ws open <name>";
            } else {
                int slot = Workstations.parseSlot(args.get(1));
                if (Workstations.find(args.get(1)) == null && slot > 0) {
                    // A free number, the way SUPER+number asks for it: made on the spot.
                    error = Workstations.create(null, slot);
                    if (error == null) ok("created workstation " + slot + ":" + Workstations.current() + " (a copy of the last one)");
                } else {
                    error = Workstations.open(args.get(1));
                    if (error == null) ok("workstation " + Workstations.current() + " loaded");
                }
            }
        } else if (W_RM.contains(sub)) {
            if (args.size() < 2) {
                error = "usage: ws rm <name>";
            } else {
                error = Workstations.remove(args.get(1));
                if (error == null) ok("deleted " + args.get(1) + ", on " + Workstations.current());
            }
        } else if (W_MV.contains(sub)) {
            if (args.size() < 3) {
                error = "usage: ws mv <name> <new name>";
            } else {
                error = Workstations.rename(args.get(1), args.get(2));
                if (error == null) ok("renamed to " + args.get(2));
            }
        } else if (W_SAVE.contains(sub)) {
            Workstations.save();
            ok("saved into " + Workstations.current());
        } else {
            String found = Workstations.isSpecial(sub) ? Workstations.SPECIAL : Workstations.find(sub);
            if (found != null) {
                error = Workstations.open(found);
                if (error == null) ok("workstation " + Workstations.current() + " loaded");
            } else {
                error = "ws: unknown '" + sub + "' - ls, new, open, rm, mv, save";
            }
        }

        if (error != null) error(error);
        host.workstationsChanged();
    }

    static String superKey() {
        String key = Workstations.state().superKey;
        for (String k : SUPER_KEYS) if (k.equals(key)) return k;
        return "alt";
    }

    private void superKey(List<String> args) {
        if (args.isEmpty()) {
            line().t("SUPER is ", DIM).t(superKey(), GOLD).t("  - set it with ", DIM)
                .fill("super alt|ctrl|win|shift", CYAN, "super ", null);
            return;
        }

        String key = args.get(0).toLowerCase(Locale.ROOT);
        if (key.equals("control")) key = "ctrl";
        if (key.equals("super") || key.equals("meta") || key.equals("cmd")) key = "win";
        for (String k : SUPER_KEYS) {
            if (k.equals(key)) {
                Workstations.state().superKey = k;
                Workstations.saveState();
                ok("SUPER is now " + k);
                return;
            }
        }
        error("super: expected alt, ctrl, win or shift");
    }

    // ---- paths ----------------------------------------------------------

    /**
     * How a clickable command names a module. Every module name is unique in the tree and a
     * bare name resolves from anywhere (see {@link #resolve}), so a line printed while the
     * prompt was somewhere else still does the same thing when clicked later.
     */
    static String rel(Node node) {
        return node.parent == null ? "~" : node.name;
    }

    /**
     * A path from the prompt's point of view: absolute ({@code ~/aim}, {@code /aim}), relative
     * ({@code ..}, {@code reach}), or failing both, a module's own name anywhere in the tree.
     */
    Node resolve(String arg) {
        if (arg == null || arg.isEmpty()) return null;

        String path = arg;
        Node node = cwd;

        if (path.equals("~") || path.equals("/")) return Modules.root();
        if (path.startsWith("~/")) {
            node = Modules.root();
            path = path.substring(2);
        } else if (path.startsWith("/")) {
            node = Modules.root();
            path = path.substring(1);
        }

        Node walked = walk(node, path);
        if (walked != null) return walked;

        if (!path.contains("/")) {
            String lower = path.toLowerCase(Locale.ROOT);
            List<Node> all = new ArrayList<>();
            Modules.root().walk(all);

            Node prefixed = null;
            int prefixCount = 0;
            for (Node n : all) {
                if (n.name.equalsIgnoreCase(path)) return n;
                if (n.name.startsWith(lower)) {
                    prefixed = n;
                    prefixCount++;
                }
            }

            // An old name a module still answers to ({@code alias=} on its dir line).
            for (Node n : all) if (n.aliased(path)) return n;
            if (prefixCount == 1 && lower.length() >= 3) return prefixed;
        }

        return null;
    }

    private static Node walk(Node from, String path) {
        Node node = from;
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (node.parent != null) node = node.parent;
                continue;
            }
            node = node.child(part);
            if (node == null) return null;
        }
        return node;
    }

    private String nearest(String word) {
        String best = null;
        int bestDistance = 3;
        List<String> words = new ArrayList<>();
        for (String[] c : ExtCommands.builtInRows()) words.add(c[0]);
        words.addAll(ExtCommands.addedWords());
        List<Node> all = new ArrayList<>();
        Modules.root().walk(all);
        for (Node n : all) words.add(n.name);
        for (Setting s : cwd.settings) words.add(s.key);

        for (String w : words) {
            int d = distance(word.toLowerCase(Locale.ROOT), w);
            if (d < bestDistance) {
                bestDistance = d;
                best = w;
            }
        }
        return best;
    }

    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = cur;
            cur = swap;
        }
        return prev[b.length()];
    }

    // ---- completion -----------------------------------------------------

    /**
     * What could finish the word under the cursor. {@code before} is everything typed up to
     * the start of that word; {@code token} is the word so far.
     */
    List<Suggestion> complete(String before, String token) {
        return completion.complete(before, token);
    }

    String[] artNames() {
        return host.artNames();
    }
}
