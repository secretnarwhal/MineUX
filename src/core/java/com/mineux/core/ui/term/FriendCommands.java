package com.mineux.core.ui.term;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mineux.core.social.Friend;
import com.mineux.core.social.FriendConfig;
import com.mineux.core.social.FriendJoin;
import com.mineux.core.social.FriendList;
import com.mineux.core.social.FriendOps;
import com.mineux.core.social.FriendTalk;
import com.mineux.core.ui.term.DiscordCommands.Status;
import com.mineux.core.util.Words;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.mineux.core.ui.term.Term.*;

/**
 * {@code friends} and everything under it, including a friend's profile.
 * <p>
 * Every subcommand that goes to the network does the same two things: print a row straight away
 * and hand the call to a worker that fills the row in when the server answers
 * ({@link DiscordCommands#row} and {@link DiscordCommands#work}, the pair {@code lookup} uses).
 * Nothing here blocks the terminal, and nothing here writes to the shell's output from a worker
 * -- only to the volatile fields on a {@link Status} that the row re-reads as it draws.
 * <p>
 * The list and a profile are both printed as rows of clickable text rather than drawn as a
 * window. That is what makes "click on your friends" work in a terminal: every name is a link to
 * their profile, and every setting on the profile is a link that toggles it and reprints.
 */
final class FriendCommands {

    private final Shell shell;

    FriendCommands(Shell shell) {
        this.shell = shell;
    }

    private static final Set<String> W_FRIENDS = Words.of("friends friend f");
    private static final Set<String> W_MSG = Words.of("msg m w tell dm");
    private static final Set<String> W_IRC = Words.of("irc chat");
    private static final Set<String> W_REPLY = Words.of("r reply");

    private static final Set<String> W_ADD = Words.of("add request");
    private static final Set<String> W_ACCEPT = Words.of("accept yes");
    private static final Set<String> W_DECLINE = Words.of("decline no deny");
    private static final Set<String> W_REMOVE = Words.of("remove rm del delete unfriend");
    private static final Set<String> W_REQUESTS = Words.of("requests pending sent");
    private static final Set<String> W_CANCEL = Words.of("cancel unrequest withdraw");
    private static final Set<String> W_JOIN = Words.of("join goto follow");
    private static final Set<String> W_WHO = Words.of("who profile info");
    private static final Set<String> W_LIST = Words.of("list ls print");
    private static final Set<String> W_SETTINGS = Words.of("set allow");
    private static final Set<String> W_NICK = Words.of("nick nickname name");
    private static final Set<String> W_MUTE = Words.of("mute");
    private static final Set<String> W_UNMUTE = Words.of("unmute");
    private static final Set<String> W_SEND = Words.of("send bring summon");
    private static final Set<String> W_RUN = Words.of("run exec");
    private static final Set<String> W_LOG = Words.of("log");
    private static final Set<String> W_ON = Words.of("on connect");
    /** Off for good: the whole feature, until {@code friends on}. Not in the tray, so a client with every module off has nothing lit. */
    private static final Set<String> W_OFF = Words.of("off");
    /** Only this connection: auto connect brings it back. */
    private static final Set<String> W_DISCONNECT = Words.of("disconnect");
    private static final Set<String> W_WINDOW = Words.of("window win gui panel open");

    /** The last person to message you, for {@code r}. */
    private static volatile String lastFrom = "";

    /** @return false for a command that is not one of these */
    boolean run(String cmd, List<String> args) {
        boolean ours = W_MSG.contains(cmd) || W_IRC.contains(cmd) || W_REPLY.contains(cmd) || W_FRIENDS.contains(cmd);
        // irc as a guest when an extension's room is there: it has its own sign-in.
        boolean guestIrc = W_IRC.contains(cmd) && com.mineux.core.social.Chat.extraRoomShown();
        if (ours && !guestIrc && Guest.refuse(shell, cmd)) return true;
        if (W_MSG.contains(cmd)) {
            message(args.isEmpty() ? "" : args.get(0), join(args, 1));
            return true;
        }
        if (W_IRC.contains(cmd)) {
            irc(args.isEmpty() ? "" : args.get(0));
            return true;
        }
        if (W_REPLY.contains(cmd)) {
            message(lastFrom, join(args, 0));
            return true;
        }
        if (!W_FRIENDS.contains(cmd)) return false;

        if (args.isEmpty()) {
            // The friends app, in this terminal; the printed list where there is none (a keybind's shell).
            if (!shell.launch(Shell.APP_FRIENDS)) list();
            return true;
        }

        String sub = args.get(0).toLowerCase(Locale.ROOT);
        List<String> rest = args.subList(1, args.size());
        if (!roster(sub, rest) && !profile(sub, rest)) {
            // Not a subcommand: `friends alex` is the profile, which is what people type.
            Friend friend = FriendList.find(sub);
            if (friend != null && shell.launch(Shell.APP_FRIENDS + ":" + friend.user)) return true;
            if (friend != null) who(sub);
            else shell.error("friends: no subcommand " + sub + " - try friends on its own");
        }
        return true;
    }

