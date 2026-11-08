package com.mineux.screens.gui;

import com.mineux.screens.Screens;
import com.mineux.screens.capture.CaptureManager;
import com.mineux.screens.displays.CapturedScreenDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The owner's red LIVE line, top right of the HUD, whenever any screen of theirs is shared:
 * how many and how many players watch. Drawn even with the HUD hidden (F1): sharing is never
 * out of sight. "paused" while the game is minimised (nothing is sent then).
 */
public final class LiveHud {

    private LiveHud() {}

    public static void extract(GuiGraphicsExtractor g) {
        CaptureManager manager = Screens.manager();
        if (manager == null) return;
        int shared = 0;
        int watching = 0;
        for (CapturedScreenDisplay d : manager.displays) {
            if (!d.shared) continue;
            shared++;
            watching += d.viewers;
        }
        if (shared == 0) return;
        Font font = Minecraft.getInstance().font;
        String text = "LIVE  " + shared + (shared == 1 ? " screen" : " screens") + " shared, " + watching + " watching";
        if (Screens.sharing().paused()) text += " (paused)";
        else if (!Screens.sharing().connected()) text += " (connecting)";
        int w = font.width(text) + 16;
        int x = g.guiWidth() - w - 4;
        int y = 4;
        g.fill(x, y, x + w, y + 13, 0xC0200608);
        g.fill(x, y, x + 2, y + 13, 0xFFF0303A);
        boolean blink = (System.currentTimeMillis() / 600L) % 2L == 0L;
        g.fill(x + 6, y + 5, x + 9, y + 8, blink ? 0xFFFF4040 : 0xFF801818);
        g.text(font, text, x + 12, y + 3, 0xFFFF6A6A, false);
    }
}
