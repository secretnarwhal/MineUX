package com.mineux.core.ui.term;

import com.mineux.core.mcaccount.Account;
import com.mineux.core.mcaccount.AccountSession;
import com.mineux.core.mcaccount.AccountsConfig;
import com.mineux.core.mcaccount.CrackedAccount;
import com.mineux.core.mcaccount.MicrosoftAccount;
import com.mineux.core.mcaccount.MicrosoftLogin;
import com.mineux.core.mcaccount.SessionAccount;
import com.mineux.core.ui.term.Term.Line;
import com.mineux.core.util.Words;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static com.mineux.core.ui.term.Term.*;

/**
 * The Minecraft account switcher, as one command: {@code accounts} lists the saved ones to
 * switch between, remove, or export the token of (the little ⧉ on a Microsoft row puts its
 * refresh token on the clipboard, which is what {@code accounts add <name> <token>} takes),
 * {@code accounts login} signs in to a Microsoft account in the
 * browser and switches to it, and {@code accounts add} saves one without the browser -- an
 * offline account from a name, a Microsoft one from a name and a refresh token (another copy
 * of this client's, or Prism Launcher's: both are the same Azure app), or a session one from
 * a Minecraft access token any launcher starts the game with, which plays until it expires.
 * <p>
 * Every login is network work that takes seconds, so it runs on a worker and reports on one
 * line whose text it updates as it goes -- the scrollback itself is only ever added to on the
 * client thread.
 */
final class AccountCommands {

    private final Shell shell;

    AccountCommands(Shell shell) {
        this.shell = shell;
    }

    /** One line a worker writes its progress to. */
    private static final class Status {
        volatile String text;
        volatile int color = DIM;

        Status(String text) {
            this.text = text;
        }

        void set(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }

    private Status status(String label, String first) {
        Status status = new Status(first);
        shell.line().t(label + " ", GOLD).live(() -> status.text, () -> status.color, null, null);
        return status;
    }

    // ---- login ----------------------------------------------------------

    /** {@code accounts login}: Microsoft's sign-in page in the browser, then that account in the game. */
    void login() {
        if (!MicrosoftLogin.available()) {
            shell.error(MicrosoftLogin.WAITING);
            shell.line().t("  ", FAINT).fill("accounts add <name> <session token>", CYAN, "accounts add ",
                "add an account by its session token (eyJ...)");
            return;
        }
        Status status = status("login", "opening microsoft sign-in in your browser...");
        shell.line().t("  finish signing in there; this line follows along", FAINT);
        signInLink();

        boolean opened = MicrosoftLogin.getRefreshToken(token -> {
            if (token == null) {
                status.set("cancelled, or microsoft said no", RED);
                return;
            }
            status.set("signed in to microsoft, fetching the minecraft profile...", DIM);

            Thread worker = new Thread(() -> {
                MicrosoftAccount account = new MicrosoftAccount(token);
                boolean switchNow = AccountSession.canSwitch();
                boolean ok = switchNow ? account.login() : account.fetchInfo();
                if (!ok) {
                    status.set("could not get a minecraft profile (does this account own the game?)", RED);
                    return;
                }
                AccountsConfig.get().put(account);
                status.set(switchNow ? "playing as " + account.displayName()
                    : "saved " + account.displayName() + " - switch to it from the home desk", GREEN);
            }, "mineux-ms-login");
            worker.setDaemon(true);
            worker.start();
        });
        if (!opened) status.set("the browser would not open - copy the link below into one", AMBER);
    }

    /**
     * The sign-in page's link under {@code accounts login}, for when the browser does not open (or
     * opens somewhere out of sight): a button that copies it, one that tries the browser again,
     * and the address itself. The loopback server is already listening, so the link works from any
     * browser on this PC.
     */
    private void signInLink() {
        String url = MicrosoftLogin.authorizeUrl();
        boolean[] copied = new boolean[1];
        Line line = shell.line().t("  browser didn't open?  ", FAINT);
        line.live(() -> copied[0] ? "⧉ link copied" : "⧉ copy the link", () -> copied[0] ? GREEN : CYAN,
            null, "copy the microsoft sign-in link to the clipboard");
        line.last().action = () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(url);
            copied[0] = true;
        };
        line.t("   ", FAINT).live(() -> "↗ open it again", () -> CYAN, null, "open the sign-in page in the browser again");
        line.last().action = () -> MicrosoftLogin.openBrowser();
        shell.line().t("  " + url, FAINT);
    }

