package com.mineux.core;

/**
 * Takes over {@code reboot} (after its yes/no question), for a host that can do better than
 * MineUX's close-then-start: start the new game first and hand over to it, say. Set once
 * through {@link DeskExtensions#setRebootHook}; with none, MineUX's own reboot runs (the game
 * closes, a helper starts it again). Pair it with a {@link ShutdownLayer} to draw the desktop
 * going down meanwhile. An abstract class, not an interface (no lambdas).
 */
public abstract class RebootHook {

    /** Why a reboot cannot start now (one is under way already, say), or null when it can. */
    public String busy() {
        return null;
    }

    /**
     * Whether this hook takes this reboot. {@code mustClose}: a singleplayer world is open,
     * which only closing the game saves. Default: every reboot but that one; the rest are
     * MineUX's close-then-start.
     */
    public boolean takes(boolean mustClose) {
        return !mustClose;
    }

    /** The detail line under the yes/no question when this hook takes the reboot. */
    public String detail() {
        return "the new game loads while this one fades out";
    }

    /** Answered yes: start it. Why not, or null once it is under way. On the client thread. */
    public abstract String reboot();
}
