package com.mineux.core.rpc;

/**
 * Something an extension is doing that the Discord presence should say: added through
 * {@code DeskExtensions.addPresenceSource}. Asked once a second; the last one added with
 * something to say wins. An abstract class, not an interface (no lambdas).
 */
public abstract class PresenceSource {

    /** The card's second line now ("autoplay > in a match"), or null for nothing to say. */
    public abstract String doing();

    /** The small badge's asset key while it is saying something, or null to keep the desktop's. */
    public String badge() {
        return null;
    }

    /**
     * Whether {@link #badge} goes over the desktop's badge even while {@link #doing} has nothing
     * to say: a badge for where you are (a workspace, say) that leaves the card's line as it was.
     * Default false: the badge only comes with a line.
     */
    public boolean badgeAlone() {
        return false;
    }

    /** The badge's hover text. */
    public String badgeText() {
        return null;
    }
}