    // ---- accounts -------------------------------------------------------

    private static final Set<String> W_LS = Words.of("ls list");
    private static final Set<String> W_USE = Words.of("use switch");
    private static final Set<String> W_LOGIN = Words.of("login signin");
    private static final Set<String> W_ADD = Words.of("add new");
    private static final Set<String> W_ACCOUNTS = Words.of("accounts account acc alts");

    /** What stands in for a token wherever a command is shown or kept. */
    private static final String HIDDEN = "<token>";

    /**
     * A command line as it may be echoed and kept in the history: {@code accounts add <name>
     * <token>} with its token taken out. The history is a file, and the scrollback is on screen.
     */
    static String redact(String input) {
        List<String> tokens = Shell.tokenize(input);
        if (tokens.size() < 4 || !W_ACCOUNTS.contains(tokens.get(0).toLowerCase(Locale.ROOT))
            || !W_ADD.contains(tokens.get(1).toLowerCase(Locale.ROOT))) {
            return input;
        }
        return tokens.get(0) + " " + tokens.get(1) + " " + tokens.get(2) + " " + HIDDEN;
    }
    private static final Set<String> W_RM = Words.of("rm remove del delete");

    /** {@code accounts [login|use|add|rm] ...} */
    void accounts(List<String> args) {
        String sub = args.isEmpty() ? "ls" : args.get(0).toLowerCase(Locale.ROOT);
        if (W_LS.contains(sub)) {
            list();
        } else if (W_LOGIN.contains(sub)) {
            // With a name it is what it was before: play as that one.
            if (args.size() < 2) login();
            else use(args.get(1));
        } else if (W_USE.contains(sub)) {
            if (args.size() < 2) shell.error("usage: accounts use <name|number>");
            else use(args.get(1));
        } else if (W_ADD.contains(sub)) {
            if (args.size() < 2) {
                shell.error("usage: accounts add <name> [token]  (a name alone is an offline account; with a refresh token a microsoft one, with a session token (eyJ...) a session one)");
            } else if (args.size() < 3) {
                add(args.get(1));
            } else {
                addReal(args.get(1), args.get(2));
            }
        } else if (W_RM.contains(sub)) {
            if (args.size() < 2) shell.error("usage: accounts rm <name|number>");
            else remove(args.get(1));
        } else {
            // A bare name or number switches to it.
            if (find(sub) != null) use(sub);
            else shell.error("accounts: unknown '" + sub + "' - ls, login, use, add, rm");
        }
    }

