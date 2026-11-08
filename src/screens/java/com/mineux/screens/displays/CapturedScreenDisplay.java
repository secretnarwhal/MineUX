package com.mineux.screens.displays;

import com.mineux.screens.ScreensConfig;
import com.mineux.screens.bridge.CaptureTarget;
import com.mineux.screens.bridge.CapturedFrame;
import com.mineux.screens.bridge.ScreensWinBridge;
import com.mineux.screens.math.ViewFrame;
import com.mineux.screens.render.FrameTexture;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;

/** A monitor or window of yours mirrored into the world. */
public class CapturedScreenDisplay extends Surface {

    public final CaptureTarget target;

    /** Native capture source handle. */
    public final long source;

    /** Its id on the relay while shared; fresh every capture, so nothing ties two sessions. */
    public final UUID id = UUID.randomUUID();

    public final FrameTexture framebuffer = new FrameTexture();

    /** Size multiplier on top of the pixels per block setting. */
    public double sizeScale = 1.0;

    /** Distance from the viewer while the screen is being moved. */
    public double holdDistance = 2.0;

    /** Shared with friends on this server who have screens enabled: off for every new screen. */
    public boolean shared;

    /** How many players are watching it, from the relay; 0 while not shared. */
    public int viewers;

    /** Reason the capture stopped, null while it is running. */
    private @Nullable String error = null;

    private int anchorMode = Anchor.WORLD;

    // Placement remembered relative to the player for the non-world anchor modes. FOLLOW_VIEW
    // stores all three in the player's view basis, FOLLOW_POSITION only the world space offset.
    private Vec3 anchorOffset = Vec3.ZERO;
    private Vec3 anchorFacing = new Vec3(0, 0, 1);
    private Vec3 anchorUp = new Vec3(0, 1, 0);

    public CapturedScreenDisplay(CaptureTarget target, long source) {
        this.target = target;
        this.source = source;
    }

    @Override
    public FrameTexture texture() {
        return framebuffer;
    }

    /**
     * Uploads the newest captured frame, if any, and checks the capture still runs. Returns the
     * frame (valid until the next poll) for the sharing encoder, or null.
     */
    public @Nullable CapturedFrame update(ScreensWinBridge bridge) {
        if (error != null) return null;
        CapturedFrame frame = bridge.pollFrame(source);
        if (frame != null) framebuffer.upload(frame.ptr(), frame.width(), frame.height(), frame.scale());
        error = bridge.getCaptureError(source);
        return frame;
    }

    public @Nullable String getError() {
        return error;
    }

    public boolean isValid() {
        return error == null;
    }

    // Captured frames are in physical pixels. Dividing out the DPI scale makes screens the same
    // size in the world whatever the scaling of the monitor they are on.
    private double blocksPerPixel() {
        return sizeScale / (ScreensConfig.get().pixelsPerBlock * framebuffer.scale());
    }

    @Override
    public double worldWidth() {
        return framebuffer.width() * blocksPerPixel();
    }

    @Override
    public double worldHeight() {
        return framebuffer.height() * blocksPerPixel();
    }

    @Override
    public double arcAngle() {
        return ScreensConfig.get().arcAngle();
    }

    public int anchorMode() {
        return anchorMode;
    }

    /** Switch anchor modes without moving the display. */
    public void setAnchorMode(int mode, ViewFrame view) {
        this.anchorMode = mode;
        storeAnchor(view);
    }

    /** Remember the current world placement relative to the viewer. Call after moving the display. */
    public void storeAnchor(ViewFrame view) {
        if (anchorMode == Anchor.FOLLOW_VIEW) {
            anchorOffset = view.toLocal(placement.center().subtract(view.pos()));
            anchorFacing = view.toLocal(placement.facing());
            anchorUp = view.toLocal(placement.up());
        } else if (anchorMode == Anchor.FOLLOW_POSITION) {
            anchorOffset = placement.center().subtract(view.pos());
        }
    }

    /** Move the display to its remembered placement relative to the viewer. */
    public void applyAnchor(ViewFrame view) {
        if (anchorMode == Anchor.FOLLOW_VIEW) {
            placement.moveTo(view.pos().add(view.toWorld(anchorOffset)));
            placement.orient(view.toWorld(anchorFacing), view.toWorld(anchorUp));
        } else if (anchorMode == Anchor.FOLLOW_POSITION) {
            placement.moveTo(view.pos().add(anchorOffset));
        }
    }

    /** Hold the screen {@code holdDistance} blocks in front of a viewer, facing them. */
    public void holdInFront(Vec3 eye, Vec3 look, Vec3 viewUp) {
        placement.faceViewer(eye, look, viewUp, holdDistance);
    }

    /**
     * Follow the view while the screen is being moved. Alt sticks it to the block being looked
     * at (with Ctrl: on a grid), Ctrl alone lines it up with other screens.
     */
    public void followView(Vec3 eye, Vec3 look, Vec3 viewUp, float yaw, List<? extends Surface> others) {
        holdInFront(eye, look, viewUp);
        boolean alt = InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_ALT);
        boolean ctrl = InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL);
        if (alt) ScreenSnapping.ontoBlock(placement, eye, look, yaw, ctrl);
        else if (ctrl) ScreenSnapping.besideNeighbor(this, others, eye, look);
    }

    /** Ray against the captured image; {@code dir} normalised. Null when it misses. */
    public @Nullable CaptureHit intersect(Vec3 pos, Vec3 dir) {
        return Hits.intersect(this, pos, dir);
    }

    /**
     * A ray hitting a captured screen: (x, y) is the hit pixel of the frame, {@code dist}
     * negative when the screen was hit from behind.
     */
    public record CaptureHit(CapturedScreenDisplay target, Vec3 position, double x, double y, double dist) {}
}
