package com.mineux.core.ui.term;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The main menu's boot sequence, straight after a sign-in, out of the login screen's fade to
 * black: the word art flickers on -- exactly where it sits at the top of the main menu's
 * terminal, because it is that terminal's own -- then the terminal, the tint and the bar come
 * up around it, the fetch beside the art types itself in, and {@code ls} runs. Any key or
 * click skips to the end.
 * <p>
 * Times are in ms from the fade.
 */
final class Boot {

    static final long LOGO_AT = 500, LOGO_STEADY = 1800, REVEAL_AT = 2400, REVEAL_MS = 750;
    static final long BAR_AT = 2550, BAR_MS = 320, FETCH_AT = 3150, LS_AT = 3950, END = 4400;

    private Boot() {}

    /** How far above the screen the bar is at {@code t}, sliding down and easing out; 0 once it is in. */
    static int barOffset(long t, int barH) {
        float left = 1f - Math.min(1f, (t - BAR_AT) / (float) BAR_MS);
        return Math.round(left * left * -barH);
    }

    /**
     * Black over everything, lifting as the desktop comes up, and the terminal's word art lit on
     * top of the black -- flickering on like a tube warming up, then steady -- in the very box
     * the terminal draws it in, so when the black has gone it is simply the terminal's art.
     *
     * @param artBox where the terminal last drew its art, {x, y, w, h}, or null
     */
    static void draw(GuiGraphicsExtractor g, Font font, long t, int width, int height, int[] artBox) {
        if (t >= REVEAL_AT + REVEAL_MS) return;
        float cover = t < REVEAL_AT ? 1f : 1f - Math.min(1f, (t - REVEAL_AT) / (float) REVEAL_MS);
        cover = cover * cover * (3 - 2 * cover);
        if (cover > 0f) {
            g.nextStratum();
            g.fill(0, 0, width, height, Math.round(cover * 255) << 24);
        }

        if (t < LOGO_AT || artBox == null) return;

        float bright = 1f;
        if (t < LOGO_STEADY) {
            float p = (t - LOGO_AT) / (float) (LOGO_STEADY - LOGO_AT);
            if (noise(t / 45) >= 0.2f + 0.8f * p) return;
            bright = 0.5f + 0.5f * p;
        }

        g.nextStratum();
        int[] drawn = ArtView.draw(g, font, artBox[0], artBox[1], artBox[2], artBox[3]);
        if (bright < 1f) {
            g.nextStratum();
            g.fill(drawn[0] - 4, drawn[1] - 4, drawn[0] + drawn[2] + 4, drawn[1] + drawn[3] + 4, Math.round((1f - bright) * 255) << 24);
        }
    }

    /** A steady random number per step, for the flicker. */
    private static float noise(long step) {
        long x = step * 0x9E3779B97F4A7C15L;
        x ^= x >>> 31;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 29;
        return (x >>> 40) / (float) (1L << 24);
    }
}