    /** Who is on the list, and getting on or off it. */
    private boolean roster(String sub, List<String> args) {
        if (W_ADD.contains(sub)) {
            if (args.isEmpty()) shell.error("usage: friends add <account|#uid>");
            else call("friends add", FriendOps.REQUEST, FriendTalk.who(args.get(0)));
        } else if (W_ACCEPT.contains(sub)) {
            if (args.isEmpty()) shell.error("usage: friends accept <account>");
            else call("friends accept", FriendOps.ACCEPT, FriendTalk.who(args.get(0)));
        } else if (W_DECLINE.contains(sub)) {
            if (args.isEmpty()) shell.error("usage: friends decline <account>");
            else call("friends decline", FriendOps.DECLINE, FriendTalk.who(args.get(0)));
        } else if (W_REMOVE.contains(sub)) {
            if (args.isEmpty()) shell.error("usage: friends remove <account>");
            else call("friends remove", FriendOps.REMOVE, FriendTalk.who(args.get(0)));
        } else if (W_CANCEL.contains(sub)) {
            // The service's decline drops a request either way round, so it takes back yours too.
            if (args.isEmpty()) shell.error("usage: friends cancel <account>");
            else call("friends cancel", FriendOps.DECLINE, FriendTalk.who(args.get(0)));
        } else if (W_REQUESTS.contains(sub)) {
            requests();
        } else if (W_LIST.contains(sub)) {
            list();
        } else if (W_WHO.contains(sub)) {
            String name = args.isEmpty() ? "" : args.get(0);
            Friend friend = FriendList.find(name);
            if (friend == null || !shell.launch(Shell.APP_FRIENDS + ":" + friend.user)) who(name);
        } else if (W_LOG.contains(sub)) {
            log();
        } else if (W_WINDOW.contains(sub)) {
            window(args.isEmpty() ? "" : args.get(0));
        } else if (W_ON.contains(sub)) {
            FriendTalk.connect();
            shell.ok("friends: connecting");
        } else if (W_OFF.contains(sub)) {
            FriendConfig config = FriendConfig.get();
            config.enabled = false;
            config.save();
            FriendTalk.disconnect();
            shell.ok("friends: off - nobody sees you online and nothing connects until friends on");
        } else if (W_DISCONNECT.contains(sub)) {
            FriendTalk.disconnect();
            shell.ok("friends: disconnected" + (FriendConfig.get().autoConnect ? " (auto connect joins again; friends off stays off)" : ""));
        } else {
            return false;
        }
        return true;
    }

    /** What you do with one friend once you have one. */
    private boolean profile(String sub, List<String> args) {
        if (W_JOIN.contains(sub)) {
            follow(args.isEmpty() ? "" : args.get(0));
        } else if (W_SETTINGS.contains(sub)) {
            grant(args);
        } else if (W_NICK.contains(sub)) {
            nick(args);
        } else if (W_MUTE.contains(sub)) {
            mute(args.isEmpty() ? "" : args.get(0), true);
        } else if (W_UNMUTE.contains(sub)) {
            mute(args.isEmpty() ? "" : args.get(0), false);
        } else if (W_SEND.contains(sub)) {
            send(args);
        } else if (W_RUN.contains(sub)) {
            remote(args);
        } else {
            return false;
        }
        return true;
    }

    // ---- the list --------------------------------------------------------

