package com.mineux.core.input;

import com.mineux.core.ui.Overlay;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;

/**
 * Module keys (zoom, freelook, inspect, Screens' menu...), stored as the desktop's binds are: a
 * GLFW key code, or a mouse button {@code b} as {@link #MOUSE_BASE}{@code  - b}, or
 * {@link #UNBOUND}. Read straight off the keyboard and mouse, never while the desktop overlay
 * has them.
 */
public final class Keys {

    public static final int UNBOUND = GLFW.GLFW_KEY_UNKNOWN;
    /** Mouse button {@code b} is stored as {@code MOUSE_BASE - b}. */
    public static final int MOUSE_BASE = -100;

    private Keys() {}

    public static boolean isMouse(int code) {
        return code <= MOUSE_BASE;
    }

    public static int mouseCode(int button) {
        return MOUSE_BASE - button;
    }

    public static int mouseButton(int code) {
        return MOUSE_BASE - code;
    }

    /** A stored bind as it may be kept: one GLFW knows, else {@link #UNBOUND}. */
    public static int sanitize(int code) {
        if (isMouse(code)) {
            int button = mouseButton(code);
            return button >= 0 && button <= GLFW.GLFW_MOUSE_BUTTON_LAST ? code : UNBOUND;
        }
        return code > 0 && code <= GLFW.GLFW_KEY_LAST ? code : UNBOUND;
    }

    /** Whether the bind is held now. */
    public static boolean isDown(Window window, int code) {
        if (window == null || code == UNBOUND || Overlay.active()) return false;
        if (isMouse(code)) {
            long handle = window.handle();
            return handle != 0 && GLFW.glfwGetMouseButton(handle, mouseButton(code)) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, code);
    }

    /** How a bind is written: {@code b}, {@code home}, {@code mouse4}, {@code none}. */
    public static String name(int code) {
        return com.mineux.core.ui.term.Setting.keyName(code);
    }
}
