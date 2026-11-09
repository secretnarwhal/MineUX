package com.mineux.core.ui;

import com.mineux.core.DeskExtensions;
import com.mineux.core.MineUX;
import com.mineux.core.ui.term.TerminalScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;

/**
 * MineUX's own screens in a world -- the desktop and the Esc desk -- held over the game rather
 * than put up as its screen.
 * <p>
 * The game never learns one is up: {@code gui.screen()} stays whatever the game has (nothing,
 * or a chest, a death screen), so nothing pauses and everything runs on as with nothing on
 * screen, and the overlay draws last in the frame, over the game's screen too. It
 * takes the keyboard and the mouse while it is open, from the head of the game's own handlers
 * (MineUX's mouse and keyboard mixins, through {@link com.mineux.core.Hooks}): the game and
 * its screen get none of it.
 * <p>
 * Every way onto the game's screen goes through {@link #onSetScreen}: one of ours is taken in
 * here instead, and a screen ours put up on its way out (back to the game, the options) closes
 * the overlay. A screen anything else puts up -- the server's chest, the death screen -- goes up
 * under the overlay, which stays on top.
 */
public final class Overlay {

    private static final long DOUBLE_CLICK_MS = 250L;

    private static Screen hosted;
    /** Inside a call into the hosted screen: a screen it puts up is its own doing. */
    private static boolean dispatching;
    /** Inside a {@code setScreen} of ours, which the hook lets through. */
    private static boolean bypass;
    /** The button held down on the overlay, for its drags; null with none. */
    private static MouseButtonInfo held;
    private static double lastX = Double.NaN;
    private static double lastY;
    private static long clickedAt;
    private static int clickedButton = 0 - 1;
    /** This frame, the game's screen under the overlay has blurred the frame already. */
    private static boolean blurTaken;

    private Overlay() {}

    /** Whether the overlay is open. */
    public static boolean active() {
        return hosted != null;
    }

    /** Whether {@code screen} is the one held over the game. */
    public static boolean hosts(Screen screen) {
        return screen != null && screen == hosted;
    }

    /** What is on top: the overlay's screen, else the game's. Where a check means "is any menu up". */
    public static Screen top(Minecraft mc) {
        return hosted != null ? hosted : mc.gui.screen();
    }

    /** Whether the frame has been blurred under the overlay: a second blur in a frame throws. */
    public static boolean blurTaken() {
        return hosted != null && blurTaken;
    }

    /** One of MineUX's own screens. */
    public static boolean ours(Screen screen) {
        if (TerminalScreen.class.isInstance(screen)) return true;
        for (Class<?> c : DeskExtensions.overlayScreens()) if (c.isInstance(screen)) return true;
        return false;
    }

    // ---- the game's screen ----------------------------------------------

    /** From the head of {@code Gui#setScreen}: true leaves the game's screen as it is. */
    public static boolean onSetScreen(Screen target) {
        if (bypass) return false;
        Minecraft mc = Minecraft.getInstance();
        if (target != null && ours(target) && mc.level != null && mc.player != null) {
            host(mc, target);
            // The Esc menu the desktop stands in for goes from under it: nothing is paused.
            if (PauseScreen.class.isInstance(mc.gui.screen())) setGameScreen(mc, null);
            return true;
        }
        if (hosted == null) return false;
        // The game's own Esc menu (the window losing the focus, with nothing up as far as it
        // knows): the overlay is the menu already, and nothing is paused under it.
        if (!dispatching && PauseScreen.class.isInstance(target)) return true;
        // Anything else's screen (the server's, the death screen) goes up under the overlay.
        if (!dispatching) return false;
        // The overlay's own way out: back to the game, or to what is already under it.
        if (target == null || target == mc.gui.screen()) {
            close(mc, true);
            return true;
        }
        // Out to one of the game's own screens (the options, the disconnect): that one is up instead.
        close(mc, false);
        return false;
    }

    /** Takes {@code screen} in, in place of the one held, as {@code Gui#setScreen} would put it up. */
    private static void host(Minecraft mc, Screen screen) {
        replace(mc, screen, hosted);
    }