    /** {@code friends}: everyone, online first, each name a link to their profile. */
    private void list() {
        if (!FriendConfig.get().enabled) {
            shell.line().t("friends ", GOLD).t("off", DIM).t("  -  friends on", FAINT);
            return;
        }
        if (!FriendList.connected()) {
            String why = FriendList.error();
            shell.line().t("friends ", GOLD).t("not connected", AMBER)
                .t(why.isEmpty() ? "" : "  -  " + why, DIM);
            shell.line().t("  friends on", CYAN).t("   to connect", FAINT);
            return;
        }

        List<Friend> all = FriendList.all();
        shell.line().t("friends ", GOLD)
            .t(FriendList.onlineCount() + " online", GREEN)
            .t("  of " + all.size(), DIM)
            .t("   you are ", FAINT).t(FriendList.selfUser(), FG)
            .t(" #" + FriendList.selfUid(), DIM);

        List<String> pending = FriendList.requests();
        if (!pending.isEmpty()) {
            shell.line().t("  ", FAINT).t(pending.size() + " waiting", AMBER)
                .run("  [see]", CYAN, "friends requests", "who has asked to be your friend");
        }
        if (FriendList.unread() > 0) {
            shell.line().t("  ", FAINT).t(FriendList.unread() + " unread", CYAN);
        }

        if (all.isEmpty()) {
            shell.line().t("  nobody yet  -  ", FAINT).t("friends add <account|#uid>", CYAN);
            return;
        }

        for (Friend friend : all) if (friend.online) row(friend);
        for (Friend friend : all) if (!friend.online) row(friend);
    }

    /** One line of the list: a dot, a clickable name, where they are, and a follow link. */
    private void row(Friend friend) {
        Term.Line line = shell.line()
            .t("  ", FAINT)
            .t(friend.online ? "* " : "  ", friend.online ? GREEN : FAINT);

        line.run(pad(friend.display(), 18), friend.online ? FG : FAINT,
            "friends who " + friend.user, "their profile, settings and uid");

        if (!friend.online) {
            line.t("offline", FAINT);
            return;
        }
        if (friend.server.isEmpty()) {
            line.t("online", DIM);
            return;
        }

        line.t(pad(friend.gamemode.isEmpty() ? "-" : friend.gamemode, 14), AMBER);
        line.t(friend.server, DIM);
        line.run("  [join]", CYAN, "friends join " + friend.user, "join " + friend.server);
    }

    /** {@code friends window}: the list in a window; {@code friends open <who>}: their profile in one. */
    private void window(String who) {
        String kind = NoticeLines.FRIENDS;
        if (!who.isEmpty()) {
            Friend friend = FriendList.find(who);
            if (friend == null) {
                shell.error("friends: nobody on your list called " + who);
                return;
            }
            kind = NoticeLines.FRIEND + friend.user;
        }
        if (!shell.openWindow(kind)) shell.error("friends window: needs the desktop - try friends on its own");
    }

    private void requests() {
        List<String> pending = FriendList.requests();
        List<String> mine = FriendList.sent();
        for (String who : mine) {
            shell.line().t("  asked ", FAINT).t(pad(who, 20), FG)
                .run("[cancel]", AMBER, "friends cancel " + who, "take the request back");
        }
        if (pending.isEmpty()) {
            shell.line().t("friends ", GOLD).t("nobody is waiting", DIM);
            return;
        }
        shell.line().t("friends ", GOLD).t(pending.size() + " waiting", AMBER);
        for (String who : pending) {
            shell.line().t("  ", FAINT).t(pad(who, 20), FG)
                .run("[accept]", GREEN, "friends accept " + who, "add them")
                .t("  ", FAINT)
                .run("[decline]", RED, "friends decline " + who, "no");
        }
    }

    // ---- one friend ------------------------------------------------------

