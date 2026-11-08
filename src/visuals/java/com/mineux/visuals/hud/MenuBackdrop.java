package com.mineux.visuals.hud;

import com.mineux.core.Backdrop;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The main menu's scene ({@link MenuScene}) as the home desk's backdrop, added once through
 * {@code DeskExtensions.addBackdrop} by {@code Visuals.install}. On while the {@code menu} skin
 * is {@code scene}; the terminal skin leaves the desk its black.
 */
public final class MenuBackdrop extends Backdrop {

    @Override
    public boolean on() {
        return MenuScene.on();
    }

    @Override
    public void draw(GuiGraphicsExtractor g, int w, int h, int mouseX, int mouseY, float delta) {
        MenuScene.draw(g, w, h);
    }
}
