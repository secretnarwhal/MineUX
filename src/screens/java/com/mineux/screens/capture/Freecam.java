package com.mineux.screens.capture;

import com.mineux.screens.math.ViewFrame;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/* Detached camera for arranging screens
 *
 * While active the player stays where they are and movement and mouse input fly the camera instead.
 * Nothing is sent to the server, the camera is purely visual.
 */
public class Freecam {

    private static final double MIN_SPEED = 1.0;
    private static final double MAX_SPEED = 64.0;

    private boolean active = false;

    private Vec3 pos = Vec3.ZERO;
    private float yRot = 0.0f;
    private float xRot = 0.0f;

    // Blocks per second
    private double speed = 8.0;

    private long lastUpdate = 0;

    public boolean isActive() {
        return active;
    }

    // Start flying from where the camera currently is
    public void enable() {
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        pos = camera.position();
        yRot = camera.yRot();
        xRot = camera.xRot();
        lastUpdate = System.nanoTime();
        active = true;
    }

    public void disable() {
        active = false;
    }

    public Vec3 pos() {
        return pos;
    }

    public float yRot() {
        return yRot;
    }

    public float xRot() {
        return xRot;
    }

    public double speed() {
        return speed;
    }

    public Vec3 forward() {
        return Vec3.directionFromRotation(xRot, yRot);
    }

    public Vec3 up() {
        return Vec3.directionFromRotation(xRot - 90.0f, yRot);
    }

    public ViewFrame view() {
        return new ViewFrame(pos, forward(), up());
    }

    // Turn the camera by a raw mouse movement, using the same sensitivity as the player
    public void turn(double dx, double dy) {
        Options options = Minecraft.getInstance().options;
        double ss = options.sensitivity().get() * 0.6 + 0.2;
        double sens = ss * ss * ss * 8.0 * 0.15;

        yRot += (float) (dx * sens * (options.invertMouseX().get() ? -1 : 1));
        xRot = Mth.clamp(xRot + (float) (dy * sens * (options.invertMouseY().get() ? -1 : 1)), -90.0f, 90.0f);
    }

    // Scroll wheel changes the flying speed
    public void adjustSpeed(double scroll) {
        speed = Math.clamp(speed * Math.pow(1.2, scroll), MIN_SPEED, MAX_SPEED);
    }

    /* Fly using the movement keys
     * Called once per frame before the camera is set up
     */
    public void update() {
        long now = System.nanoTime();
        double dt = Math.min((now - lastUpdate) / 1.0e9, 0.1);
        lastUpdate = now;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() != null) return;

        Options options = minecraft.options;
        double forward = (options.keyUp.isDown() ? 1 : 0) - (options.keyDown.isDown() ? 1 : 0);
        double right = (options.keyRight.isDown() ? 1 : 0) - (options.keyLeft.isDown() ? 1 : 0);
        double up = (options.keyJump.isDown() ? 1 : 0) - (options.keyShift.isDown() ? 1 : 0);
        if (forward == 0 && right == 0 && up == 0) return;

        Vec3 look = forward();
        Vec3 move = look.scale(forward)
                .add(look.cross(up()).scale(right))
                .add(0, up, 0);
        if (move.lengthSqr() < 1.0e-6) return;

        double distance = speed * dt * (options.keySprint.isDown() ? 3.0 : 1.0);
        pos = pos.add(move.normalize().scale(distance));
    }

}
