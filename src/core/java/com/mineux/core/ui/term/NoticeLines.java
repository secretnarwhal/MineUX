package com.mineux.core.ui.term;

import com.mineux.core.social.Friend;
import com.mineux.core.social.FriendConfig;
import com.mineux.core.social.FriendList;
import com.mineux.core.social.Notices;
import com.mineux.core.ui.term.Term.Line;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static com.mineux.core.ui.term.Term.*;

/**
 * What the bar's social windows list, a {@link ListPane} each: the bell's notifications, the
 * friends list, and one friend's profile. Built fresh every few hundred ms from {@link Notices}
 * and {@link FriendList}.
 * <p>
 * Every click is either a terminal command, run and echoed in the desk's terminal exactly as if
 * typed (so a setting changed here is changed the same way as {@code friends set}, warning and
 * all), or opens another of these windows.
 */
final class NoticeLines {

    private NoticeLines() {}

    static final String NOTICES = "notifications";
    static final String FRIENDS = "friends";
    /** {@code friend:<account>}: that friend's profile. */
    static final String FRIEND = "friend:";
    /** {@code chat:<account>}: the conversation with them, in irc. */
    static final String CHAT = "chat:";

    /** Changelog posts opened in the notifications window, by id. */
    private static final Set<Integer> OPEN = new HashSet<>();

    /** Roughly what fits on a line of the window, in characters, for wrapping a body. */
    private static final int WRAP = 46;

    // ---- the bell --------------------------------------------------------

    static List<Line> notices(Consumer<String> opener) {
        if (Guest.is()) return Guest.lines();
        List<Line> out = new ArrayList<>();
        List<Notices.Item> all = Notices.all();
        int unread = Notices.unread();

        Line head = new Line()
            .t(unread > 0 ? unread + " unread" : "all read", unread > 0 ? GOLD : DIM);
        if (unread > 0) head.t("   ", FAINT).run("[mark all read]", CYAN, "notifications read", "grey every one");
        if (!all.isEmpty()) head.t("  ", FAINT).run("[clear all]", RED, "notifications clear", "remove every notification");
        out.add(head);

        if (!Notices.error().isEmpty()) out.add(new Line().t(Notices.error(), RED));
        if (all.isEmpty()) {
            out.add(new Line());
            out.add(new Line().t(Notices.loaded() ? "nothing here" : "loading...", FAINT));
            out.add(new Line().t("messages from friends and the changelog land here", FAINT));
            return out;
        }

        for (Notices.Item item : all) {
            out.add(new Line());
            boolean dm = item.dm();
            String title = dm && item.count > 1 ? item.title + " (" + item.count + ")" : item.title;
            Line top = new Line().t(item.read ? "  " : "* ", item.read ? FAINT : GOLD);
            if (dm) {
                // Brings you to the conversation, in a chat window of its own; opening it reads the row.
                String who = item.ref;
                top.run(clip(title, WRAP - 8), item.read ? DIM : FG, null, "open the conversation");
                top.last().action = () -> opener.accept(CHAT + who);
            } else if (item.request() || item.friend()) {
                int id = item.id;
                String who = item.ref;
                top.run(clip(title, WRAP - 8), item.read ? DIM : FG, null, "their profile");
                top.last().action = () -> {
                    Notices.read(id);
                    opener.accept(FriendList.find(who) != null ? FRIEND + who : FRIENDS);
                };
            } else {
                int id = item.id;
                top.run(clip(title, WRAP - 8), item.read ? DIM : FG, null, "read it");
                top.last().action = () -> {
                    if (!OPEN.remove(id)) OPEN.add(id);
                    Notices.read(id);
                };
            }
            top.t("  " + ago(item.at), FAINT);
            out.add(top);

            boolean open = !dm && OPEN.contains(item.id);
            String tag = dm ? "message" : item.request() ? "friend request" : item.friend() ? "new friend" : "changelog";
            int bodyColour = item.read ? FAINT : DIM;
            if (open) {
                for (String line : wrap(item.body, WRAP)) out.add(new Line().t("  " + line, item.read ? DIM : FG));
            } else if (!item.body.isEmpty() && dm) {
                // The message itself opens the conversation too: the whole notification is the way in.
                String who = item.ref;
                Line body = new Line().t("  ", bodyColour).run(clip(item.body.replace('\n', ' '), WRAP), bodyColour, null, "open the conversation");
                body.last().action = () -> opener.accept(CHAT + who);
                out.add(body);
            } else if (!item.body.isEmpty()) {
                out.add(new Line().t("  " + clip(item.body.replace('\n', ' '), WRAP), bodyColour));
            }
            Line foot = new Line().t("  " + tag, item.read ? FAINT : CYAN);
            if (item.request() && FriendList.asked(item.ref)) {
                // Still waiting on you: answer it here. Either answer reads and retitles the row.
                foot.t("  ", FAINT).run("[accept]", GREEN, "friends accept " + item.ref, "add them")
                    .t(" ", FAINT).run("[decline]", RED, "friends decline " + item.ref, "no");
            } else if (!item.read) {
                int id = item.id;
                foot.t("  ", FAINT).run("[mark read]", DIM, null, "grey it");
                foot.last().action = () -> Notices.read(id);
            }
            out.add(foot);
        }
        return out;
    }

