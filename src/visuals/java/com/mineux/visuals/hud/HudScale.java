package com.mineux.visuals.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * How large the HUD skins are drawn: the hotbar, its bars and the
 * scoreboard, all at one scale over the game's GUI scale. Auto keeps them the
 * size they are at GUI scale 3 on a 1080p window, whatever the window and the GUI scale; the
 * scale setting multiplies either.
 */
public final class HudScale {

    private HudScale() {}

    /** The factor over the GUI's own scale. */
    public static float factor(Minecraft mc) {
        SkinConfig config = SkinConfig.get();
        float f = config.hudScale;
        if (config.hudAutoScale) {
            double gui = mc.getWindow().getGuiScale();
            if (gui > 0.0) f *= (float) (mc.getWindow().getHeight() / 360.0 / gui);
        }
        return Float.isNaN(f) ? 1.0F : Math.max(0.5F, Math.min(3.0F, f));
    }

    /** Scales the pose by {@code f} from the top left; the width and height to lay out in are the GUI's over {@code f}. */
    static void push(GuiGraphicsExtractor g, float f) {
        g.pose().pushMatrix();
        if (f != 1.0F) g.pose().scale(f, f);
    }

    static void pop(GuiGraphicsExtractor g) {
        g.pose().popMatrix();
    }

    static int width(GuiGraphicsExtractor g, float f) {
        return Math.round(g.guiWidth() / f);
    }

    static int height(GuiGraphicsExtractor g, float f) {
        return Math.round(g.guiHeight() / f);
    }
}
