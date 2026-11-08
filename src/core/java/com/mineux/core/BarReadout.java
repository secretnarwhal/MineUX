package com.mineux.core;

/**
 * One readout in the bar, right of the tray and left of the clock, beside the FPS, ping and
 * memory: added through {@link DeskExtensions#addBarReadout}. Read every frame, so keep
 * {@link #text} cheap. An abstract class, not an interface (no lambdas: see {@link TrayGroup}).
 */
public abstract class BarReadout {

    /** What it shows now; null or empty hides it. */
    public abstract String text();

    /** Its colour, ARGB. */
    public int color() {
        return 0xFFD7DCE6;
    }

    /** Shown while it is hovered; null for none. */
    public String hint() {
        return null;
    }

    /** A muxsh command a click on it runs; null for none. */
    public String command() {
        return null;
    }
}