    // ---- the friends list -----------------------------------------------

    static List<Line> friends(Consumer<String> opener) {
        if (Guest.is()) return Guest.lines();
        List<Line> out = new ArrayList<>();
        if (!FriendConfig.get().enabled) {
            out.add(new Line().t("friends are off", DIM));
            out.add(new Line().run("[turn on]", CYAN, "friends on", "connect to the friends service"));
            return out;
        }
        if (!FriendList.connected()) {
            String why = FriendList.error();
            out.add(new Line().t("not connected", AMBER).t(why.isEmpty() ? "" : "  " + why, DIM));
            out.add(new Line().run("[connect]", CYAN, "friends on", "connect to the friends service"));
            return out;
        }

        List<Friend> all = FriendList.all();
        out.add(new Line().t(FriendList.onlineCount() + " online", GREEN).t(" of " + all.size(), DIM)
            .t("   you are ", FAINT).t(FriendList.selfUser(), FG).t(" #" + FriendList.selfUid(), DIM));
        List<String> pending = FriendList.requests();
        if (!pending.isEmpty()) {
            Line waiting = new Line().t(pending.size() + " waiting:", AMBER);
            for (String who : pending) {
                waiting.t(" " + who, FG).run(" [accept]", GREEN, "friends accept " + who, "add them")
                    .run(" [x]", RED, "friends decline " + who, "decline");
            }
            out.add(waiting);
        }
        List<String> mine = FriendList.sent();
        if (!mine.isEmpty()) {
            Line asked = new Line().t("asked:", DIM);
            for (String who : mine) asked.t(" " + who, FG).run(" [cancel]", AMBER, "friends cancel " + who, "take the request back");
            out.add(asked);
        }
        out.add(new Line().fill("[add friend]", GREEN, "friends add ", "type their account name or #uid")
            .t("  click a name for their profile and settings", FAINT));
        out.add(new Line());

        if (all.isEmpty()) out.add(new Line().t("nobody yet  -  friends add <account|#uid>", FAINT));
        for (Friend friend : all) if (friend.online) out.add(row(friend, opener));
        for (Friend friend : all) if (!friend.online) out.add(row(friend, opener));
        return out;
    }

    private static Line row(Friend friend, Consumer<String> opener) {
        Line line = new Line().t(friend.online ? "* " : "  ", friend.online ? GREEN : FAINT);
        String user = friend.user;
        line.run(pad(friend.display(), 16), friend.online ? FG : DIM, null, "their profile");
        line.last().action = () -> opener.accept(FRIEND + user);
        if (!friend.online) line.t(pad("offline", 12), FAINT);
        else if (friend.server.isEmpty()) line.t(pad("online", 12), DIM);
        else line.t(pad(clip(friend.server, 11), 12), AMBER);
        line.run("[dm]", CYAN, "msg " + user, "open the conversation");
        if (friend.somewhere()) line.t(" ", FAINT).run("[join]", CYAN, "friends join " + user, "join " + friend.server);
        return line;
    }

    // ---- one friend -------------------------------------------------------

