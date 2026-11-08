package com.mineux.screens.capture;

import com.mineux.screens.ScreensConfig;
import com.mineux.screens.bridge.ScreensWinBridge;
import com.mineux.screens.displays.CapturedScreenDisplay;
import com.mineux.screens.displays.CapturedScreenDisplay.CaptureHit;
import com.mineux.screens.gui.HoverIndicator;
import com.mineux.screens.math.ViewFrame;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * What the crosshair and the mouse do with your screens: which one is under the crosshair,
 * picking one up and placing it, clicking through to its window, and the freecam's look.
 */
public final class Pointer {

    /** Screens farther away than this cannot be clicked. */
    private static final double MAX_CLICK_DISTANCE = 64.0;
    /** How close and far a screen can be held while it is moved. */
    private static final double MIN_GRAB_DISTANCE = 0.5;
    private static final double MAX_GRAB_DISTANCE = 64.0;

    private final CaptureManager manager;

    /** Screen being moved, placed on the next click. */
    public @Nullable CapturedScreenDisplay grabbed = null;
    /** Screen under the crosshair; only set while screens can be clicked or picked up. */
    public @Nullable CaptureHit hovered = null;

    /** What the player aims at in the world, recorded before a screen can hide it. */
    public @Nullable HitResult worldTarget = null;
    /** While set the player targets nothing in the world: a screen or the freecam has the crosshair. */
    public boolean blockWorldTargeting = false;

    // Offset of the grabbed screen from the crosshair, blocks along the view's right and up.
    private double grabPanX;
    private double grabPanY;

    private boolean shownReturnHint;
    private boolean shownFullscreenWarning;

    Pointer(CaptureManager manager) {
        this.manager = manager;
    }

    void forget(CapturedScreenDisplay display) {
        if (grabbed == display) grabbed = null;
        if (hovered != null && hovered.target() == display) hovered = null;
    }

    /** Pick a screen up: it moves to the crosshair and follows the view until placed. */
    public void startGrab(CapturedScreenDisplay display) {
        ViewFrame view = manager.currentView();
        if (view == null) return;
        grabbed = display;
        grabPanX = 0.0;
        grabPanY = 0.0;
        display.holdDistance = Math.clamp(display.placement.center().distanceTo(view.pos()), MIN_GRAB_DISTANCE, MAX_GRAB_DISTANCE);
        showGrabHint();
    }

    /** Pick a screen up where it is, without moving its centre to the crosshair. */
    public void startGrabInPlace(CapturedScreenDisplay display) {
        ViewFrame view = manager.currentView();
        if (view == null) return;
        Vec3 offset = view.toLocal(display.placement.center().subtract(view.pos()));
        grabbed = display;
        grabPanX = offset.x;
        grabPanY = offset.y;
        display.holdDistance = Math.clamp(offset.z, MIN_GRAB_DISTANCE, MAX_GRAB_DISTANCE);
        showGrabHint();
    }

    private static void showGrabHint() {
        CaptureManager.message(Component.literal("Click to place  |  Scroll: closer / farther  |  Hold right click: slide  |  Alt: attach to walls  |  Ctrl: snap").withStyle(ChatFormatting.GRAY), true);
    }

