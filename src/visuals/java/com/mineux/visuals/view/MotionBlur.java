package com.mineux.visuals.view;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Motion blur: the world just drawn, smeared along the way the camera turned since the last
 * frame. Only the camera's turn, never anything moving on its own: a pixel's streak is where the
 * same direction was on screen a frame ago, so a still camera draws a sharp picture, and the hand
 * and the HUD are drawn after it, sharp.
 * <p>
 * Run from {@code OP_MOTION_BLUR}, between the world and the hand. The turn is the rotation from
 * last frame's view to this one's; its length on screen is that turn through the projection,
 * scaled by {@link CameraConfig#blurAmount} over the frame time (so a fast frame rate does not
 * make the blur fade) and capped at {@link CameraConfig#blurMax}. A turn over 45 degrees in a
 * frame is a cut -- a respawn, a teleport, a perspective change -- and is drawn sharp. The drawing
 * is {@link MotionBlurPass}, on the game's own render API (OpenGL and Vulkan alike); thirty
 * failures in a row turn the blur off until a restart.
 */
public final class MotionBlur {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/camera");

    private static final float CUT_ANGLE = (float) Math.toRadians(45.0);

    private static final Matrix4f view = new Matrix4f();
    private static final Matrix3f current = new Matrix3f();
    private static final Matrix3f previous = new Matrix3f();
    private static final Matrix3f delta = new Matrix3f();
    private static final float[] deltaValues = new float[9];

    private static boolean havePrevious;
    private static long lastNanos;
    private static float averageSeconds;
    private static int errors;
    private static boolean broken;

    private MotionBlur() {}

    /** {@code OP_MOTION_BLUR}, once a frame right after the world is drawn. */
    public static void onWorldDrawn(Minecraft mc) {
        CameraConfig config = CameraConfig.get();
        if (!config.motionBlur || broken || mc.level == null || mc.player == null) {
            havePrevious = false;
            lastNanos = 0L;
            return;
        }
        try {
            run(mc, config);
            errors = 0;
        } catch (Throwable t) {
            errors++;
            if (errors >= 30) {
                broken = true;
                LOGGER.error("Motion blur failed thirty frames in a row and is off until the game restarts", t);
            }
        }
    }

    private static void run(Minecraft mc, CameraConfig config) {
        float seconds = frameSeconds();
        Camera camera = mc.gameRenderer.mainCamera();
        camera.getViewRotationMatrix(view);
        current.set(view);
        if (!havePrevious) {
            previous.set(current);
            havePrevious = true;
            return;
        }
        current.transpose(delta);
        previous.mul(delta, delta);
        previous.set(current);
        float trace = delta.m00() + delta.m11() + delta.m22();
        float angle = (float) Math.acos(Math.max(-1f, Math.min(1f, (trace - 1f) * 0.5f)));
        if (!Float.isFinite(angle) || angle > CUT_ANGLE) return;

        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) return;
        float fovRadians = (float) Math.toRadians(camera.getFov());
        if (fovRadians < 1.0E-3f) return;
        float focalY = (float) (1.0 / Math.tan(fovRadians * 0.5));
        float focalX = focalY * main.height / main.width * (float) CameraShape.aspect();

        float exposure = (float) (config.blurAmount / 100.0 / 24.0);
        float scale = exposure / Math.max(seconds * 0.5f, Math.min(seconds * 1.5f, averageSeconds));
        float longest = angle * Math.max(focalX, focalY) * 0.5f * scale * Math.max(main.width, main.height);
        if (longest < 0.75f) return;

        delta.get(deltaValues);
        MotionBlurPass.draw(main, deltaValues, focalX, focalY, scale,
            (float) (config.blurMax / 100.0), samples(config.blurQuality));
    }

    /** This frame's length in seconds, kept between 1 and 50 ms, and its running average. */
    private static float frameSeconds() {
        long now = System.nanoTime();
        float raw = lastNanos == 0L ? 1f / 60f : (now - lastNanos) / 1.0E9f;
        float seconds = Math.max(0.001f, Math.min(0.05f, raw));
        float average = averageSeconds <= 0f ? seconds : averageSeconds + (seconds - averageSeconds) * 0.25f;
        lastNanos = now;
        averageSeconds = average;
        return seconds;
    }

    private static int samples(String quality) {
        if (Objects.equals(CameraConfig.QUALITY_LOW, quality)) return 6;
        if (Objects.equals(CameraConfig.QUALITY_HIGH, quality)) return 16;
        return 10;
    }
}