    /**
     * {@code friends who <name>}: the profile.
     * <p>
     * The three permission rows are the point of this screen. Each is a live statement of what
     * this person may do to your client and a link that flips it, so the thing you are agreeing
     * to and the thing you click are the same line.
     */
    private void who(String name) {
        Friend friend = FriendList.find(name);
        if (friend == null) {
            shell.error("friends: nobody on your list called " + name);
            return;
        }

        shell.line().t("friends ", GOLD).t(friend.display(), FG)
            .t("  #" + friend.uid, CYAN)
            .t(friend.nickname.isEmpty() ? "" : "  (" + friend.user + ")", DIM);

        if (friend.online && !friend.server.isEmpty()) {
            shell.line().t("  playing  ", DIM).t(friend.gamemode.isEmpty() ? "-" : friend.gamemode, AMBER)
                .t("  " + friend.server, FG)
                .run("  [join]", CYAN, "friends join " + friend.user, "join " + friend.server);
        } else {
            shell.line().t("  playing  ", DIM).t(friend.online ? "online, not on a server" : "offline", FAINT);
        }
        if (!friend.mc.isEmpty()) shell.line().t("  as       ", DIM).t(friend.mc, FG);

        shell.line().t("  what they may do to your client", FAINT);
        toggle(friend, "join    ", friend.allowJoin,
            "send you to a server, without asking", "remoteJoin");
        toggle(friend, "command ", friend.allowCommand,
            "run any terminal command in your client (asks first)", "remoteCommand");
        toggle(friend, "presence", friend.sharing,
            "see which server you are on", "sharePresence");
        toggle(friend, "screens ", friend.screens,
            "both of you need this on to see each other's screens", "screens");

        shell.line().t("  what you may do to theirs", FAINT);
        shell.line().t("    send     ", DIM)
            .t(friend.theyAllowJoin ? "allowed" : "not allowed", friend.theyAllowJoin ? GREEN : FAINT)
            .t(friend.theyAllowJoin ? "" : "   they have not turned it on", FAINT);
        if (friend.theyAllowJoin) {
            shell.line().t("             ", DIM)
                .run("[send them here]", CYAN, "friends send " + friend.user,
                    "ask their client to join the server you are on");
        }
        shell.line().t("    command  ", DIM)
            .t(friend.theyAllowCommand ? "allowed" : "not allowed", friend.theyAllowCommand ? GREEN : FAINT);
        shell.line().t("    screens  ", DIM)
            .t(friend.display() + " has screens " + (friend.theyScreens ? "on" : "off"), friend.theyScreens ? GREEN : FAINT);

        shell.line().t("  ", FAINT)
            .run("[message]", CYAN, "msg " + friend.user + " ", "write to them")
            .t("  ", FAINT)
            .run(friend.muted ? "[unmute]" : "[mute]", friend.muted ? GREEN : AMBER,
                (friend.muted ? "friends unmute " : "friends mute ") + friend.user,
                friend.muted ? "hear from them again" : "drop their messages and presence")
            .t("  ", FAINT)
            .run("[remove]", RED, "friends remove " + friend.user, "take them off your list");
    }

    /** One permission row: what it is, whether it is on, and the link that flips it. */
    private void toggle(Friend friend, String label, boolean on, String what, String field) {
        shell.line().t("    " + label + "  ", DIM)
            .t(on ? "allowed" : "no", on ? AMBER : FAINT)
            .run(on ? "  [revoke]" : "  [allow]", on ? RED : CYAN,
                "friends set " + friend.user + " " + field + " " + (on ? "off" : "on"), what)
            .t("   " + what, FAINT);
    }

    // ---- the things a profile links to -----------------------------------

    private void follow(String name) {
        Friend friend = FriendList.find(name);
        if (friend == null) {
            shell.error("friends join: nobody on your list called " + name);
            return;
        }
        if (!friend.somewhere()) {
            shell.error("friends join: " + friend.display()
                + (friend.online ? " is not on a server" : " is offline"));
            return;
        }
        shell.ok(FriendJoin.connect(friend.server));
    }

    /** {@code friends set <who> <field> on|off}. The field names match the service's. */
    private void grant(List<String> args) {
        if (args.size() < 3) {
            shell.error("usage: friends set <who> remoteJoin|remoteCommand|sharePresence|screens on|off");
            return;
        }
        Friend friend = FriendList.find(args.get(0));
        if (friend == null) {
            shell.error("friends: nobody on your list called " + args.get(0));
            return;
        }
        String field = args.get(1);
        if (!field.equalsIgnoreCase("remoteJoin") && !field.equalsIgnoreCase("remoteCommand")
            && !field.equalsIgnoreCase("sharePresence") && !field.equalsIgnoreCase("screens")) {
            shell.error("friends set: remoteJoin, remoteCommand, sharePresence or screens");
            return;
        }
        boolean on = Words.of("on true yes 1 allow").contains(args.get(2).toLowerCase(Locale.ROOT));

        if (on && field.equalsIgnoreCase("remoteCommand") && !friend.allowCommand) {
            // The one grant that hands over the whole client. It is never sent without the
            // warning being on screen and answered yes -- typed or clicked, the same prompt.
            shell.line().t("  ", FAINT).t("waiting for you to confirm: " + friend.display()
                + " running commands in your client", AMBER);
            shell.confirm("let " + friend.display() + " run commands in your client?",
                COMMAND_WARNING, "allow", () -> send(friend, field, true));
            return;
        }
        send(friend, field, on);
    }