    public void releaseGrab() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (grabbed != null && player != null) grabbed.storeAnchor(ViewFrame.of(player));
        grabbed = null;
    }

    /** After the level's extraction, every frame: anchors, the held screen, what is hovered. */
    public void updateWorld(Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        hovered = null;
        HoverIndicator.kind = HoverIndicator.NONE;
        blockWorldTargeting = false;
        if (player == null) return;
        // Opening any screen (the pause menu too) places a held screen where it is.
        if (minecraft.gui.screen() != null) releaseGrab();
        ViewFrame view = ViewFrame.of(player);
        for (CapturedScreenDisplay display : manager.displays) {
            if (display != grabbed) display.applyAnchor(view);
        }
        Vec3 pos = camera.position();
        Vec3 look = new Vec3(camera.forwardVector());
        Vec3 up = new Vec3(camera.upVector());
        if (manager.freecam.isActive()) blockWorldTargeting = true;
        if (grabbed != null) {
            Vec3 right = look.cross(up);
            Vec3 grabPos = pos.add(right.scale(grabPanX)).add(up.scale(grabPanY));
            grabbed.followView(grabPos, look, up, camera.yRot(), manager.displays);
            blockWorldTargeting = true;
            return;
        }
        if (minecraft.gui.screen() != null || player.isUsingItem() || !(canTabInto() || canPickUp())) return;
        hover(pos, look);
    }

    private void hover(Vec3 pos, Vec3 look) {
        CaptureHit closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (CapturedScreenDisplay display : manager.displays) {
            CaptureHit hit = display.intersect(pos, look);
            if (hit == null || hit.dist() < 0) continue;
            double distance = hit.position().distanceToSqr(pos);
            if (distance < closestDistance) {
                closest = hit;
                closestDistance = distance;
            }
        }
        if (closest == null || closestDistance > MAX_CLICK_DISTANCE * MAX_CLICK_DISTANCE) return;
        // Blocks and entities in front of the screen win. The game traces from the player, so
        // its hit means nothing for the freecam.
        HitResult gameHit = worldTarget;
        if (!manager.freecam.isActive() && gameHit != null && gameHit.getType() != HitResult.Type.MISS
            && gameHit.getLocation().distanceToSqr(pos) < closestDistance) return;
        hovered = closest;
        blockWorldTargeting = true;
        HoverIndicator.kind = canTabInto() ? HoverIndicator.SWITCH : HoverIndicator.MOVE;
    }

    private boolean canTabInto() {
        return ScreensConfig.get().clickable && !manager.freecam.isActive();
    }

    private boolean canPickUp() {
        ScreensConfig config = ScreensConfig.get();
        return manager.freecam.isActive() || (config.draggable && !config.clickable);
    }

    private boolean isPanning() {
        return grabbed != null && GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    }

    /** A mouse button in game; true consumes it. */
    public boolean onButtonPress(int button, int action) {
        if (grabbed != null) {
            // The right button slides the screen while held, see onMouseTurn.
            if (action == GLFW.GLFW_PRESS && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) releaseGrab();
            return true;
        }
        // Releases always reach the game, so a button held before looking at a screen is not stuck.
        if (action != GLFW.GLFW_PRESS) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hovered != null) {
            if (canTabInto()) tabInto(hovered);
            else startGrabInPlace(hovered.target());
        }
        // Nothing mined or placed behind the screen, nor anything at all from the freecam.
        return hovered != null || manager.freecam.isActive();
    }

    /** The wheel in game; true consumes it. */
    public boolean onScroll(double scrollY) {
        if (grabbed != null) {
            grabbed.holdDistance = Math.clamp(grabbed.holdDistance * Math.pow(1.1, scrollY), MIN_GRAB_DISTANCE, MAX_GRAB_DISTANCE);
            return true;
        }
        if (manager.freecam.isActive()) {
            manager.freecam.adjustSpeed(scrollY);
            CaptureManager.message(Component.literal(String.format("Freecam speed: %.1f blocks/s", manager.freecam.speed())).withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        return false;
    }

    /** Mouse movement that would turn the player; true consumes it. */
    public boolean onMouseTurn(double dx, double dy) {
        if (isPanning()) {
            double blocksPerPixel = grabbed.holdDistance * 0.0015;
            grabPanX += dx * blocksPerPixel;
            grabPanY -= dy * blocksPerPixel;
            return true;
        }
        if (manager.freecam.isActive()) {
            manager.freecam.turn(dx, dy);
            return true;
        }
        return false;
    }

    private void tabInto(CaptureHit hit) {
        Minecraft minecraft = Minecraft.getInstance();
        CapturedScreenDisplay display = hit.target();
        if (minecraft.getWindow().isFullscreen() && !shownFullscreenWarning) {
            shownFullscreenWarning = true;
            CaptureManager.message(Component.literal("Minecraft is in fullscreen mode. Windows may minimize it when switching to another window, windowed mode works best.").withStyle(ChatFormatting.YELLOW), false);
        }
        int result = manager.bridge.tabInto(display.source, hit.x(), hit.y());
        if (result == ScreensWinBridge.FOCUS_OK) {
            if (!shownReturnHint && manager.isReturnHotkeyRegistered()) {
                shownReturnHint = true;
                CaptureManager.message(Component.literal("Press " + manager.returnHotkeyName() + " to return to Minecraft").withStyle(ChatFormatting.GRAY), false);
            }
        } else if (result == ScreensWinBridge.FOCUS_NO_WINDOW) {
            CaptureManager.message(Component.literal(display.target.isMonitor() ? "There is no window at that spot" : "The window no longer exists"), true);
        } else if (result == ScreensWinBridge.FOCUS_BLOCKED) {
            CaptureManager.message(Component.literal("Windows doesn't allow switching to this window because it runs as administrator").withStyle(ChatFormatting.RED), true);
        } else {
            CaptureManager.message(Component.literal("Windows refused to switch to that window").withStyle(ChatFormatting.RED), true);
        }
    }
}
