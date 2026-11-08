package com.mineux.core.ui.term;

/**
 * Hyprland's workspace slide: on a switch the desk being left slides off one side while the
 * one arriving slides in from the other, the bar and the backdrop staying put. Going right in
 * the bar slides the new desk in from the right; going left, from the left.
 * <p>
 * Noticed rather than announced: whichever way the desk changed (the bar, SUPER+n, {@code ws
 * open}, Alt+Esc), the next frame draws a different desk from the last one, and that starts it.
 */
final class Slide {

    private Desk shown;
    private int shownOrder;
    /** The desk sliding away, or null when nothing is sliding. */
    private Desk from;
    private int dir;
    private long start;
    /** The next switch drops the arriving desk in from the top, over the one being left. */
    private boolean dropNext;
    /** The slide under way is such a drop. */
    private boolean drop;

    /** Asks for the next switch to be a drop: the special workspace's first appearance. */
    void dropNext() {
        dropNext = true;
    }

    /** Whether the slide under way is a drop rather than a slide sideways. */
    boolean dropping() {
        return drop;
    }

    /**
     * Notes the desk drawn this frame and its place in the bar; a different desk from last
     * frame's starts a slide away from that one.
     */
    void track(Desk desk, int order, long now) {
        if (shown != null && shown != desk) {
            from = shown;
            drop = dropNext;
            dropNext = false;
            dir = order >= shownOrder ? 1 : -1;
            // Started on the first frame drawn, not at the switch: loading a workstation's
            // config can stall that frame, and the slide would lose its start to the stall.
            start = now;
        }
        shown = desk;
        shownOrder = order;
    }

    /** The eased share of the way, 0 to 1; 1 with nothing sliding. */
    private float eased(long now) {
        if (from == null) return 1f;
        // hyprland.conf's workspaces animation: its speed and curve.
        HyprConf conf = HyprConf.get();
        String animation = drop ? "specialWorkspace" : "workspaces";
        long ms = conf.ms(animation);
        float t = ms <= 0 ? 1f : Math.min(1f, (now - start) / (float) ms);
        if (t >= 1f) {
            from = null;
            drop = false;
            return 1f;
        }
        return conf.curve(animation, t);
    }

    /** How far through the slide is, along its curve: 0 at the start, 1 done. */
    float progress(long now) {
        return eased(now);
    }

    /** How far right (or down) to draw the arriving desk this frame, in GUI px. */
    float arriving(long now, int width) {
        return dir * (1f - eased(now)) * width;
    }

    /** How far right to draw the leaving desk this frame, in GUI px: a width beside the arriving one. */
    float leaving(long now, int width) {
        return arriving(now, width) - dir * width;
    }

    /** Whether a slide is still under way this frame; one that has run its time ends here. */
    boolean running(long now) {
        return eased(now) < 1f;
    }

    /** The desk sliding away, while {@link #running} says a slide is under way. */
    Desk from() {
        return from;
    }
}