    /**
     * What granting remote commands means, in the prompt that asks before it is granted. It is
     * blunt on purpose: anything softer would be a warning people click through.
     */
    static final String COMMAND_WARNING =
        "They will be able to run ANY MineUX terminal command on your client, whenever "
            + "you are online, without asking you first.\n"
            + "That includes: turning modules on and off and changing every setting, binding "
            + "your keys, switching workstations and accounts, disconnecting you and joining "
            + "other servers, and 'say' -- which speaks in chat and runs /commands as you "
            + "(/pay, /msg, /tpa...).\n"
            + "Every command they run shows a banner and is written to friends log. "
            + "Only allow someone you trust with your account. Revoke it any time from their "
            + "profile.";

    private void send(Friend friend, String field, boolean on) {
        // Shown changed at once; the service leaves an absent field alone, so one toggle is one
        // field and two clicks cannot race each other into overwriting the other's setting.
        Status row = DiscordCommands.row(shell, "friends set ", GOLD, "...");
        FriendTalk.grant(friend.user, camel(field), on, why -> row.set(why == null ? "ok" : why, why == null ? GREEN : RED));

        if (on && field.equalsIgnoreCase("screens")) {
            shell.line().t("  ", FAINT).t(friend.theyScreens
                ? "you and " + friend.display() + " now see each other's shared screens"
                : "screens with " + friend.display() + " start once they turn it on too", AMBER);
        } else if (on && !field.equalsIgnoreCase("sharePresence")) {
            shell.line().t("  ", FAINT).t(friend.display() + " can now "
                + (field.equalsIgnoreCase("remoteJoin") ? "move your client between servers"
                : "run commands in your client"), AMBER);
        }
    }

    private void nick(List<String> args) {
        if (args.isEmpty()) {
            shell.error("usage: friends nick <who> [name]   (no name clears it)");
            return;
        }
        Friend friend = FriendList.find(args.get(0));
        if (friend == null) {
            shell.error("friends: nobody on your list called " + args.get(0));
            return;
        }
        JsonObject body = FriendTalk.who(friend.user);
        body.addProperty("nickname", join(args, 1));
        call("friends nick", FriendOps.GRANTS, body);
    }

    private void mute(String name, boolean muted) {
        Friend friend = FriendList.find(name);
        if (friend == null) {
            shell.error("friends: nobody on your list called " + name);
            return;
        }
        JsonObject body = FriendTalk.who(friend.user);
        body.addProperty("muted", muted);
        call(muted ? "friends mute" : "friends unmute", FriendOps.GRANTS, body);
    }

    /** Ask a friend's client to come to the server you are on. Needs their permission. */
    private void send(List<String> args) {
        if (args.isEmpty()) {
            shell.error("usage: friends send <who> [server]");
            return;
        }
        Friend friend = FriendList.find(args.get(0));
        if (friend == null) {
            shell.error("friends: nobody on your list called " + args.get(0));
            return;
        }

        String server = args.size() > 1 ? args.get(1)
            : com.mineux.core.social.ServerName.current(net.minecraft.client.Minecraft.getInstance());
        if (server.isEmpty()) {
            shell.error("friends send: you are not on a server - friends send <who> <server>");
            return;
        }
        if (!friend.theyAllowJoin) {
            shell.error("friends send: " + friend.display() + " has not allowed that");
            return;
        }

        JsonObject body = FriendTalk.who(friend.user);
        body.addProperty("server", server);
        call("friends send", FriendOps.REMOTE_JOIN, body);
    }

    /** Run a terminal command in a friend's client. Needs their permission. */
    private void remote(List<String> args) {
        if (args.size() < 2) {
            shell.error("usage: friends run <who> <command>");
            return;
        }
        Friend friend = FriendList.find(args.get(0));
        if (friend == null) {
            shell.error("friends: nobody on your list called " + args.get(0));
            return;
        }
        if (!friend.theyAllowCommand) {
            shell.error("friends run: " + friend.display() + " has not allowed that");
            return;
        }

        JsonObject body = FriendTalk.who(friend.user);
        body.addProperty("command", join(args, 1));
        call("friends run", FriendOps.REMOTE_COMMAND, body);
    }

    // ---- messages --------------------------------------------------------

