package com.mineux.core.ui.term;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

/** The bar's small readouts beside the clock and the FPS: ping on a server, and memory in use. */
final class BarFacts {

    private static final long MB = 1024L * 1024L;

    private BarFacts() {}

    /** Your own latency on the server as the tab list has it; null off a server. */
    static String ping(Minecraft mc) {
        if (mc == null || mc.player == null || mc.isLocalServer()) return null;
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) return null;
        PlayerInfo info = connection.getPlayerInfo(mc.player.getUUID());
        return info == null ? null : info.getLatency() + " ms";
    }

    /** Heap in use of the most the JVM will take, as {@code 812/4096 MB}. */
    static String memory() {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / MB;
        return used + "/" + rt.maxMemory() / MB + " MB";
    }
}
