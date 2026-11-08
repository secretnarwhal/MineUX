package com.mineux.core;

import com.mineux.core.greeter.Greeter;
import com.mineux.core.rpc.Presence;
import com.mineux.core.social.FriendTick;
import com.mineux.core.ui.Overlay;
import com.mineux.core.ui.term.Binds;
import com.mineux.core.ui.term.InGameKeys;
import com.mineux.core.ui.term.Readouts;
import com.mineux.core.ui.term.Servers;
import com.mineux.core.ui.term.TerminalScreen;
import com.mineux.core.ui.term.WindowMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;

/**
 * Every way the game reaches MineUX's core, one static method per hook. MineUX's own mixins
 * call these; a host mod that builds core into itself may call them too. Before {@link MineUX#init} each
 * one does nothing and leaves the game as it is.
 * <p>
 * The desktop is {@link TerminalScreen}: the home desk in place of the title screen, the Esc
 * desk in place of the pause menu, and the desktop itself on {@link #DESK_KEY} in a world, held
 * over the game by {@link Overlay} so nothing pauses. Once the desktop has failed in a launch
 * ({@link TerminalScreen#broken}) the menus are vanilla's until the game restarts.
 */
public final class Hooks {

    /** MineUX's own desk key, right shift; the one in use is {@link MineUX#deskKey} (a host may choose another). */
    public static final int DESK_KEY = GLFW.GLFW_KEY_RIGHT_SHIFT;

    /** Whether the home desk has played its boot sequence this launch. */
    private static boolean booted;

    private Hooks() {}

    // ---- screens --------------------------------------------------------

    /**
     * A title screen has finished its init and is still the one showing: the home desk takes
     * its place, the first time in a launch with the boot sequence. True when it did.
     */
    public static boolean titleScreen(TitleScreen title) {
        if (!MineUX.ready() || TerminalScreen.broken()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != title) return false;
        // The greeter first, once a launch (and again after a sign-out): it puts this title
        // screen back up when it is passed, and that init lands here again for the desk.
        if (Greeter.intercept(title)) return true;
        boolean intro = !booted;
        booted = true;
        return open(mc, "the home desk", () -> TerminalScreen.mainMenu(title, intro));
    }

    /**
     * The home desk with its boot sequence, for whatever comes before it (the greeter, after a
     * sign-in): it plays again, as it does straight after a login.
     */
    public static boolean homeDesk(Screen title) {
        if (!MineUX.ready() || TerminalScreen.broken()) return false;
        booted = true;
        return open(Minecraft.getInstance(), "the home desk", () -> TerminalScreen.mainMenu(title, true));
    }

    /** A pause screen with its menu has finished its init: the Esc desk takes its place. */
    public static boolean pauseScreen(PauseScreen pause) {
        if (!MineUX.ready() || TerminalScreen.broken() || !pause.showsPauseMenu()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != pause) return false;
        return open(mc, "the esc desk", () -> TerminalScreen.escape(pause));
    }

    /** From the head of {@code Gui#setScreen}: true leaves the game's screen as it is. */
    public static boolean setScreen(Screen screen) {
        return MineUX.ready() && Overlay.onSetScreen(screen);
    }

    /** Whether a title or pause screen will be handed over, so it should not draw itself meanwhile. */
    public static boolean takesMenus() {
        return MineUX.ready() && !TerminalScreen.broken();
    }

    /**
     * Puts one of MineUX's screens up. Building one reaches into most of core, and what it
     * replaces is one of the game's own screens, so a failure is logged in full and leaves the
     * game where it was. Failures once the desktop is up are {@link TerminalScreen}'s own.
     */
    private static boolean open(Minecraft mc, String what, Supplier<Screen> screen) {
        Screen built;
        try {
            built = screen.get();
        } catch (RuntimeException | LinkageError e) {
            MineUX.LOGGER.error("Could not open {}; staying on the current screen", what, e);
            return false;
        }
        mc.gui.setScreen(built);
        return true;
    }

    // ---- frame and tick -------------------------------------------------

    /** End of the GUI's frame, before the cursor is set: the overlay is drawn over everything. */
    public static void drawOverlay(GuiGraphicsExtractor graphics) {
        if (MineUX.ready()) Overlay.draw(graphics);
    }

    /** Tail of the client tick: the overlay's screen, and everything of the desktop's that runs with it closed. */
    public static void tick(Minecraft mc) {
        if (!MineUX.ready()) return;
        Overlay.tick(mc);
        tickOne("the window mode", 0, mc);
        tickOne("the discord presence", 1, mc);
        tickOne("the binds", 2, mc);
        tickOne("the readouts", 3, mc);
        tickOne("the in-game keys", 4, mc);
        tickOne("the servers", 5, mc);
        tickOne("the friends", 6, mc);
        tickOne("the shutdown", 7, mc);
    }

    /** One of {@link #tick}'s, caught: a part that throws is logged once and the rest go on. */
    private static void tickOne(String what, int which, Minecraft mc) {
        if ((failedTicks & (1 << which)) != 0) return;
        try {
            if (which == 0) WindowMode.onClientTick(mc);
            else if (which == 1) Presence.onClientTick(mc);
            else if (which == 2) Binds.onClientTick(mc);
            else if (which == 3) Readouts.onClientTick(mc);
            else if (which == 4) InGameKeys.onClientTick(mc);
            else if (which == 5) Servers.onClientTick();
            else if (which == 6) FriendTick.onClientTick(mc);
            else com.mineux.core.ui.term.Shutdowns.tick(mc);
        } catch (RuntimeException | LinkageError e) {
            failedTicks |= 1 << which;
            MineUX.LOGGER.error("MineUX: {} failed and is off until the game restarts", what, e);
        }
    }

    private static int failedTicks;

    /** Head of the frame's mouse handling. */
    public static void mouseFrame(Minecraft mc) {
        if (MineUX.ready()) Overlay.onFrame(mc);
    }

    /** Head of {@code Minecraft#handleKeybinds}, in a world: SUPER+number takes the number keys off the hotbar. */
    public static void beforeKeybinds(Minecraft mc) {
        if (MineUX.ready()) InGameKeys.beforeKeybinds(mc);
    }

    // ---- input ----------------------------------------------------------

    /** Every key, GLFW's action: true keeps it from the game and its screen. */
    public static boolean keyPress(KeyEvent event, int action) {
        if (!MineUX.ready()) return false;
        if (Overlay.keyPress(event, action)) return true;
        Minecraft mc = Minecraft.getInstance();
        if (action == GLFW.GLFW_PRESS && event.key() == MineUX.deskKey() && mc.gui.screen() == null && mc.level != null && mc.player != null
            && !TerminalScreen.broken()) {
            return open(mc, "the desktop", () -> new TerminalScreen(null));
        }
        return InGameKeys.keyPress(event, action);
    }

    /** Every typed character ({@code CharacterEvent}): true keeps it from the game. */
    public static boolean charTyped(Object event) {
        return MineUX.ready() && (Overlay.charTyped(event) || InGameKeys.charTyped());
    }

    /** A mouse button ({@code MouseButtonInfo}), GLFW's action: true keeps it from the game. */
    public static boolean mouseButton(Object info, int action) {
        return MineUX.ready() && Overlay.button(info, action);
    }

    /** The wheel, GLFW's raw offsets: true keeps it from the game. */
    public static boolean mouseScroll(double x, double y) {
        return MineUX.ready() && Overlay.scroll(x, y);
    }

    /** From the head of {@code MouseHandler#grabMouse}: true keeps the mouse free for the overlay. */
    public static boolean holdsMouse() {
        return MineUX.ready() && Overlay.active();
    }
}
