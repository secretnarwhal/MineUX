package com.mineux.screens.capture;

import com.mineux.core.input.Keys;
import com.mineux.screens.Screens;
import com.mineux.screens.ScreensConfig;
import com.mineux.screens.bridge.ScreensWinBridge;
import org.lwjgl.glfw.GLFW;

/**
 * The system-wide Ctrl+Alt+key that brings the game back after a click switched to a window,
 * kept in step with the back key bound in Screens' settings.
 */
final class ReturnHotkey {

    private static final int MODIFIERS = ScreensWinBridge.MOD_CONTROL | ScreensWinBridge.MOD_ALT;

    private int scancode;
    private boolean registered;

    void update(ScreensWinBridge bridge) {
        int code = scancode(ScreensConfig.get().backKey);
        if (code == scancode) return;
        scancode = code;
        registered = bridge.setReturnHotkey(MODIFIERS, code) && code != 0;
        if (code != 0 && !registered) {
            Screens.LOGGER.warn("Could not register the {} hotkey, another application might be using it", name());
        }
    }

    /** The bound key's scancode, 0 when none (or a mouse button, which a hotkey cannot be). */
    private int scancode(int key) {
        if (key == Keys.UNBOUND || Keys.isMouse(key)) return 0;
        return Math.max(0, GLFW.glfwGetKeyScancode(key));
    }

    String name() {
        return "Ctrl+Alt+" + com.mineux.screens.Keys.backName();
    }

    boolean registered() {
        return registered;
    }
}
