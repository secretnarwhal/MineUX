package com.mineux.core.ui.term;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mineux.core.ui.Overlay;
import com.mineux.core.ui.UiConfig;
import com.mineux.core.ui.term.Term.Line;
import com.mineux.core.ui.term.Term.Span;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * {@code bind <key> "<command>"}: a key that runs any muxsh command in game -- a toggle, a
 * setting, a workstation, several at once with {@code ;}.
 * <p>
 * Polled once a tick with no screen open, the way the modules' own keybinds are, and run on a
 * shell of its own with nothing drawn. What the command would have printed last goes on the
 * action bar, so {@code toggle rpc} on a key says which way it went.
 */
public final class Binds {

    /** Keys held last tick, by code, so a held key fires once. */
    private static final Map<Integer, Boolean> DOWN = new HashMap<>();

    private static Shell shell;

    private Binds() {}

    public static void onClientTick(Minecraft mc) {
        Workstations.Bind[] binds = Workstations.state().binds;
        if (binds.length == 0) return;

        Window window = mc.getWindow();
        boolean live = window != null && mc.gui.screen() == null;

        for (Workstations.Bind bind : binds) {
            Integer code = Setting.parseKey(bind.key);
            if (code == null || code == GLFW.GLFW_KEY_UNKNOWN) continue;

            boolean down = live && isDown(window, code);
            boolean was = Boolean.TRUE.equals(DOWN.put(code, down));
            if (down && !was) run(mc, bind.command);
        }
    }

    private static boolean isDown(Window window, int code) {
        if (code <= Setting.MOUSE_BASE) {
            // A command on the left or right button would run on every ordinary click.
            if (Setting.MOUSE_BASE - code <= GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;
            long handle = window.handle();
            return handle != 0 && !Overlay.active() && mouseDown(handle, Setting.MOUSE_BASE - code);
        }
        return !Overlay.active() && keyDown(window, code);
    }

    /** The host's reading of the key ({@link com.mineux.core.Host#keyDown}): the physical key by default. */
    private static boolean keyDown(Window window, int code) {
        return com.mineux.core.MineUX.ready() ? com.mineux.core.MineUX.host().keyDown(window, code) : InputConstants.isKeyDown(window, code);
    }

    private static boolean mouseDown(long handle, int button) {
        return com.mineux.core.MineUX.ready() ? com.mineux.core.MineUX.host().mouseDown(handle, button)
            : GLFW.glfwGetMouseButton(handle, button) == GLFW.GLFW_PRESS;
    }

    private static void run(Minecraft mc, String command) {
        String said = headless(command);
        if (said != null && mc.player != null && UiConfig.get().announceKeybinds) {
            Notify.show("bind", said, Notify.INFO);
        }
    }

    /**
     * Runs a command in the shell nobody is looking at, and gives back the last thing it said.
     * <p>
     * Shared with the friends list's remote commands ({@code friend.Remote}), which need exactly
     * this and must not have a second headless shell of their own: one of them is the only place
     * in the client where a command can arrive from outside, and it should run through the same
     * tested path a keybind does rather than a parallel one.
     * <p>
     * On the client thread only -- it drives the terminal, which touches screens.
     *
     * @return the last non-empty line the command printed, or null if it printed nothing
     */
    static String headless(String command) {
        if (command == null || command.isBlank()) return null;
        if (shell == null) shell = new Shell(new Headless());

        int before = shell.out.size();
        try {
            shell.click(command);
        } catch (RuntimeException e) {
            return null;
        }

        String said = null;
        for (int i = shell.out.size() - 1; i >= before && said == null; i--) {
            String text = plain(shell.out.get(i)).strip();
            if (!text.isEmpty()) said = text;
        }
        shell.clear();

        if (said != null && said.startsWith("> ")) said = said.substring(2);
        return said;
    }

    private static String plain(Line line) {
        StringBuilder out = new StringBuilder();
        for (Span span : line.spans) out.append(span.text());
        return out.toString();
    }

    /** The key as {@code bind} lists it: {@code r}, {@code mouse4}, {@code rshift}; null for no key. */
    static String canonical(String key) {
        Integer code = Setting.parseKey(key);
        if (code == null || code == GLFW.GLFW_KEY_UNKNOWN) return null;
        return Setting.keyName(code).toLowerCase(Locale.ROOT);
    }

    /** What a bound command can ask for, from a key pressed in game with no desktop open. */
    private static final class Headless implements Shell.Host {

        @Override
        public void exit() {
        }

        @Override
        public void quit() {
        }

        /** A bound quit or reboot asks on the desktop, opened for it. */
        @Override
        public void confirm(String question, String detail, String yes, Runnable onYes) {
            Minecraft mc = Minecraft.getInstance();
            TerminalScreen screen = new TerminalScreen(mc.gui.screen());
            mc.gui.setScreen(screen);
            screen.confirm(question, detail, yes, onYes);
        }

        @Override
        public void art(String arg) {
            ArtView.cycle(arg);
        }

        @Override
        public String artName() {
            return ArtView.name();
        }

        @Override
        public String[] artNames() {
            return ArtView.names();
        }

        @Override
        public void launch(String app) {
        }

        @Override
        public void workstationsChanged() {
        }
    }
}
