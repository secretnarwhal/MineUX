package com.mineux.core.social;

import com.google.gson.JsonObject;
import com.mineux.core.net.MuxApi;
import net.minecraft.client.Minecraft;

/**
 * The friends list's job per tick: run whatever the stream thread left, and tell the service
 * where you are when that changes.
 * <p>
 * Called from {@code LiveFeatureHandler} on {@code OP_TICK}, below the entitlement gate with
 * everything else that is gameplay. It is also where the stream gets opened, rather than at
 * startup: the stream needs a session and an entitlement, and the tick is the first place both
 * are known to be true.
 * <p>
 * <b>Presence is sent on change, not on a timer.</b> A client idling on one server for an hour
 * sends one update, not three and a half thousand. The server's own fan-out has the same rule,
 * so an unchanged presence costs nothing anywhere.
 */
public final class FriendTick {

    private FriendTick() {}

    /**
     * How long after joining a server to look at the scoreboard.
     * <p>
     * The sidebar is the second-best gamemode clue and it does not exist yet at the moment you
     * connect -- the server sets it a moment later, and on a big network it can be several
     * seconds. So the address is reported at once and the mode is re-checked for a while after.
     */
    private static final long SETTLE_MS = 12_000;

    /** Between re-checks during that window. Cheap, but not free, so not every tick. */
    private static final long RECHECK_MS = 1_500;

    private static String lastServer = "";
    private static String lastGamemode = "";
    private static String lastName = "";
    private static long arrivedAtMs;
    private static long lastCheckMs;

    public static void onClientTick(Minecraft mc) {
        // Another MineUX account (or none) since the last tick: the streams follow it first.
        AccountWatch.check();
        // Each extension chatroom's stream follows its own sign-in, friends switched on or not.
        ChatNet.tick();
        FriendConfig config = FriendConfig.get();
        // The one flag the per-entity friend check reads, copied out of the synchronized getter
        // once a tick rather than once an entity. See FriendList#isFriend.
        FriendList.setFriendly(config.friendly);

        if (!config.enabled) {
            if (FriendNet.started()) FriendNet.stop();
            return;
        }
        if (config.autoConnect && !FriendNet.started() && MuxApi.signedIn()) {
            FriendNet.start();
        }

        // Anything the stream thread wanted done on this thread: a chat line, a connect, a
        // remote instruction.
        FriendNet.drain(FriendTick::run);

        if (!FriendList.connected()) return;

        String server = config.sharePresence ? ServerName.current(mc) : "";
        // Told to the list so it can work out which friends are standing next to you, whether
        // or not you are sharing that fact with them.
        FriendList.setHere(ServerName.current(mc));

        long now = System.currentTimeMillis();
        boolean moved = !server.equals(lastServer);
        if (moved) arrivedAtMs = now;

        // Re-read the mode while it is still settling, then leave it alone.
        boolean settling = now - arrivedAtMs < SETTLE_MS && now - lastCheckMs > RECHECK_MS;
        if (!moved && !settling) return;
        lastCheckMs = now;

        String gamemode = server.isEmpty() ? "" : Gamemode.detect(mc);
        String name = mc.getUser() == null ? "" : mc.getUser().getName();

        if (!moved && gamemode.equals(lastGamemode) && name.equals(lastName)) return;

        lastServer = server;
        lastGamemode = gamemode;
        lastName = name;

        JsonObject body = new JsonObject();
        body.addProperty("server", server);
        body.addProperty("gamemode", gamemode);
        body.addProperty("mc", name);
        body.addProperty("uuid", mc.player == null ? "" : mc.player.getUUID().toString());
        FriendNet.async(() -> FriendNet.post(FriendOps.PRESENCE, body));
    }

    private static void run(Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            // A bad frame, or a remote instruction that threw, must not take the tick down.
            com.mineux.core.MineUX.LOGGER.warn("a friends handoff failed", e);
        }
    }
}