    private void list() {
        List<Account> accounts = AccountsConfig.get().accounts();
        String current = currentName();

        shell.line().t("accounts", GOLD).t(" - click one to play as it; ", DIM)
            .run("accounts login", CYAN, "accounts login", "add a microsoft account").t(" adds a microsoft one", DIM);
        if (accounts.isEmpty()) {
            shell.line().t("  none saved yet - ", DIM).run("accounts login", CYAN, "accounts login", "sign in with microsoft")
                .t(" or ", DIM).fill("accounts add <name> [token]", CYAN, "accounts add ", "an offline account, or a microsoft one by its refresh token");
        }

        // One cell of copied-state per row, so each export button flips only itself.
        boolean[] copied = new boolean[accounts.size()];
        for (int i = 0; i < accounts.size(); i++) {
            Account account = accounts.get(i);
            boolean playing = account.displayName().equalsIgnoreCase(current);
            Line line = shell.line().t(playing ? "  * " : "    ", GREEN).t((i + 1) + " ", FAINT);
            line.run(account.displayName(), playing ? GREEN : FG, "accounts use " + (i + 1), "play as " + account.displayName());
            line.at(Shell.COL_DESC).t(account.kind(), account.microsoft() ? CYAN : account instanceof SessionAccount ? MAGENTA : AMBER);
            if (account instanceof SessionAccount session) line.t(" " + expiry(session.expiresAt()), FAINT);
            line.at(Shell.COL_DESC + 60).run("[rm]", FAINT, "accounts rm " + (i + 1), "forget this account");
            // The export button, an account's token out to the clipboard: only a Microsoft row has
            // one (an offline account is its name and nothing else), and the token is the refresh
            // token -- the same secret `accounts add <name> <token>` spends, so what is copied
            // here is what another install of this client takes to play as that account there.
            if (account instanceof MicrosoftAccount microsoft) {
                final int row = i;
                line.at(Shell.COL_DESC + 60 + 36).live(
                    () -> copied[row] ? "⧉ copied" : "⧉",
                    () -> copied[row] ? GREEN : CYAN,
                    null, "copy " + account.displayName() + "'s auth token to the clipboard");
                line.last().action = () -> {
                    Minecraft.getInstance().keyboardHandler.setClipboard(microsoft.refreshToken());
                    copied[row] = true;
                };
            } else if (account instanceof SessionAccount session) {
                final int row = i;
                line.at(Shell.COL_DESC + 60 + 36).live(
                    () -> copied[row] ? "⧉ copied" : "⧉",
                    () -> copied[row] ? GREEN : CYAN,
                    null, "copy " + account.displayName() + "'s session token to the clipboard");
                line.last().action = () -> {
                    Minecraft.getInstance().keyboardHandler.setClipboard(session.accessToken());
                    copied[row] = true;
                };
            }
        }
        shell.line().t("  playing as ", DIM).t(current, GREEN);
    }

    private void use(String which) {
        Account account = find(which);
        if (account == null) {
            shell.error("accounts: no account '" + which + "'");
            return;
        }
        if (!AccountSession.canSwitch()) {
            shell.error("accounts: leave the world first - an account can only change on the home desk");
            return;
        }

        Status status = status("accounts", "logging in as " + account.displayName() + "...");
        Thread worker = new Thread(() -> {
            try {
                if (account.login()) {
                    // A refreshed Microsoft token, or a first-time profile, is kept.
                    AccountsConfig.get().put(account);
                    status.set("playing as " + account.displayName(), GREEN);
                } else if (account instanceof SessionAccount) {
                    status.set("could not log in as " + account.displayName()
                        + " - its session token has expired; add a fresh one", RED);
                } else {
                    status.set("could not log in as " + account.displayName(), RED);
                }
            } catch (RuntimeException e) {
                status.set("login failed: " + e.getMessage(), RED);
            }
        }, "mineux-account");
        worker.setDaemon(true);
        worker.start();
    }

