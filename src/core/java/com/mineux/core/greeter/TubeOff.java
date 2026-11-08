package com.mineux.core.greeter;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The screen switching off like an old tube, over black: a bright band squeezed to a line, the
 * line pulled in to a dot, the dot fading. The greeter's F1 shutdown ends this way, and the
 * desktop's {@code reboot} and {@code shutdown} can share it, so everything goes down alike.
 */
public final class TubeOff {

    private static final int GLOW = 0xFFF6F0DC;

    private TubeOff() {}

    /** @param off 0 (just starting) to 1 (gone) */
    public static void draw(GuiGraphicsExtractor g, int width, int height, float off) {
        g.fill(0, 0, width, height, 0xFF000000);
        int cx = width / 2, cy = height / 2;
        if (off < 0.2f) {
            float s = off / 0.2f;
            int half = Math.max(1, Math.round(6 * (1f - s)));
            g.fill(0, cy - half, width, cy + half, alpha(GLOW, 0.35f + 0.65f * s));
        } else if (off < 0.75f) {
            float s = (off - 0.2f) / 0.55f;
            float left = (1f - s) * (1f - s);
            int half = Math.max(1, Math.round(width / 2f * left));
            g.fill(cx - half, cy - 3, cx + half, cy + 3, alpha(GLOW, 0.12f));
            g.fill(cx - half, cy - 1, cx + half, cy + 1, GLOW);
        } else if (off < 1f) {
            float s = (off - 0.75f) / 0.25f;
            g.fill(cx - 3, cy - 3, cx + 3, cy + 3, alpha(GLOW, 0.15f * (1f - s)));
            g.fill(cx - 1, cy - 1, cx + 1, cy + 1, alpha(GLOW, 1f - s));
        }
    }

    /** {@code argb} with its alpha scaled by {@code a} (0..1). */
    public static int alpha(int argb, float a) {
        int out = Math.round((argb >>> 24) * Math.max(0f, Math.min(1f, a)));
        return out << 24 | (argb & 0xFFFFFF);
    }

    /** Smoothstep, 0..1 to 0..1. */
    public static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }
}
