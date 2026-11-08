package com.mineux.screens;

import java.util.Locale;
import net.minecraft.client.Minecraft;

/**
 * Screens' keys, bound in its settings window like every module key (screens.json: menuKey,
 * freecamKey, backKey; personal, not per workstation). Read straight off the keyboard and mouse
 * once a tick, in a world with no screen open and the desktop overlay closed, so a press is seen
 * once however long it is held.
 */
public final class Keys {

    private boolean menuWas;
    private boolean freecamWas;

    /** The menu key went down since the last tick. */
    public boolean menuPressed(Minecraft mc) {
        boolean now = held(mc, ScreensConfig.get().menuKey);
        boolean pressed = now && !menuWas;
        menuWas = now;
        return pressed;
    }

    /** The freecam key went down since the last tick. */
    public boolean freecamPressed(Minecraft mc) {
        boolean now = held(mc, ScreensConfig.get().freecamKey);
        boolean pressed = now && !freecamWas;
        freecamWas = now;
        return pressed;
    }

    private static boolean held(Minecraft mc, int code) {
        if (mc.gui.screen() != null) return false;
        return com.mineux.core.input.Keys.isDown(mc.getWindow(), code);
    }

    public static String menuName() {
        return name(ScreensConfig.get().menuKey);
    }

    public static String freecamName() {
        return name(ScreensConfig.get().freecamKey);
    }

    public static String backName() {
        return name(ScreensConfig.get().backKey);
    }

    private static String name(int code) {
        return com.mineux.core.input.Keys.name(code).toUpperCase(Locale.ROOT);
    }
}