    // The held screen comes in as a parameter, never a local copy of hosted: the obfuscator
    // reads hosted again after it is cleared, and calls removed() on null (HoistCheck).
    private static void replace(Minecraft mc, Screen screen, Screen old) {
        if (old == screen) return;
        if (old != null) {
            hosted = null;
            call(old, Call.REMOVED, null, null, 0, 0, 0, 0);
        } else {
            // As a screen going up does: the mouse let go, and no key left held for the game.
            if (mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.releaseMouse();
            KeyMapping.releaseAll();
        }
        held = null;
        lastX = Double.NaN;
        hosted = screen;
        Window window = mc.getWindow();
        call(screen, Call.ADDED, null, null, 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    /** Puts the overlay away; {@code grab} takes the mouse back for the game when it has no screen. */
    public static void close(Minecraft mc, boolean grab) {
        closeHeld(mc, grab, hosted);
    }

    private static void closeHeld(Minecraft mc, boolean grab, Screen screen) {
        if (screen == null) return;
        hosted = null;
        held = null;
        call(screen, Call.REMOVED, null, null, 0, 0, 0, 0);
        if (mc.gui.screen() != null) return;
        mc.textInputManager().stopTextInput();
        if (mc.level != null) KeyMapping.restoreToggleStatesOnScreenClosed();
        if (grab) mc.mouseHandler.grabMouse();
    }

    /** The game's screen, set past the hook. */
    private static void setGameScreen(Minecraft mc, Screen screen) {
        bypass = true;
        try {
            mc.gui.setScreen(screen);
        } finally {
            bypass = false;
        }
    }

    // ---- the game's passes ----------------------------------------------

    /** Tail of the client tick: the hosted screen's tick, as the game's gets one. */
    public static void tick(Minecraft mc) {
        if (hosted == null) return;
        if (mc.level == null || mc.player == null) {
            close(mc, false);
            return;
        }
        call(hosted, Call.TICK, null, null, 0, 0, 0, 0);
    }

    /** End of the GUI's frame: drawn over everything, at the size the game's screen would have. */
    public static void draw(GuiGraphicsExtractor g) {
        if (hosted == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            close(mc, false);
            return;
        }
        Screen under = mc.gui.screen();
        blurTaken = under != null && !under.isInGameUi();
        // A desk screen is laid out at the desk's size (DeskScale), not the GUI's.
        boolean desk = DeskScreen.class.isInstance(hosted);
        int w = desk ? DeskScale.width() : g.guiWidth();
        int h = desk ? DeskScale.height() : g.guiHeight();
        if (hosted.width != w || hosted.height != h) call(hosted, Call.RESIZE, null, null, 0, 0, g.guiWidth(), g.guiHeight());
        if (hosted == null) return;
        Window window = mc.getWindow();
        int mouseX = (int) mc.mouseHandler.getScaledXPos(window);
        int mouseY = (int) mc.mouseHandler.getScaledYPos(window);
        dispatching = true;
        try {
            hosted.extractRenderStateWithTooltipAndSubtitles(g, mouseX, mouseY, mc.getDeltaTracker().getGameTimeDeltaTicks());
        } catch (RuntimeException | LinkageError e) {
            fail(mc, "drawing", e);
        } finally {
            dispatching = false;
        }
    }

    /** Head of the frame's mouse handling: the cursor's moves and drags, to the overlay. */
    public static void onFrame(Minecraft mc) {
        if (hosted == null) return;
        double x = mc.mouseHandler.xpos();
        double y = mc.mouseHandler.ypos();
        if (Double.isNaN(lastX)) {
            lastX = x;
            lastY = y;
            return;
        }
        double dx = x - lastX;
        double dy = y - lastY;
        lastX = x;
        lastY = y;
        if (dx == 0.0 && dy == 0.0) return;
        Window window = mc.getWindow();
        call(hosted, Call.MOVE, null, held, mc.mouseHandler.getScaledXPos(window), mc.mouseHandler.getScaledYPos(window),
            MouseHandler.getScaledXPos(window, dx), MouseHandler.getScaledYPos(window, dy));
    }

    // ---- input ----------------------------------------------------------

    /** Every key while the overlay is open: true keeps it from the game and its screen. */
    public static boolean keyPress(KeyEvent event, int action) {
        if (hosted == null) return false;
        boolean taken = action == GLFW.GLFW_RELEASE
            ? call(hosted, Call.KEY_UP, event, null, 0, 0, 0, 0)
            : call(hosted, Call.KEY_DOWN, event, null, 0, 0, 0, 0);
        // The function keys the overlay has no use for are the game's still: fullscreen, a screenshot, F3.
        return taken || !function(event.key());
    }

    /** Every typed character while the overlay is open. */
    public static boolean charTyped(Object event) {
        if (hosted == null) return false;
        if (CharacterEvent.class.isInstance(event)) call(hosted, Call.CHAR, event, null, 0, 0, 0, 0);
        return true;
    }

    /** A mouse button while the overlay is open; GLFW's action. */
    public static boolean button(Object info, int action) {
        if (hosted == null || !MouseButtonInfo.class.isInstance(info)) return false;
        Minecraft mc = Minecraft.getInstance();
        MouseButtonInfo button = (MouseButtonInfo) info;
        Window window = mc.getWindow();
        double x = mc.mouseHandler.getScaledXPos(window);
        double y = mc.mouseHandler.getScaledYPos(window);
        if (action == GLFW.GLFW_PRESS) {
            long now = System.currentTimeMillis();
            boolean twice = now - clickedAt < DOUBLE_CLICK_MS && clickedButton == button.button();
            held = button;
            if (call(hosted, twice ? Call.CLICK_TWICE : Call.CLICK, null, button, x, y, 0, 0)) {
                clickedAt = now;
                clickedButton = button.button();
            }
        } else {
            if (held != null && held.button() == button.button()) held = null;
            call(hosted, Call.RELEASE, null, button, x, y, 0, 0);
        }
        return true;
    }

    /** The wheel while the overlay is open, GLFW's offsets as the game would scale them. */
    public static boolean scroll(double xOffset, double yOffset) {
        if (hosted == null) return false;
        Minecraft mc = Minecraft.getInstance();
        boolean discrete = mc.options.discreteMouseScroll().get();
        double sensitivity = mc.options.mouseWheelSensitivity().get();
        double h = (discrete ? Math.signum(xOffset) : xOffset) * sensitivity;
        double v = (discrete ? Math.signum(yOffset) : yOffset) * sensitivity;
        Window window = mc.getWindow();
        call(hosted, Call.SCROLL, null, null, mc.mouseHandler.getScaledXPos(window), mc.mouseHandler.getScaledYPos(window), h, v);
        return true;
    }

    private static boolean function(int key) {
        return key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25;
    }

    // ---- calls into the hosted screen -----------------------------------

    /** What {@link #call} does: constants rather than an enum, which the obfuscator renames. */
    private static final class Call {
        static final int ADDED = 0;
        static final int REMOVED = 1;
        static final int TICK = 2;
        static final int RESIZE = 3;
        static final int KEY_DOWN = 4;
        static final int KEY_UP = 5;
        static final int CHAR = 6;
        static final int CLICK = 7;
        static final int CLICK_TWICE = 8;
        static final int RELEASE = 9;
        static final int MOVE = 10;
        static final int SCROLL = 11;

        private Call() {}
    }

    /**
     * One call into {@code screen}, marked as the overlay's doing, with what it throws caught: a
     * screen that throws comes off rather than staying up as an invisible wall over the game.
     */
    private static boolean call(Screen screen, int what, Object event, MouseButtonInfo button, double x, double y, double a, double b) {
        return dispatch(dispatching, screen, what, event, button, x, y, a, b);
    }

    /** {@code was}: dispatching before this call, put back after it (a parameter, not a local copy: HoistCheck). */
    private static boolean dispatch(boolean was, Screen screen, int what, Object event, MouseButtonInfo button,
                                double x, double y, double a, double b) {
        Minecraft mc = Minecraft.getInstance();
        dispatching = true;
        try {
            if (what == Call.ADDED) {
                screen.added();
                screen.init((int) a, (int) b);
            } else if (what == Call.REMOVED) {
                screen.removed();
            } else if (what == Call.TICK) {
                screen.tick();
            } else if (what == Call.RESIZE) {
                screen.resize((int) a, (int) b);
            } else if (what == Call.KEY_DOWN) {
                return screen.keyPressed((KeyEvent) event);
            } else if (what == Call.KEY_UP) {
                return screen.keyReleased((KeyEvent) event);
            } else if (what == Call.CHAR) {
                return screen.charTyped((CharacterEvent) event);
            } else if (what == Call.CLICK || what == Call.CLICK_TWICE) {
                boolean taken = screen.mouseClicked(new MouseButtonEvent(x, y, button), what == Call.CLICK_TWICE);
                screen.afterMouseAction();
                return taken;
            } else if (what == Call.RELEASE) {
                return screen.mouseReleased(new MouseButtonEvent(x, y, button));
            } else if (what == Call.MOVE) {
                screen.mouseMoved(x, y);
                if (button != null) screen.mouseDragged(new MouseButtonEvent(x, y, button), a, b);
                screen.afterMouseMove();
            } else if (what == Call.SCROLL) {
                boolean taken = screen.mouseScrolled(x, y, a, b);
                screen.afterMouseAction();
                return taken;
            }
            return true;
        } catch (RuntimeException | LinkageError e) {
            fail(mc, "handling " + what, e);
            return true;
        } finally {
            dispatching = was;
        }
    }

    private static void fail(Minecraft mc, String doing, Throwable error) {
        MineUX.LOGGER.error("The MineUX overlay failed while {}; it was closed", doing, error);
        if (hosted == null) return;
        hosted = null;
        held = null;
        if (mc.gui.screen() == null) mc.mouseHandler.grabMouse();
    }
}
