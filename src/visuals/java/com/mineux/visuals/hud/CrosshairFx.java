package com.mineux.visuals.hud;

import com.mineux.visuals.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The crosshair's answers to your hits, drawn round its middle in its own units: the marker
 * (four short bars on the diagonals, out and faded), the shockwave (a ring running out), and
 * a kill's burst (a wide ring and a cross).
 */
final class CrosshairFx {

    private static final float HIT_S = 0.35F;
    private static final float SHOCK_S = 0.45F;
    private static final float KILL_S = 0.7F;
    private static final int OUTLINE = 0xB0050508;

    private CrosshairFx() {}

    static void effects(GuiGraphicsExtractor g, SkinConfig config, float gap, float hitAge, float killAge, int hit) {
        float thick = Math.max(1.0F, config.chThickness);
        if (config.chHitmarker && hitAge < HIT_S) {
            float left = 1.0F - hitAge / HIT_S;
            float r0 = gap + 4.0F + 2.0F * (1.0F - left);
            for (int i = 0; i < 4; i++) {
                float deg = 45.0F + i * 90.0F;
                if (config.chOutline) Strokes.spoke(g, deg, r0 - 1.0F, r0 + 6.0F, thick + 2.0F, Theme.fade(OUTLINE, left));
                Strokes.spoke(g, deg, r0, r0 + 5.0F, thick, Theme.alpha(hit, left));
            }
        }
        if (config.chShockwave && hitAge < SHOCK_S) {
            float p = hitAge / SHOCK_S;
            float radius = gap + config.chSize + 30.0F * Strokes.easeOut(p);
            Strokes.ring(g, radius, 1.5F * (1.0F - p) + 0.5F, Theme.alpha(hit, (1.0F - p) * 0.8F));
        }
        if (config.chKill && killAge < KILL_S) {
            float p = killAge / KILL_S;
            float left = 1.0F - p;
            float grow = Strokes.easeOut(p);
            Strokes.ring(g, 6.0F + 40.0F * grow, 3.0F * left + 0.5F, Theme.alpha(hit, left));
            Strokes.ring(g, 4.0F + 22.0F * grow, 1.5F * left + 0.5F, Theme.alpha(0xFFFFFFFF, left * 0.6F));
            float reach = 3.0F + 10.0F * grow;
            for (int i = 0; i < 4; i++) {
                float deg = 45.0F + i * 90.0F;
                if (config.chOutline) Strokes.spoke(g, deg, 2.0F, reach + 1.0F, thick + 3.0F, Theme.fade(OUTLINE, left));
                Strokes.spoke(g, deg, 3.0F, reach, thick + 1.0F, Theme.alpha(hit, left));
            }
        }
    }
}
