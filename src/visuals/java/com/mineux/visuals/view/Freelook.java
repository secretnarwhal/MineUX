package com.mineux.visuals.view;

import com.mineux.core.input.Keys;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

import java.util.Objects;

/**
 * Freelook: while its key is held (or after a press, in toggle mode), the camera turns with the
 * mouse and the body does not. The body's look -- what the server is shown and what the
 * crosshair picks -- stays exactly where it was.
 * <p>
 * The hand's movement comes down {@code MouseHandler#turnPlayer}, where the visuals' mouse mixin
 * hands it to {@link #turn} for the camera; nothing here writes the player's rotation.
 * <p>
 * Switching on puts the camera where the body was looking and goes to the perspective asked for;
 * switching off goes back to the perspective you had, and the camera snaps back onto the body.
 * Session state only: it never comes back on by itself on a launch.
 */
public final class Freelook {

    /** {@code Entity#turn}'s own scale, applied to the same deltas it would have had. */
    private static final float TURN_SCALE = 0.15f;

    private static boolean active;
    private static float yaw, pitch;
    private static CameraType previous;

    /** Toggle mode's switch, and last frame's key, for the press edge. */
    private static boolean toggled;
    private static boolean wasDown;

    private Freelook() {}

    public static boolean active() {
        return active;
    }

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
    }

    /** Once a frame, at the head of the mouse's own frame, before any turn. */
    public static void onFrame(Minecraft mc) {
        CameraConfig config = CameraConfig.get();
        LocalPlayer player = mc.player;
        if (!config.freelook || player == null || mc.getCameraEntity() != player) {
            toggled = false;
            stop(mc);
            return;
        }
        // A screen has the keys: the key's state is left as it was until it closes.
        if (mc.gui.screen() != null) return;
        boolean down = config.freelookKey != Keys.UNBOUND && Keys.isDown(mc.getWindow(), config.freelookKey);
        if (down && !wasDown) toggled = !toggled;
        wasDown = down;
        boolean want = config.freelookHold ? down : toggled;
        if (want && !active) start(mc, player, config);
        else if (!want && active) stop(mc);
    }

    private static void start(Minecraft mc, LocalPlayer player, CameraConfig config) {
        yaw = player.getYRot();
        pitch = player.getXRot();
        previous = mc.options.getCameraType();
        mc.options.setCameraType(perspective(config.freelookView));
        active = true;
    }

    private static void stop(Minecraft mc) {
        if (!active) return;
        active = false;
        if (previous != null) mc.options.setCameraType(previous);
        previous = null;
    }

    private static CameraType perspective(String view) {
        if (Objects.equals(CameraConfig.VIEW_FIRST, view)) return CameraType.FIRST_PERSON;
        if (Objects.equals(CameraConfig.VIEW_FRONT, view)) return CameraType.THIRD_PERSON_FRONT;
        return CameraType.THIRD_PERSON_BACK;
    }

    /** The hand's turn, from {@code MouseHandler#turnPlayer}: true when the camera took it. */
    public static boolean turn(double dx, double dy) {
        if (!active) return false;
        float nextYaw = Mth.wrapDegrees(yaw + (float) (dx * TURN_SCALE));
        float nextPitch = Mth.clamp(pitch + (float) (dy * TURN_SCALE), -90f, 90f);
        yaw = nextYaw;
        pitch = nextPitch;
        return true;
    }

}
