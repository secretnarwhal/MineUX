package com.mineux.core.social;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import java.util.Locale;

/**
 * Server addresses, in the one form two spellings of the same place agree on.
 * <p>
 * {@code 2b2t.org} and {@code 2b2t.org:25565} are the same server, and a friends list where one
 * person's client said each would show them in two different places and refuse to notice they
 * were together. Everything that compares addresses goes through here.
 */
public final class ServerName {

    private ServerName() {}

    /** The address with its port made explicit and its case flattened, or empty. */
    public static String normalise(String address) {
        if (address == null || address.isBlank()) return "";
        try {
            ServerAddress parsed = ServerAddress.parseString(address.trim());
            return (parsed.getHost() + ":" + parsed.getPort()).toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return address.trim().toLowerCase(Locale.ROOT);
        }
    }

    /** The address this client is connected to, as typed, or empty off a server. */
    public static String current(Minecraft mc) {
        if (mc == null || mc.getCurrentServer() == null) return "";
        String address = mc.getCurrentServer().ip;
        return address == null ? "" : address;
    }

    /** Whether the client is already on this address, however either was written. */
    public static boolean on(Minecraft mc, String address) {
        String current = current(mc);
        return !current.isEmpty() && normalise(current).equals(normalise(address));
    }
}
