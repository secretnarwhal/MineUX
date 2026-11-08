package com.mineux.core.ui.term;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.network.EventLoopGroupHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The multiplayer servers, for the main menu's servers window and the {@code join} command:
 * vanilla's own saved list ({@code servers.dat}), pinged the way vanilla's list pings them,
 * and joined through vanilla's connect screen, so disconnecting comes back to the menu.
 */
public final class Servers {

    private static ServerList list;
    private static final ServerStatusPinger PINGER = new ServerStatusPinger();
    private static final ExecutorService PINGS = Executors.newFixedThreadPool(5, runnable -> {
        Thread thread = new Thread(runnable, "mineux-server-ping");
        thread.setDaemon(true);
        return thread;
    });

    private Servers() {}

    /** Reads servers.dat again and pings everything on it. */
    static void refresh() {
        Minecraft mc = Minecraft.getInstance();
        PINGER.removeAll();
        list = new ServerList(mc);
        list.load();
        for (ServerData server : all()) ping(server);
    }

    static List<ServerData> all() {
        if (list == null) {
            list = new ServerList(Minecraft.getInstance());
            list.load();
        }
        List<ServerData> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) out.add(list.get(i));
        return out;
    }

    private static void ping(ServerData server) {
        server.setState(ServerData.State.PINGING);
        Minecraft mc = Minecraft.getInstance();
        PINGS.submit(() -> {
            try {
                PINGER.pingServer(server, () -> {}, () -> {}, EventLoopGroupHolder.remote(mc.options.useNativeTransport()));
            } catch (Exception e) {
                server.setState(ServerData.State.UNREACHABLE);
            }
        });
    }

    /** Keeps the pings in flight moving. */
    static void tick() {
        PINGER.tick();
    }

    /** From the client tick, so pings land wherever the list is being looked at -- a window or a completion. */
    public static void onClientTick() {
        if (list != null) PINGER.tick();
    }

    /** Pings every saved server not pinged yet this session, so their icons and counts come in. */
    static void pingUnpinged() {
        for (ServerData server : all()) {
            if (server.state() == ServerData.State.INITIAL) ping(server);
        }
    }

    /** Drops every ping in flight, when the window closes or the game leaves the menu. */
    static void stop() {
        PINGER.removeAll();
    }

    /** By its number in the list, its name, or its address. */
    static ServerData find(String which) {
        List<ServerData> all = all();
        try {
            int index = Integer.parseInt(which) - 1;
            if (index >= 0 && index < all.size()) return all.get(index);
        } catch (NumberFormatException ignored) {
        }
        for (ServerData s : all) if (s.name.equalsIgnoreCase(which) || s.ip.equalsIgnoreCase(which)) return s;
        // An address that is not one of the saved ones is a direct connect, not a prefix of a name.
        if (looksLikeAddress(which)) return null;
        String lower = which.toLowerCase(Locale.ROOT);
        ServerData prefixed = null;
        for (ServerData s : all) {
            if (s.name.toLowerCase(Locale.ROOT).startsWith(lower)) {
                if (prefixed != null) return null;
                prefixed = s;
            }
        }
        return prefixed;
    }

    /**
     * Connects, backing out to the title screen on a disconnect. From inside a world it leaves
     * that world first, the way the pause menu's disconnect button does.
     */
    static void join(ServerData server, Screen title) {
        Minecraft mc = Minecraft.getInstance();
        stop();
        if (mc.level != null) leave();
        ConnectScreen.startConnecting(title, mc, ServerAddress.parseString(server.ip), server, false, null);
    }

    /** Leaves the world or server, saving a singleplayer one, and lands on the title screen. */
    static void leave() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // Minecraft#disconnectFromWorld, less where it lands: vanilla opens its own server list
        // (or Realms) after a multiplayer disconnect; here every disconnect lands on the title
        // screen, which is the home desk. The server list is never even opened.
        boolean local = mc.isLocalServer();
        mc.level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
        if (local) mc.disconnectWithSavingScreen();
        else mc.disconnectWithProgressScreen();
        mc.gui.setScreen(new TitleScreen());
    }

    /** Whether a {@code join} argument reads as an address rather than a saved server's name. */
    static boolean looksLikeAddress(String text) {
        return text.contains(".") || text.contains(":") || text.equalsIgnoreCase("localhost");
    }

    /** @return why the address is no good, or null once connecting */
    static String joinAddress(String address, Screen title) {
        if (!ServerAddress.isValidAddress(address)) return "'" + address + "' is not a server address";
        join(new ServerData(address, address, ServerData.Type.OTHER), title);
        return null;
    }

    /** @return why it was not added, or null */
    static String add(String name, String address) {
        if (!ServerAddress.isValidAddress(address)) return "'" + address + "' is not a server address";
        all();
        ServerData server = new ServerData(name, address, ServerData.Type.OTHER);
        list.add(server, false);
        list.save();
        ping(server);
        return null;
    }

    static void remove(ServerData server) {
        all();
        list.remove(server);
        list.save();
    }

    /** The first line of the message of the day, as plain text. */
    static String motd(ServerData server) {
        if (server.motd == null) return "";
        String text = server.motd.getString().replace('\r', ' ');
        int nl = text.indexOf('\n');
        return (nl >= 0 ? text.substring(0, nl) : text).trim();
    }
}
