package com.mineux.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The core's state: the host it runs on and what has been added to it ({@link Extensions}).
 * {@link #init} once, before any {@link Hooks} call does anything.
 */
public final class MineUX {

    public static final Logger LOGGER = LoggerFactory.getLogger("MineUX");

    /** Editions, as strings: an enum's constants are renamed when core is obfuscated. */
    public static final String FULL = "full";
    public static final String LITE = "lite";

    private static Host host;

    private MineUX() {}

    public static void init(Host running) {
        if (host != null) throw new IllegalStateException("MineUX is already running on " + host);
        host = running;
        LOGGER.info("MineUX core up: {} edition", running.edition());
    }

    /** Whether {@link #init} has run. Every hook is a no-op before it. */
    public static boolean ready() {
        return host != null;
    }

    public static Host host() {
        if (host == null) throw new IllegalStateException("MineUX.init has not run");
        return host;
    }

    public static boolean full() {
        String edition = host == null ? null : host.edition();
        return edition != null && edition.equals(FULL);
    }

    /** The host's shell name ({@link Host#shellName}), MineUX's before {@link #init}. */
    public static String shellName() {
        return host == null ? "muxsh" : host.shellName();
    }

    /** The host's name for the special workspace ({@link Host#specialName}), MineUX's before {@link #init}. */
    public static String specialName() {
        return host == null ? "special" : host.specialName();
    }

    /** The host's desk key ({@link Host#deskKey}), right shift before {@link #init}. */
    public static int deskKey() {
        return host == null ? org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT : host.deskKey();
    }
}
