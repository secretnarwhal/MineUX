package com.mineux.core.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mineux.core.ui.term.FriendBanner;
import com.mineux.core.ui.term.Notify;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * One stream frame, turned into state.
 * <p>
 * Split out of {@link FriendNet} rather than written as a switch inside it for the reason the
 * terminal's {@code Shell} is split into small dispatchers: an obfuscator turns every
 * string case into a block of decryption code, and a class that grows one big switch over frame
 * names eventually blows the JVM's 64KB method limit and gets written out unobfuscated -- which
 * breaks every call into it.
 * <p>
 * Everything here runs on the stream thread, and nothing here may touch the game: what has to
 * reach Minecraft goes through {@link FriendNet#handoff}. {@link Notify} and
 * {@link FriendBanner} are the exceptions and are safe -- both are synchronized and only append
 * to a list the render thread reads.
 */
final class FriendEvents {

    private FriendEvents() {}

    static void handle(String event, JsonObject json) {
        if (java.util.Objects.equals(FriendOps.EV_FRIENDS, event)) {
            friends(json);
        } else if (java.util.Objects.equals(FriendOps.EV_DM, event)) {
            dm(json);
        } else if (java.util.Objects.equals(FriendOps.EV_HELLO, event)) {
            hello(json);
        } else if (java.util.Objects.equals(FriendOps.EV_REQUESTS, event)) {
            requests(json);
        } else if (java.util.Objects.equals(FriendOps.EV_REQUEST, event)) {
            request(json);
        } else if (java.util.Objects.equals(FriendOps.EV_REMOTE, event)) {
            remote(json);
        } else if (java.util.Objects.equals(FriendOps.EV_NOTIFY, event)) {
            Notices.onCount(FriendNet.integer(json, "unread"));
        } else if (java.util.Objects.equals(FriendOps.EV_BYE, event)) {
            bye(json);
        }
        // An event this build does not know about is ignored, not an error: the service is
        // additive and a shipped jar cannot be redeployed the way it can.
    }

    private static void hello(JsonObject json) {
        FriendList.onConnected(
            FriendNet.string(json, "user"),
            FriendNet.integer(json, "uid"),
            FriendNet.integer(json, "unread"));
        Notices.onCount(FriendNet.integer(json, "notifications"));
    }

    /**
     * The whole list. Also the only place an arrival can be noticed.
     * <p>
     * The server sends the list and not a delta -- one frame shape is one thing to get right --
     * so "X has turned up somewhere" is a diff taken here against the list this frame replaces.
     */
    private static void friends(JsonObject json) {
        List<Friend> list = parse(FriendNet.array(json, "friends"));
        List<Friend> before = FriendList.all();
        FriendList.onFriends(list);
        for (Friend friend : list) announce(friend, before);
    }

    /** The rows of a {@code friends} frame or of {@code /list}. */
    static List<Friend> parse(JsonArray raw) {
        List<Friend> list = new ArrayList<>(raw.size());

        for (JsonElement element : raw) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            list.add(new Friend(
                FriendNet.integer(row, "uid"),
                FriendNet.string(row, "user"),
                FriendNet.string(row, "nickname"),
                FriendNet.string(row, "since"),
                FriendNet.bool(row, "online"),
                FriendNet.string(row, "server"),
                FriendNet.string(row, "gamemode"),
                FriendNet.string(row, "mc"),
                FriendNet.string(row, "uuid"),
                FriendNet.string(row, "lastMc"),
                FriendNet.string(row, "lastUuid"),
                FriendNet.string(row, "lastAt"),
                FriendNet.bool(row, "allowJoin"),
                FriendNet.bool(row, "allowCommand"),
                FriendNet.bool(row, "sharing"),
                FriendNet.bool(row, "muted"),
                FriendNet.bool(row, "screens"),
                FriendNet.bool(row, "theyAllowJoin"),
                FriendNet.bool(row, "theyAllowCommand"),
                FriendNet.bool(row, "theyScreens")));
        }
        return list;
    }

    /**
     * The banner, if this friend has just arrived somewhere.
     * <p>
     * Three things are deliberately silent, and between them they are most of what the server
     * sends:
     * <ul>
     *   <li><b>A friend who was not on the previous list.</b> Signing in should not fire a
     *       banner for every one of your friends who happens to be playing.</li>
     *   <li><b>A friend leaving a server.</b> Going to the menu is not news and there is
     *       nowhere to follow them to.</li>
     *   <li><b>A friend joining the server you are already on.</b> This is the common case --
     *       people play together -- and a banner saying somebody is where you can already see
     *       them is noise.</li>
     * </ul>
     */
    private static void announce(Friend now, List<Friend> before) {
        if (!now.online || now.server.isEmpty() || now.muted) return;

        Friend was = null;
        for (Friend each : before) {
            if (each.user.equalsIgnoreCase(now.user)) {
                was = each;
                break;
            }
        }
        if (was == null) return;
        if (ServerName.normalise(was.server).equals(ServerName.normalise(now.server))) return;
        if (ServerName.on(Minecraft.getInstance(), now.server)) return;

        String where = now.where();
        FriendList.note("* " + now.display() + " is playing " + where);

        if (FriendConfig.get().banner) {
            // Two lines: who, then where. The second is the one you act on, so it carries the
            // address and the mode rather than being a restatement of the first.
            FriendBanner.show(now.display() + " is playing", where);
        }
        if (FriendConfig.get().chatInGame) {
            toChat("\u00a7b[friends] \u00a7f" + now.display() + "\u00a77 is playing \u00a7f" + where
                + "\u00a78  -  \u00a7bfriend join " + now.user);
        }
    }

    private static void requests(JsonObject json) {
        FriendList.onRequests(users(FriendNet.array(json, "requests")), users(FriendNet.array(json, "sent")));
    }

    private static List<String> users(JsonArray raw) {
        List<String> who = new ArrayList<>(raw.size());
        for (JsonElement element : raw) {
            if (!element.isJsonObject()) continue;
            who.add(FriendNet.string(element.getAsJsonObject(), "user"));
        }
        return who;
    }

    private static void request(JsonObject json) {
        String from = FriendNet.string(json, "from");
        if (from.isEmpty()) return;

        FriendList.note("* " + from + " wants to be your friend");
        FriendBanner.request(from + " wants to be your friend", "friend accept " + from);
        if (FriendConfig.get().chatInGame) {
            toChat("\u00a7b[friends] \u00a7f" + from + "\u00a77 wants to be your friend  -  \u00a7bfriend accept "
                + from);
        }
    }

    private static void dm(JsonObject json) {
        String from = FriendNet.string(json, "from");
        String text = FriendNet.string(json, "body");
        if (from.isEmpty() || text.isEmpty()) return;

        FriendList.note(from + ": " + text);
        FriendList.addUnread(1);
        com.mineux.core.social.Chat.dmIn(from, text);
        // At the top, two lines, and on the desktop a click on it opens the conversation.
        FriendBanner.message(from, text);
        if (FriendConfig.get().chatInGame) {
            toChat("\u00a7b[" + from + "] \u00a7f" + text);
        }
    }

    /**
     * A friend driving this client. Straight to the client thread, where {@link Remote} checks
     * the permission again before anything happens.
     */
    private static void remote(JsonObject json) {
        String kind = FriendNet.string(json, "kind");
        String from = FriendNet.string(json, "from");

        if (java.util.Objects.equals(FriendOps.REMOTE_KIND_JOIN, kind)) {
            String server = FriendNet.string(json, "server");
            FriendNet.handoff(() -> Remote.join(from, server));
        } else if (java.util.Objects.equals(FriendOps.REMOTE_KIND_COMMAND, kind)) {
            String command = FriendNet.string(json, "command");
            FriendNet.handoff(() -> Remote.command(from, command));
        }
    }

    private static void bye(JsonObject json) {
        String reason = FriendNet.string(json, "reason");
        if (!reason.isEmpty()) {
            FriendList.note("* " + reason);
            Notify.show("friends", reason, Notify.BAD);
        }
    }

    /**
     * A line in the game's chat, on the client thread.
     * <p>
     * {@code ChatListener#handleSystemMessage} rather than the chat component directly: the
     * component's {@code addMessage} is private (it is what {@code ChatComponentMixin} hooks for
     * the name spoof), and the listener is the public door every server system message already
     * comes through -- so a friends line is filtered, logged and spoofed exactly as one.
     */
    private static void toChat(String text) {
        FriendNet.handoff(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui == null || mc.level == null) return;
            mc.gui.chatListener().handleSystemMessage(Component.literal(text), false);
        });
    }
}
