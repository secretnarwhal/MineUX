package com.mineux.screens.capture;

import com.mineux.screens.Screens;
import com.mineux.screens.ScreensConfig;
import com.mineux.screens.bridge.CaptureTarget;
import com.mineux.screens.bridge.CapturedFrame;
import com.mineux.screens.bridge.ScreensWinBridge;
import com.mineux.screens.displays.CapturedScreenDisplay;
import com.mineux.screens.math.ViewFrame;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFWNativeWin32;

import java.util.ArrayList;
import java.util.List;

/**
 * Your floating screens: the native capture bridge, the screens it feeds, and where new ones go.
 * Captured screens are read-only: with Clickable on, clicking one only brings the real window
 * to the foreground. Input on screens is {@link Pointer}'s.
 */
public final class CaptureManager {

    public @Nullable ScreensWinBridge bridge = null;

    public final ArrayList<CapturedScreenDisplay> displays = new ArrayList<>();

    /** Detached camera for arranging screens. */
    public final Freecam freecam = new Freecam();

    public final Pointer pointer = new Pointer(this);

    private final ReturnHotkey returnHotkey = new ReturnHotkey();

    private int appliedFps;

    /** Starts the native bridge, uploads new frames, drops screens whose source is gone. Every frame. */
    public void update() {
        if (bridge == null) start();
        returnHotkey.update(bridge);
        int fps = ScreensConfig.get().captureFps;
        if (fps != appliedFps) {
            appliedFps = fps;
            for (CapturedScreenDisplay d : displays) bridge.setCaptureRate(d.source, fps);
        }
        for (CapturedScreenDisplay display : displays) {
            CapturedFrame frame = display.update(bridge);
            if (frame != null && display.shared) Screens.sharing().offer(display, frame);
        }
        for (CapturedScreenDisplay display : List.copyOf(displays)) {
            if (display.isValid()) continue;
            message(Component.literal("Stopped showing " + display.target.title() + ": " + display.getError()).withStyle(ChatFormatting.GRAY), false);
            stopCapture(display);
        }
    }

    private void start() {
        long window = GLFWNativeWin32.glfwGetWin32Window(Minecraft.getInstance().getWindow().handle());
        bridge = ScreensWinBridge.start(window);
        appliedFps = ScreensConfig.get().captureFps;
        Screens.LOGGER.info("Windows screen capture backend started");
    }

    public CaptureTarget[] monitors() {
        if (bridge == null) start();
        return bridge.getMonitors();
    }

    public CaptureTarget[] windows() {
        if (bridge == null) start();
        return bridge.getWindows();
    }

    /** Start capturing and place it in front of the player. Throws with a readable message when it cannot. */
    public CapturedScreenDisplay startCapture(CaptureTarget target) {
        if (bridge == null) start();
        long source = bridge.startCapture(target, ScreensConfig.get().captureFps);
        CapturedScreenDisplay display = new CapturedScreenDisplay(target, source);
        displays.add(display);
        placeInFront(display);
        return display;
    }

    public void stopCapture(CapturedScreenDisplay display) {
        pointer.forget(display);
        if (display.shared) Screens.sharing().unshare(display);
        displays.remove(display);
        bridge.stopCapture(display.source);
        display.framebuffer.destroy();
    }

    public void stopAll() {
        for (CapturedScreenDisplay d : List.copyOf(displays)) stopCapture(d);
        freecam.disable();
    }

    /** The display captured from {@code target}, or null. */
    public @Nullable CapturedScreenDisplay find(CaptureTarget target) {
        for (CapturedScreenDisplay d : displays) {
            if (d.target.kind() == target.kind() && d.target.handle() == target.handle()) return d;
        }
        return null;
    }

    /** Where the user is looking from: the freecam while it is on, the player otherwise. */
    public @Nullable ViewFrame currentView() {
        if (freecam.isActive()) return freecam.view();
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null ? ViewFrame.of(player) : null;
    }

    /** Move a screen in front of the viewer, far enough away to fit in view. */
    public void placeInFront(CapturedScreenDisplay display) {
        LocalPlayer player = Minecraft.getInstance().player;
        ViewFrame view = currentView();
        if (player == null || view == null) return;
        // The first frame might not have arrived yet: estimate the size from the target.
        double blocks = display.target.width() / display.target.scale() / ScreensConfig.get().pixelsPerBlock;
        if (display.framebuffer.isValid()) blocks = display.worldWidth();
        display.holdDistance = Math.clamp(blocks * 0.9, 1.5, 20.0);
        display.holdInFront(view.pos(), view.forward(), view.up());
        display.storeAnchor(ViewFrame.of(player));
    }

    public void toggleFreecam() {
        if (freecam.isActive()) {
            pointer.releaseGrab();
            freecam.disable();
            message(Component.literal("Freecam off").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (Minecraft.getInstance().player == null) return;
        pointer.releaseGrab();
        freecam.enable();
        message(Component.literal("Freecam: fly with the movement keys, click a screen to move it, scroll to change speed, "
            + com.mineux.screens.Keys.freecamName() + " to leave").withStyle(ChatFormatting.GRAY), true);
    }

    public String returnHotkeyName() {
        return returnHotkey.name();
    }

    public boolean isReturnHotkeyRegistered() {
        return returnHotkey.registered();
    }

    public static void message(Component message, boolean overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        minecraft.gui.chatListener().handleSystemMessage(message, overlay);
    }
}
