package com.mineux.core;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * What the home desk draws behind its windows in place of black: added through
 * {@link DeskExtensions#addBackdrop}, the visuals' menu scenes for one. Draw only through the
 * game's GUI (it must work on OpenGL and Vulkan). An abstract class, not an interface.
 */
public abstract class Backdrop {

    /** Whether it draws now; the last one added that is on is the one drawn. */
    public abstract boolean on();

    /** The whole screen, {@code w} by {@code h} GUI px. */
    public abstract void draw(GuiGraphicsExtractor g, int w, int h, int mouseX, int mouseY, float delta);
}
