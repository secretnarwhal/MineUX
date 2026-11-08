package com.mineux.core;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Something drawn over the game while the desktop is closed, under the desktop's own pinned
 * windows and readouts: a HUD, a target card, info over the world. Added through
 * {@link DeskExtensions#addHudLayer}; drawn in the order added, in a world only. An abstract
 * class, not an interface (no lambdas: an obfuscator a host may run over this code, such as Skidfuscator, breaks those).
 */
public abstract class HudLayer {

    /** Whether it has anything to draw now; read every tick. */
    public abstract boolean on();

    /** Draws it over the whole screen, {@code g.guiWidth()} by {@code g.guiHeight()}. */
    public abstract void draw(GuiGraphicsExtractor g, Font font);
}