    static List<Line> profile(String user, Consumer<String> opener) {
        if (Guest.is()) return Guest.lines();
        List<Line> out = new ArrayList<>();
        Friend friend = FriendList.find(user);
        if (friend == null) {
            out.add(new Line().t(user + " is not on your list", DIM));
            Line back = new Line().run("[friends]", CYAN, null, "the friends list");
            back.last().action = () -> opener.accept(FRIENDS);
            out.add(back);
            return out;
        }

        out.add(new Line().t(friend.display(), GOLD).t("  #" + friend.uid, CYAN)
            .t(friend.nickname.isEmpty() ? "" : "  (" + friend.user + ")", DIM));
        if (friend.online && !friend.server.isEmpty()) {
            out.add(new Line().t("playing  ", DIM).t(friend.gamemode.isEmpty() ? "-" : friend.gamemode, AMBER)
                .t("  " + friend.server, FG).run("  [join]", CYAN, "friends join " + friend.user, "join " + friend.server));
        } else {
            out.add(new Line().t("playing  ", DIM).t(friend.online ? "online, not on a server" : "offline", FAINT));
        }
        if (!friend.mc.isEmpty()) out.add(new Line().t("as       ", DIM).t(friend.mc, FG));
        if (!friend.since.isEmpty()) out.add(new Line().t("friends  ", DIM).t(ago(friend.since) + " ago", FAINT));

        out.add(new Line());
        out.add(new Line().t("what they may do to your client", FAINT));
        out.add(toggle(friend, "join    ", friend.allowJoin, "remoteJoin", "send you to a server"));
        out.add(toggle(friend, "command ", friend.allowCommand, "remoteCommand", "run commands in your client"));
        out.add(toggle(friend, "presence", friend.sharing, "sharePresence", "see which server you are on"));
        out.add(toggle(friend, "screens ", friend.screens, "screens", "both of you need this on to see each other's screens"));

        out.add(new Line());
        out.add(new Line().t("what they allow you", FAINT));
        Line send = new Line().t("  send     ", DIM)
            .t(friend.theyAllowJoin ? "allowed" : "no", friend.theyAllowJoin ? GREEN : FAINT);
        if (friend.theyAllowJoin) send.run("  [send them here]", CYAN, "friends send " + friend.user, "ask their client to join your server");
        out.add(send);
        out.add(new Line().t("  command  ", DIM)
            .t(friend.theyAllowCommand ? "allowed" : "no", friend.theyAllowCommand ? GREEN : FAINT));
        out.add(new Line().t("  screens  ", DIM)
            .t(friend.theyScreens ? "on" : "off", friend.theyScreens ? GREEN : FAINT));

        out.add(new Line());
        Line acts = new Line()
            .run("[message]", CYAN, "msg " + friend.user, "open the conversation")
            .t("  ", FAINT)
            .run(friend.muted ? "[unmute]" : "[mute]", friend.muted ? GREEN : AMBER,
                (friend.muted ? "friends unmute " : "friends mute ") + friend.user,
                friend.muted ? "hear from them again" : "drop their messages and presence")
            .t("  ", FAINT)
            .run("[remove]", RED, "friends remove " + friend.user, "take them off your list");
        out.add(acts);
        Line back = new Line().run("[all friends]", DIM, null, "the friends list");
        back.last().action = () -> opener.accept(FRIENDS);
        out.add(back);
        return out;
    }

    /** A permission: whether it is on, and the click that flips it through {@code friends set}. */
    private static Line toggle(Friend friend, String label, boolean on, String field, String what) {
        return new Line().t("  " + label + " ", DIM)
            .t(on ? "allowed" : "off    ", on ? AMBER : FAINT)
            .run(on ? "  [revoke]" : "  [allow]", on ? RED : CYAN,
                "friends set " + friend.user + " " + field + " " + (on ? "off" : "on"), what)
            .t("  " + what, FAINT);
    }

    // ---- text ---------------------------------------------------------------

    /** "5m", "3h", "2d" since an ISO-8601 time; empty for one that does not parse. */
    static String ago(String iso) {
        try {
            long s = Math.max(0L, Duration.between(Instant.parse(iso), Instant.now()).getSeconds());
            if (s < 60) return "now";
            if (s < 3600) return s / 60 + "m";
            if (s < 86400) return s / 3600 + "h";
            return s / 86400 + "d";
        } catch (RuntimeException e) {
            return "";
        }
    }

    static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static String pad(String text, int width) {
        StringBuilder out = new StringBuilder(clip(text, width - 1));
        while (out.length() < width) out.append(' ');
        return out.toString();
    }

    /** A body as lines of at most {@code width} characters, broken at spaces where it can be. */
    static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            String rest = para;
            int guard = 0;
            while (rest.length() > width && guard++ < 200) {
                int cut = rest.lastIndexOf(' ', width);
                if (cut <= 0) cut = width;
                out.add(rest.substring(0, cut));
                rest = rest.substring(cut).stripLeading();
            }
            out.add(rest);
        }
        return out;
    }
}
