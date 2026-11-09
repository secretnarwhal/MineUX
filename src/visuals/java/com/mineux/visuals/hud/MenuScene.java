package com.mineux.visuals.hud;

import com.mojang.authlib.GameProfile;
import com.mineux.visuals.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.world.entity.player.PlayerSkin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * The main menu's scene skin: the desktop as ever, over an animated scene in place of its black
 * -- an aurora, a nebula, rain, bokeh, silk, a horizon grid or a picture -- in the accent colour, moving a
 * little with the mouse, with a soft light following the cursor and your face and name in the
 * corner. Clarity's menu scenes, redrawn with the GUI's own rectangles. The terminal skin, the
 * default, is the plain black the desktop always had.
 */
final class MenuScene {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/menuscene");
    private static final long START = System.nanoTime();

    private static boolean failed;
    private static float parallaxX;
    private static float parallaxY;
    private static String skinFor;
    private static Supplier<PlayerSkin> skin;

    private MenuScene() {}

    /** Whether the main menu wears the scene. */
    static boolean on() {
        return !failed && Objects.equals("scene", SkinConfig.get().menuSkin);
    }

    /** The scene, full screen; false when it failed, for the caller to draw the plain background. */
    static boolean draw(GuiGraphicsExtractor g, int width, int height) {
        if (!on()) return false;
        try {
            drawAll(g, width, height);
            return true;
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            LOGGER.error("The main menu's scene failed; the menu is plain until the game restarts", e);
            return false;
        }
    }

    private static void drawAll(GuiGraphicsExtractor g, int width, int height) {
        SkinConfig config = SkinConfig.get();
        Minecraft mc = Minecraft.getInstance();
        float t = (float) ((System.nanoTime() - START) / 1.0E9 % 7200.0) * config.menuSpeed;
        int accent = SkinConfig.rgb(config.menuAccent, 0xEFC648);
        // In the desk's units: the scene is the home desk's backdrop, drawn at the desk scale.
        float mx = (float) com.mineux.core.ui.DeskScale.cursorX();
        float my = (float) com.mineux.core.ui.DeskScale.cursorY();
        float toX = config.menuParallax ? (mx / Math.max(1, width) - 0.5F) * 2.0F : 0.0F;
        float toY = config.menuParallax ? (my / Math.max(1, height) - 0.5F) * 2.0F : 0.0F;
        parallaxX += (toX - parallaxX) * 0.08F;
        parallaxY += (toY - parallaxY) * 0.08F;

        MenuScenes.Frame frame = new MenuScenes.Frame(g, width, height, t, accent, parallaxX, parallaxY);
        String scene = config.menuScene;
        switch (scene) {
            case "nebula": MenuScenes.nebula(frame); break;
            case "rain": MenuScenes.rain(frame); break;
            case "bokeh": MenuScenes.bokeh(frame); break;
            case "silk": MenuScenes.silk(frame); break;
            case "grid": MenuScenes.grid(frame); break;
            case "picture": MenuScenes.picture(frame); break;
            default: MenuScenes.aurora(frame); break;
        }

        if (config.menuSpotlight > 0.01F) {
            MenuScenes.glow(g, Math.round(mx), Math.round(my), 130, Theme.alpha(0xFFFFFF, 0.025F * config.menuSpotlight), 5);
        }
        // A vignette, so the windows over it stand out at the edges.
        g.fillGradient(0, 0, width, height / 4, 0x66000000, 0x00000000);
        g.fillGradient(0, height - height / 3, width, height, 0x00000000, 0x80000000);
        if (config.menuAccount) account(g, mc, mc.font, height);
    }

    /** Your face and name, bottom left. */
    private static void account(GuiGraphicsExtractor g, Minecraft mc, Font font, int height) {
        GameProfile profile = mc.getGameProfile();
        String name = mc.getUser().getName();
        if (skin == null || !Objects.equals(skinFor, name)) {
            skinFor = name;
            skin = mc.getSkinManager().createLookup(profile, false);
        }
        int x = 8;
        int y = height - 34;
        int w = font.width(name) + 40;
        Theme.roundRect(g, x, y, w, 26, 4, 0xA0101219);
        Theme.ring(g, x, y, w, 26, 4, Theme.EDGE);
        PlayerFaceExtractor.extractRenderState(g, skin.get(), x + 4, y + 4, 18);
        g.text(font, name, x + 28, y + 5, Theme.TEXT, true);
        g.pose().pushMatrix();
        g.pose().translate((float) x + 28.0F, (float) y + 15.0F);
        g.pose().scale(0.67F, 0.67F);
        g.text(font, "signed in", 0, 0, Theme.GREEN, false);
        g.pose().popMatrix();
    }
}
