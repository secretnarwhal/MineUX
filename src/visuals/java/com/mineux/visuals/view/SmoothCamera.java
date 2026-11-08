package com.mineux.visuals.view;

import com.mineux.visuals.mixin.CameraAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The smooth camera: the camera's look eases after the body's instead of being on it, and in
 * third person its position trails the body too, pulled in front of any block between the two.
 * Only the camera: the body, its look and everything aimed with it are where they always were.
 * <p>
 * Eased by time, not by frame: each frame closes {@code 1 - e^(-speed * seconds)} of the gap, so
 * the feel is the same at 60 frames a second and at 400. A frame more than a quarter second after
 * the last, or a jump of more than 20 blocks, starts again on the body rather than sweeping
 * across.
 * <p>
 * The arithmetic is done on locals and the fields written last: the obfuscator can move a call
 * that reads a field past a later write of the same field.
 */
public final class SmoothCamera {

    private static boolean lookOn;
    private static float yaw, pitch;
    private static long lastLook;

    private static boolean placeOn;
    private static double px, py, pz;
    private static long lastPlace;

    private SmoothCamera() {}

    public static boolean on() {
        return CameraConfig.get().smoothCamera;
    }

    /** The eased look toward {@code targetYaw, targetPitch}, as {yaw, pitch}. */
    public static float[] look(float targetYaw, float targetPitch) {
        CameraConfig config = CameraConfig.get();
        long now = System.nanoTime();
        double seconds = (now - lastLook) / 1.0E9;
        boolean fresh = !lookOn || seconds > 0.25 || seconds <= 0.0;
        float[] out = fresh ? new float[] {targetYaw, targetPitch}
            : eased(yaw, pitch, targetYaw, targetPitch, ease(config.smoothSpeed, seconds), config.smoothPitch);
        lastLook = now;
        lookOn = true;
        yaw = out[0];
        pitch = out[1];
        return out;
    }

    private static float[] eased(float fromYaw, float fromPitch, float toYaw, float toPitch, float a, boolean withPitch) {
        float nextYaw = wrap(fromYaw + wrap(toYaw - fromYaw) * a);
        float nextPitch = withPitch ? fromPitch + (toPitch - fromPitch) * a : toPitch;
        return new float[] {nextYaw, nextPitch};
    }

    private static float ease(double speed, double seconds) {
        return (float) (1.0 - Math.exp((0.0 - speed) * seconds));
    }

    private static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d >= 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    /** {@code OP_CAMERA_ALIGNED}: the third-person camera's position, trailed. */
    public static void place(Minecraft mc, Camera camera, float partialTicks) {
        CameraConfig config = CameraConfig.get();
        Entity entity = camera.entity();
        if (!on() || !config.smoothLag || !camera.isDetached() || entity == null || mc.level == null) {
            placeOn = false;
            return;
        }
        Vec3 target = camera.position();
        long now = System.nanoTime();
        double seconds = (now - lastPlace) / 1.0E9;
        double dx = target.x - px;
        double dy = target.y - py;
        double dz = target.z - pz;
        boolean fresh = !placeOn || seconds > 0.25 || seconds <= 0.0 || dx * dx + dy * dy + dz * dz > 400.0;
        lastPlace = now;
        placeOn = true;
        if (fresh) {
            px = target.x;
            py = target.y;
            pz = target.z;
            return;
        }
        double a = ease(config.smoothLagSpeed, seconds);
        Vec3 trailed = new Vec3(px + dx * a, py + dy * a, pz + dz * a);
        px = trailed.x;
        py = trailed.y;
        pz = trailed.z;
        ((CameraAccessor) camera).mineux$setPosition(clip(mc, entity, trailed, partialTicks));
    }

    /** Kept on the eye's side of any block between the eye and the trailed camera. */
    private static Vec3 clip(Minecraft mc, Entity entity, Vec3 pos, float partialTicks) {
        Vec3 eye = entity.getEyePosition(partialTicks);
        HitResult hit = mc.level.clip(new ClipContext(eye, pos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
        if (hit == null || hit.getType() == HitResult.Type.MISS) return pos;
        Vec3 at = hit.getLocation();
        Vec3 back = eye.subtract(at);
        double length = back.length();
        return length > 1.0E-4 ? at.add(back.scale(Math.min(0.2, length) / length)) : at;
    }
}
