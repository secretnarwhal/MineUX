package com.mineux.core.ui.term;

import com.mineux.core.social.Notices;
import com.mineux.core.ui.term.DiscordCommands.Status;
import com.mineux.core.util.Words;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.mineux.core.ui.term.Term.*;

/**
 * {@code notifications}: the bell's list, typed. On its own it opens the bell's window (or
 * prints the list where there is no desktop); {@code read} greys them all, {@code clear} takes
 * them all away. {@code changelog <title> | <body>} posts a changelog entry into everyone's bell
 * (admins only; the server refuses anyone else).
 */
final class NoticeCommands {

    private final Shell shell;

    NoticeCommands(Shell shell) {
        this.shell = shell;
    }

    private static final Set<String> W_NOTICES = Words.of("notifications notification notify bell inbox");
    private static final Set<String> W_CHANGELOG = Words.of("changelog");
    private static final Set<String> W_READ = Words.of("read seen markread");
    private static final Set<String> W_CLEAR = Words.of("clear clean empty");
    private static final Set<String> W_LIST = Words.of("list ls print");
    private static final Set<String> W_POST = Words.of("post add new");

    /** @return false for a command that is not one of these */
    boolean run(String cmd, List<String> args) {
        if ((W_CHANGELOG.contains(cmd) || W_NOTICES.contains(cmd)) && Guest.refuse(shell, cmd)) return true;
        if (W_CHANGELOG.contains(cmd)) {
            List<String> rest = !args.isEmpty() && W_POST.contains(args.get(0).toLowerCase(Locale.ROOT))
                ? args.subList(1, args.size()) : args;
            post(String.join(" ", rest));
            return true;
        }
        if (!W_NOTICES.contains(cmd)) return false;

        String sub = args.isEmpty() ? "" : args.get(0).toLowerCase(Locale.ROOT);
        if (sub.isEmpty()) {
            Notices.refresh();
            if (!shell.openWindow(NoticeLines.NOTICES)) list();
        } else if (W_READ.contains(sub)) {
            Notices.readAll();
            shell.ok("notifications: all read");
        } else if (W_CLEAR.contains(sub)) {
            Notices.clear();
            shell.ok("notifications: cleared");
        } else if (W_LIST.contains(sub)) {
            list();
        } else if (W_POST.contains(sub)) {
            post(String.join(" ", args.subList(1, args.size())));
        } else {
            shell.error("notifications: read, clear, list, or post <title> | <body>");
        }
        return true;
    }

    private void list() {
        List<Notices.Item> all = Notices.all();
        shell.line().t("notifications ", GOLD).t(Notices.unread() + " unread", Notices.unread() > 0 ? GOLD : DIM)
            .t("  of " + all.size(), DIM);
        for (Notices.Item item : all) {
            shell.line().t(item.read ? "    " : "  * ", item.read ? FAINT : GOLD)
                .t(item.title, item.read ? DIM : FG)
                .t("  " + NoticeLines.ago(item.at), FAINT)
                .t("  " + NoticeLines.clip(item.body.replace('\n', ' '), 60), item.read ? FAINT : DIM);
        }
    }

    /**
     * {@code changelog <title> | <body>}: the title is everything before the first {@code |},
     * the body everything after; {@code \n} in the body is a new line.
     */
    private void post(String text) {
        int bar = text.indexOf('|');
        String title = (bar < 0 ? text : text.substring(0, bar)).trim();
        String body = bar < 0 ? "" : text.substring(bar + 1).trim().replace("\\n", "\n");
        if (title.isEmpty()) {
            shell.error("usage: changelog <title> | <body>   (\\n for a new line in the body)");
            return;
        }
        Status row = DiscordCommands.row(shell, "changelog ", GOLD, "posting...");
        Notices.post(title, body, said -> row.set(said, said.startsWith("posted") ? GREEN : RED));
    }
}
