package com.mineux.core.ui.term;

import com.mineux.core.DeskExtensions;
import com.mineux.core.MineUX;
import com.mineux.core.ShutdownLayer;
import com.mineux.core.greeter.StreamFill;
import com.mineux.core.ui.Overlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * The extensions' {@link ShutdownLayer}s, as the desktop asks them: which one is up, and the
 * desktop kept on screen under it. One that throws is logged and left out from then on, so a
 * broken layer can never hold the desktop hostage. Public for {@code Hooks.tick} only.
 */
public final class Shutdowns {

    private static final List<ShutdownLayer> BROKEN = new ArrayList<>();

    private Shutdowns() {}

    /** The first layer up now, or null: with none added, always null. */
    static ShutdownLayer active() {
        List<ShutdownLayer> layers = DeskExtensions.shutdownLayers();
        if (layers.isEmpty()) return null;
        for (ShutdownLayer layer : layers) {
            if (BROKEN.contains(layer)) continue;
            try {
                if (layer.active()) return layer;
            } catch (RuntimeException | LinkageError e) {
                broke(layer, e);
            }
        }
        return null;
    }

    static boolean covers(ShutdownLayer layer) {
        try {
            return layer.covers();
        } catch (RuntimeException | LinkageError e) {
            broke(layer, e);
            return false;
        }
    }

    static boolean escape(ShutdownLayer layer) {
        try {
            return layer.escape();
        } catch (RuntimeException | LinkageError e) {
            broke(layer, e);
            return true;
        }
    }

    static String lost(ShutdownLayer layer) {
        try {
            return layer.lost();
        } catch (RuntimeException | LinkageError e) {
            broke(layer, e);
            return "failed";
        }
    }

    static void draw(ShutdownLayer layer, GuiGraphicsExtractor g, Font font, int width, int height, boolean menu, StreamFill liquid) {
        try {
            layer.draw(g, font, width, height, menu, liquid);
        } catch (RuntimeException | LinkageError e) {
            broke(layer, e);
        }
    }

    /** A client tick: while a layer is up, the desktop is put back if anything took it away. */
    public static void tick(Minecraft mc) {
        if (active() == null || TerminalScreen.broken()) return;
        Screen showing = Overlay.top(mc);
        if (TerminalScreen.class.isInstance(showing)) return;
        mc.gui.setScreen(new TerminalScreen(showing));
    }

    private static void broke(ShutdownLayer layer, Throwable e) {
        if (BROKEN.contains(layer)) return;
        BROKEN.add(layer);
        MineUX.LOGGER.warn("A shutdown layer failed and is off", e);
    }
}
