package com.mineux.visuals.world;

import static com.mineux.visuals.VisualOps.*;

/**
 * No render: the overlays and effects in the way of seeing the fight, left undrawn -- the
 * scoreboard, the nausea wobble, the portal swirl, the spyglass mask, the vignette, a popped
 * totem's spin across your screen, the flames while you burn, and explosions' particles. What
 * they stand for still happens: you still burn, still have nausea; it is only not drawn.
 */
public final class NoRender {

    private NoRender() {}

    /** Whether the piece {@code code} names (a {@code HIDE_} code) is to be left undrawn. */
    static boolean hides(int code) {
        if (!World.allowed()) return false;
        WorldConfig config = WorldConfig.get();
        if (!config.noRender) return false;
        if (code == HIDE_SCOREBOARD) return config.hideScoreboard;
        if (code == HIDE_NAUSEA) return config.hideNausea;
        if (code == HIDE_PORTAL) return config.hidePortal;
        if (code == HIDE_SPYGLASS) return config.hideSpyglass;
        if (code == HIDE_VIGNETTE) return config.hideVignette;
        if (code == HIDE_TOTEM_ANIMATION) return config.hideTotemAnimation;
        if (code == HIDE_FIRE) return config.hideFire;
        if (code == HIDE_EXPLOSIONS) return config.hideExplosions;
        return false;
    }
}
