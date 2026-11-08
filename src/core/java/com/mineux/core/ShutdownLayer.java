package com.mineux.core;

import com.mineux.core.greeter.StreamFill;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Drawn over the whole desktop while the game is going down (a {@link RebootHook}'s reboot):
 * while it is {@link #active}, the desktop is kept on screen (put back up if anything takes it
 * away), drawn under it until it {@link #covers} it, and it has every key and click. Escape
 * asks it to call the reboot off. Added through {@link DeskExtensions#addShutdownLayer}; the
 * first one active is the one drawn. Draw only through the game's GUI (OpenGL and Vulkan). An
 * abstract class, not an interface (no lambdas).
 */
public abstract class ShutdownLayer {

    /** Whether it is up now. Read every frame and every tick, so keep it cheap. */
    public abstract boolean active();

    /** Whether everything behind has faded out, so the desktop need not be drawn under it. Default false. */
    public boolean covers() {
        return false;
    }

    /**
     * The whole screen, {@code width} by {@code height} GUI px. {@code menu} is whether the
     * desktop is the home desk; {@code liquid} is the home desk's stream fill, null for none.
     */
    public abstract void draw(GuiGraphicsExtractor g, Font font, int width, int height, boolean menu, StreamFill liquid);

    /** Escape while it is up: true when the reboot was called off (the desktop says so). Default: it cannot be. */
    public boolean escape() {
        return false;
    }

    /**
     * Why the reboot fell through, once, or null while it has not: the desktop prints it on its
     * terminal. The layer is expected to go inactive with it.
     */
    public String lost() {
        return null;
    }
}