    /**
     * {@code msg <who> <text>}, and with no text, the conversation so far.
     * <p>
     * Reading it is a fetch, because the history is the server's -- a message sent to you while
     * you were offline is waiting there and has never been through this client's stream.
     */
    private void message(String name, String text) {
        Friend friend = FriendList.find(name);
        if (friend == null) {
            shell.error(name.isEmpty() ? "usage: msg <who> <message>"
                : "msg: nobody on your list called " + name);
            return;
        }
        if (text.isBlank()) {
            // Opening the conversation reads its notifications in the bell.
            com.mineux.core.social.Notices.readFrom(friend.user);
            // The conversation as a tab in irc; its last lines printed where there is no terminal to open it in.
            if (!shell.launch(Shell.APP_IRC + ":@" + friend.user)) conversation(friend);
            lastFrom = friend.user;
            return;
        }

        lastFrom = friend.user;
        shell.line().t("to " + friend.display() + " ", CYAN).t(text, FG);

        JsonObject body = FriendTalk.who(friend.user);
        body.addProperty("text", text);
        Status row = DiscordCommands.row(shell, "", DIM, "sending...");
        DiscordCommands.work(() -> {
            JsonObject answer = FriendTalk.post(FriendOps.DM, body);
            row.set(answer == null ? fail() : "sent", answer == null ? RED : FAINT);
        });
    }

    /** {@code irc}, {@code irc public}, {@code irc @friend}: the chat, on that tab. */
    private void irc(String target) {
        String tab = target;
        if (target.startsWith("@")) {
            Friend friend = FriendList.find(target.substring(1));
            if (friend == null) {
                shell.error("irc: nobody on your list called " + target.substring(1));
                return;
            }
            tab = "@" + friend.user;
            com.mineux.core.social.Notices.readFrom(friend.user);
        }
        if (!shell.launch(Shell.APP_IRC + (tab.isEmpty() ? "" : ":" + tab))) shell.error("irc: needs a terminal - run it in one");
    }

    private void conversation(Friend friend) {
        Status head = DiscordCommands.row(shell, "msg " + friend.display() + " ", GOLD, "reading...");
        DiscordCommands.work(() -> {
            JsonObject answer = FriendTalk.conversation(friend.user);
            if (answer == null) {
                head.set(fail(), RED);
                return;
            }
            JsonArray messages = answer.getAsJsonArray("messages");
            if (messages == null || messages.isEmpty()) {
                head.set("nothing yet", DIM);
                return;
            }
            StringBuilder out = new StringBuilder();
            int from = Math.max(0, messages.size() - 8);
            for (int i = from; i < messages.size(); i++) {
                JsonElement element = messages.get(i);
                if (!element.isJsonObject()) continue;
                JsonObject row = element.getAsJsonObject();
                if (out.length() > 0) out.append("   ");
                out.append(row.get("mine").getAsBoolean() ? "you: " : friend.display() + ": ")
                    .append(row.get("body").getAsString());
            }
            head.set(out.toString(), FG);
        });
        lastFrom = friend.user;
    }

    private void log() {
        List<String> lines = FriendList.log();
        if (lines.isEmpty()) {
            shell.line().t("friends log ", GOLD).t("nothing yet", DIM);
            return;
        }
        int from = Math.max(0, lines.size() - 20);
        for (int i = from; i < lines.size(); i++) {
            String line = lines.get(i);
            int colour = line.startsWith("!") ? RED : line.startsWith("*") ? DIM : FG;
            shell.line().t("  " + line, colour);
        }
    }

    // ---- plumbing --------------------------------------------------------

    /**
     * Prints a row, calls off the terminal's thread, and fills the row in with what the server
     * said. The list itself arrives separately on the stream, so a call that worked has nothing
     * to report beyond having worked.
     */
    private void call(String label, String path, JsonObject body) {
        Status row = DiscordCommands.row(shell, label + " ", GOLD, "...");
        DiscordCommands.work(() -> {
            JsonObject answer = FriendTalk.post(path, body);
            row.set(answer == null ? fail() : "ok", answer == null ? RED : GREEN);
        });
    }

    private static String fail() {
        String why = FriendList.callError();
        return why.isEmpty() ? "failed" : why;
    }

    /** The service's field names are camelCase; the terminal accepts any case. */
    private static String camel(String field) {
        if (field == null) return "sharePresence";
        if (field.equalsIgnoreCase("remotejoin")) return "remoteJoin";
        if (field.equalsIgnoreCase("remotecommand")) return "remoteCommand";
        if (field.equalsIgnoreCase("screens")) return "screens";
        return "sharePresence";
    }

    private static String join(List<String> args, int from) {
        return args.size() <= from ? "" : String.join(" ", args.subList(from, args.size())).trim();
    }

    private static String pad(String text, int width) {
        if (text.length() >= width) return text.substring(0, width - 1) + " ";
        StringBuilder out = new StringBuilder(text);
        while (out.length() < width) out.append(' ');
        return out.toString();
    }
}
