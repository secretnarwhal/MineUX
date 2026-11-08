package com.mineux.core.ui.term;

import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.blaze3d.platform.Window;
import com.mineux.core.util.Words;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.Set;

/**
 * SUPER+M ({@code windowmode toggle}): the game's own window between windowed and maximised,
 * title bar and all, and borderless -- no title bar, covering the whole monitor.
 * <p>
 * Asked for from a key press or a command, done on the next client tick: resizing the window
 * lays the open screen out again, which must not happen from inside that screen's own key
 * handling. Out of Minecraft's exclusive fullscreen first, through the option, as the login
 * screen does, so options.txt agrees.
 */
public final class WindowMode {

    private static final int NONE = 0, TOGGLE = 1, BORDERLESS = 2, MAXIMIZE = 3;
    /** How the choice is kept in terminal.json: plain strings, as obfuscated builds need anything saved kept. */
    private static final String SAVED_BORDERLESS = "borderless", SAVED_MAXIMIZED = "maximize";
    /** Whether a remembered borderless window has been put back since the last sign-in. */
    private static boolean restored;
    private static volatile int pending = NONE;
    /** Whether a windowmode bind's keys were held last tick, in game, so holding them toggles once. */
    private static boolean held;

    private WindowMode() {}

    private static final Set<String> W_TOGGLE = Words.of("toggle t");
    private static final Set<String> W_BORDERLESS = Words.of("borderless fill");
    private static final Set<String> W_MAXIMIZE = Words.of("maximize maximise maximized windowed");

    /** Asks for a mode by name; null once asked, or what was wrong with the name. */
    static String request(String arg) {
        String a = arg == null || arg.isBlank() ? "toggle" : arg.trim().toLowerCase(Locale.ROOT);
        if (W_TOGGLE.contains(a)) pending = TOGGLE;
        else if (W_BORDERLESS.contains(a)) pending = BORDERLESS;
        else if (W_MAXIMIZE.contains(a)) pending = MAXIMIZE;
        else return "windowmode is toggle, borderless or maximize";
        return null;
    }

    /** Once a tick: carries out a mode asked for since the last one. */
    public static void onClientTick(Minecraft mc) {
        restore(mc);
        pollInGame(mc);
        apply(mc);
    }

    private static void apply(Minecraft mc) {
        applyMode(mc, pending);
    }

    /** {@code mode}: the pending one, a parameter rather than a local copy of the field (HoistCheck). */
    private static void applyMode(Minecraft mc, int mode) {
        if (mode == NONE) return;
        pending = NONE;
        Window window = mc.getWindow();
        if (window == null) return;
        if (window.isFullscreen()) {
            mc.options.fullscreen().set(false);
            window.updateFullscreenIfChanged();
            mc.options.save();
            if (window.isFullscreen()) return;
        }
        if (mode == TOGGLE) mode = borderless(window) ? MAXIMIZE : BORDERLESS;
        if (mode == BORDERLESS) goBorderless(window);
        else goMaximized(window);
    }

    /**
     * Borderless, if that was the last choice, once the login screen is done with the window:
     * it makes the game windowed and maximised every launch (its dotfile's {@code window} key),
     * which would otherwise undo SUPER+M each time.
     */
    private static void restore(Minecraft mc) {
        if (restored || mc.gui == null || !TerminalScreen.class.isInstance(com.mineux.core.ui.Overlay.top(mc))) return;
        restored = true;
        if (pending == NONE && java.util.Objects.equals(SAVED_BORDERLESS, Workstations.state().windowMode)) pending = BORDERLESS;
    }

    /**
     * In game, with no screen open, the {@code windowmode} binds of hyprland.conf work too:
     * the window is the game's, not the desktop's.
     */
    private static void pollInGame(Minecraft mc) {
        Window window = mc.getWindow();
        if (window == null || mc.gui == null || mc.gui.screen() != null) {
            held = false;
            return;
        }
        long handle = window.handle();
        HyprConf.Bind hit = null;
        for (HyprConf.Bind bind : HyprConf.get().binds) {
            if (!java.util.Objects.equals("windowmode", bind.dispatcher) || bind.key < 0) continue;
            if (GLFW.glfwGetKey(handle, bind.key) != GLFW.GLFW_PRESS) continue;
            if (HyprConf.glfwMods(bind.mods, HyprConf.superBit()) == HyprConf.heldMods(handle)) hit = bind;
        }
        if (hit != null && !held) request(hit.arg);
        held = hit != null;
    }

    /** Whether the window is borderless now: no title bar, and at least the monitor's size. */
    static boolean borderless(Window window) {
        long handle = window.handle();
        if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_DECORATED) != GLFW.GLFW_FALSE) return false;
        Monitor monitor = window.findBestMonitor();
        if (monitor == null) return true;
        VideoMode mode = monitor.currentMode();
        return window.getScreenWidth() >= mode.getWidth() && window.getScreenHeight() >= mode.getHeight();
    }

    private static void goBorderless(Window window) {
        long handle = window.handle();
        Monitor monitor = window.findBestMonitor();
        if (monitor == null) return;
        VideoMode mode = monitor.currentMode();
        if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED) != GLFW.GLFW_FALSE) GLFW.glfwRestoreWindow(handle);
        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
        GLFW.glfwSetWindowMonitor(handle, 0L, monitor.x(), monitor.y(), mode.getWidth(), mode.getHeight(), GLFW.GLFW_DONT_CARE);
        remember(SAVED_BORDERLESS);
    }

    private static void remember(String mode) {
        if (java.util.Objects.equals(mode, Workstations.state().windowMode)) return;
        Workstations.state().windowMode = mode;
        Workstations.saveState();
    }

    private static void goMaximized(Window window) {
        long handle = window.handle();
        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
        if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_FALSE) GLFW.glfwMaximizeWindow(handle);
        remember(SAVED_MAXIMIZED);
    }
}
