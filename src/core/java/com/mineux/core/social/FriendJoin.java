package com.mineux.core.social;

import com.mineux.core.ui.term.Notify;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * Going where a friend is.
 * <p>
 * Every path into this is on the client thread -- {@link FriendNet#handoff} is how the stream
 * thread gets here. Connecting from any other thread tears a world down from under the renderer.
 */
public final class FriendJoin {

    private FriendJoin() {}

    /**
     * Connects to an address, from the client thread.
     *
     * @return what happened, for a caller with somewhere to print it
     */
    public static String connect(String address) {
        if (address == null || address.isBlank()) return "no address to join";

        Minecraft mc = Minecraft.getInstance();
        String target = address.trim();
        if (ServerName.on(mc, target)) return "you are already on " + target;

        try {
            ServerData data = new ServerData("friend", target, ServerData.Type.OTHER);
            ServerAddress parsed = ServerAddress.parseString(target);
            // The fallback screen is the server list over the title screen, which is where a
            // player would expect to land if the connect fails -- not wherever they were.
            ConnectScreen.startConnecting(
                new JoinMultiplayerScreen(new TitleScreen()), mc, parsed, data, false, null);
            Notify.show("friends", "joining " + target, Notify.GOOD);
            return "joining " + target;
        } catch (RuntimeException e) {
            Notify.show("friends", "could not join " + target, Notify.BAD);
            return "could not join " + target;
        }
    }
}
