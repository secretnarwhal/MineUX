package com.mineux.core.social;

import com.mineux.core.ui.term.FriendBanner;
import com.mineux.core.ui.term.Notify;
import com.mineux.core.ui.term.RemoteShell;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;

/**
 * A friend driving your client, with permission you gave them.
 * <p>
 * This is the sharpest thing in the client and it is written on the assumption that the person
 * on the other end is hostile. The rules, in the order they are enforced:
 * <ol>
 *   <li><b>The server checks first.</b> {@code /remote/join} and {@code /remote/command} read
 *       the permission off <em>your</em> row at the moment of the send. An instruction that
 *       arrives here has already been authorised once by something the sender cannot edit.</li>
 *   <li><b>This client checks again, independently.</b> The grant is re-read from the friends
 *       list that the server pushed, not from the instruction. A frame that claims to be from
 *       somebody who is not on the list, or who is on it without the grant, is dropped. Trusting
 *       the first check alone would mean a bug in one place is the whole security model.</li>
 *   <li><b>Nothing happens quietly.</b> Every accepted instruction raises a banner naming who
 *       did it, and every refused one is logged. There is no silent mode and there should never
 *       be one -- a remote control you cannot see being used is a backdoor.</li>
 *   <li><b>Commands go to the terminal.</b> A remote command runs in the terminal, never
 *       straight to a game server. The terminal's {@code say} does put a line on the server, so a
 *       friend with the command grant can talk -- and run server commands -- as you; that is
 *       what the warning shown before granting it says, in those words.</li>
 *   <li><b>Nobody remote hands out grants.</b> {@code friends set} and {@code friends allow}
 *       are refused from here whatever the grant, so a friend with the command grant cannot
 *       give it, or anything else, to somebody else.</li>
 * </ol>
 * Everything here runs on the client thread: {@link FriendNet#handoff} is how the stream thread
 * gets in.
 */
public final class Remote {

    private Remote() {}

    /** No more than one instruction a second from anybody, however many they send. */
    private static final long MIN_GAP_MS = 1_000;

    private static long lastAtMs;

    /**
     * A friend asking your client to join a server.
     * <p>
     * It connects, which disconnects you from wherever you are. That is exactly as rude as it
     * sounds, which is why the grant is off by default and per friend.
     */
    static void join(String from, String server) {
        Friend friend = allowed(from, GRANT_JOIN);
        if (friend == null) return;
        if (server == null || server.isBlank()) return;

        if (ServerName.on(Minecraft.getInstance(), server)) {
            // Already there. Obeying would disconnect and reconnect for nothing, which is worse
            // than ignoring it -- and mid-fight it would be much worse.
            FriendList.note("* " + friend.display() + " sent you to " + server + " (already there)");
            return;
        }

        FriendList.note("* " + friend.display() + " sent you to " + server);
        FriendBanner.note(friend.display() + " sent you to", server, FriendBanner.ALERT);
        FriendJoin.connect(server);
    }

    /**
     * A friend running a terminal command in your client.
     * <p>
     * <b>It goes to the terminal and nowhere else</b> -- but the terminal is the whole client.
     * Its {@code say} speaks in chat and runs a server's /commands, so a friend with this grant
     * can talk and act as you on whatever server you are on. That is why the grant is off by
     * default, per friend, and only given after a prompt that says exactly this
     * ({@code FriendCommands.COMMAND_WARNING}); and why every command shows a banner.
     */
    static void command(String from, String command) {
        Friend friend = allowed(from, GRANT_COMMAND);
        if (friend == null) return;
        if (command == null || command.isBlank()) return;

        String line = command.trim();
        // A bare leading slash is a server command typed into the wrong box: no terminal
        // command starts with one. Server commands go through `say /...`, on purpose.
        if (line.startsWith("/")) {
            FriendList.note("! " + friend.display() + " tried to run a server command: " + line);
            Notify.show("friends", friend.display() + " tried to run a server command", Notify.BAD);
            return;
        }

        if (grants(line)) {
            FriendList.note("! " + friend.display() + " tried to change your friend permissions: " + line);
            Notify.show("friends", friend.display() + " tried to change your friend permissions", Notify.BAD);
            return;
        }

        FriendList.note("* " + friend.display() + " ran: " + line);
        FriendBanner.note(friend.display() + " ran a command", line, FriendBanner.ALERT);
        RemoteShell.run(line);
    }

    private static final int GRANT_JOIN = 0, GRANT_COMMAND = 1;

    private static final List<String> FRIEND_WORDS = List.of("friends", "friend", "f");
    private static final List<String> GRANT_WORDS = List.of("set", "allow");

    /** Whether any command in a line edits a friend's permissions: {@code friends set ...}. */
    private static boolean grants(String line) {
        for (String part : line.split(";|&&")) {
            String[] words = part.trim().replace("\"", "").toLowerCase(Locale.ROOT).split("\\s+");
            if (words.length >= 2 && FRIEND_WORDS.contains(words[0]) && GRANT_WORDS.contains(words[1])) return true;
        }
        return false;
    }

    /**
     * The friend behind a name, if they are on the list and hold the grant.
     *
     * @param grant which one: {@link #GRANT_JOIN} or {@link #GRANT_COMMAND}
     * @return the friend, or null -- having said why
     */
    private static Friend allowed(String from, int grant) {
        if (from == null || from.isEmpty()) return null;

        Friend friend = FriendList.find(from);
        if (friend == null) {
            // The server should never route this. If it does, something is wrong and saying so
            // is more useful than ignoring it.
            FriendList.note("! refused a remote from " + from + ", who is not on your list");
            Notify.show("friends", "refused a remote from " + from, Notify.BAD);
            return null;
        }
        if (friend.muted) return null;

        boolean granted = grant == GRANT_JOIN ? friend.allowJoin : friend.allowCommand;
        if (!granted) {
            FriendList.note("! refused a remote from " + friend.display() + ": not allowed");
            Notify.show("friends", "refused a remote from " + friend.display(), Notify.BAD);
            return null;
        }

        long now = System.currentTimeMillis();
        if (now - lastAtMs < MIN_GAP_MS) {
            FriendList.note("! ignored a remote from " + friend.display() + ": too fast");
            return null;
        }
        lastAtMs = now;
        return friend;
    }
}