    private void add(String name) {
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            shell.error("accounts: a minecraft name is 1-16 letters, digits or _");
            return;
        }
        CrackedAccount account = new CrackedAccount(name);
        account.fetchInfo();
        AccountsConfig.get().put(account);
        shell.line().t("> ", GREEN).t("added offline account ", FG).run(name, GOLD, "accounts use " + name, "play as " + name);
    }

    /**
     * {@code accounts add <name> <token>}, either kind of token, told apart by shape:
     * <ul>
     * <li>a Minecraft session token (a JWT, {@code eyJ...}): the access token a launcher starts
     *     the game with. Checked against the profile endpoint and saved as a
     *     {@link SessionAccount}, which plays until the token expires (about a day) and cannot
     *     be renewed.</li>
     * <li>anything else is taken as a Microsoft refresh token, which Microsoft only redeems for
     *     the Azure app that was given it -- this client's (the list's ⧉) or Prism Launcher's,
     *     since this client signs in as Prism's app. It is spent once here to get the profile,
     *     which is how it is known to be good before anything is saved; Microsoft hands a new
     *     one back, and that is the one kept.</li>
     * </ul>
     * Nothing is switched: it is a row in the list, to be played as when you choose.
     */
    private void addReal(String name, String token) {
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            shell.error("accounts: a minecraft name is 1-16 letters, digits or _");
            return;
        }
        if (token.length() < 20) {
            shell.error("accounts: that is not a token - leave it off for an offline account");
            return;
        }
        boolean session = SessionAccount.looksLikeSession(token);

        Status status = status("accounts", "checking " + name + "'s " + (session ? "session" : "refresh") + " token...");
        Thread worker = new Thread(() -> {
            try {
                if (session) addSession(status, name, token);
                else addRefresh(status, name, token);
            } catch (RuntimeException e) {
                status.set("could not add " + name + ": " + e.getMessage(), RED);
            }
        }, "mineux-account-add");
        worker.setDaemon(true);
        worker.start();
    }

    private static void addSession(Status status, String name, String token) {
        SessionAccount account = new SessionAccount(token, name);
        if (!account.fetchInfo()) {
            long expires = account.expiresAt();
            boolean expired = expires > 0L && expires < System.currentTimeMillis();
            status.set("minecraft would not take that session token for " + name
                + (expired ? " - it expired " + ago(expires) : " - expired, or not a minecraft token"), RED);
            return;
        }
        AccountsConfig.get().put(account);
        status.set("added " + account.displayName() + " (session, " + expiry(account.expiresAt())
            + ") - `accounts use " + account.displayName() + "` plays as it", GREEN);
    }

    private static void addRefresh(Status status, String name, String token) {
        MicrosoftAccount account = new MicrosoftAccount(token, name);
        MicrosoftLogin.Result result = account.check();
        if (result.ok()) {
            AccountsConfig.get().put(account);
            status.set("added " + account.displayName() + " - `accounts use " + account.displayName() + "` plays as it", GREEN);
        } else if (Objects.equals(MicrosoftLogin.OWNS, result.failure)) {
            status.set(name + "'s microsoft account does not own minecraft java", RED);
        } else if (Objects.equals(MicrosoftLogin.REFRESH, result.failure)) {
            status.set("microsoft would not refresh that token for " + name
                + " - only this client's (the ⧉ in `accounts`) and prism launcher's work;"
                + " for the official launcher's or another client's, add its session token (eyJ...)", RED);
        } else {
            status.set("microsoft took the token but xbox/minecraft refused " + name
                + " (" + result.failure + ") - try `accounts login`", RED);
        }
    }

    /** "expires in 14h", "expired 3h ago", or "expiry unknown". */
    private static String expiry(long at) {
        if (at <= 0L) return "expiry unknown";
        long now = System.currentTimeMillis();
        return at > now ? "expires in " + span(at - now) : "expired " + ago(at);
    }

    private static String ago(long at) {
        return span(System.currentTimeMillis() - at) + " ago";
    }

    private static String span(long ms) {
        long minutes = Math.max(0L, ms / 60_000L);
        if (minutes < 60L) return minutes + "m";
        if (minutes < 48L * 60L) return (minutes / 60L) + "h";
        return (minutes / (24L * 60L)) + "d";
    }

    private void remove(String which) {
        Account account = find(which);
        if (account == null) {
            shell.error("accounts: no account '" + which + "'");
            return;
        }
        AccountsConfig.get().remove(account);
        shell.ok("forgot " + account.displayName());
    }

    /** By number in the list, or by name. */
    private static Account find(String which) {
        List<Account> accounts = AccountsConfig.get().accounts();
        try {
            int index = Integer.parseInt(which) - 1;
            if (index >= 0 && index < accounts.size()) return accounts.get(index);
        } catch (NumberFormatException ignored) {
        }
        for (Account account : accounts) if (account.displayName().equalsIgnoreCase(which)) return account;
        return null;
    }

    private static String currentName() {
        try {
            return Minecraft.getInstance().getUser().getName();
        } catch (RuntimeException e) {
            return "?";
        }
    }
}
