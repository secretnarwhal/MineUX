package com.mineux.visuals.view;

import com.mineux.visuals.mixin.CameraAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/**
 * What the camera hooks ({@code CameraViewMixin}, {@code GameRendererCameraMixin}) ask: the
 * camera's look while freelook or the smooth camera has it, camera tweaks' distance and no clip,
 * no fov, no bob, no hurt cam and the stretched picture. Each is read off {@link CameraConfig}
 * as it is asked, so a change in a settings window shows on the next frame.
 */
public final class CameraShape {

    /** The pitch worked out with this frame's yaw, which the camera asks for first. */
    private static float framePitch;

    private CameraShape() {}

    /** {@code OP_CAMERA_LOOK}: whether the camera has a look of its own this frame. */
    public static boolean ownLook() {
        return Freelook.active() || SmoothCamera.on();
    }

    /** {@code OP_CAMERA_YAW}: works out the frame's look; the pitch is kept for {@link #pitch}. */
    public static double yaw(float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        Entity entity = mc.getCameraEntity();
        if (entity == null) entity = mc.player;
        if (entity == null) return 0.0;
        float[] look = Freelook.active() ? new float[] {Freelook.yaw(), Freelook.pitch()}
            : new float[] {entity.getViewYRot(partialTicks), entity.getViewXRot(partialTicks)};
        if (SmoothCamera.on()) look = SmoothCamera.look(look[0], look[1]);
        framePitch = look[1];
        return look[0];
    }

    /** {@code OP_CAMERA_PITCH}. */
    public static double pitch() {
        return framePitch;
    }

    /** {@code OP_CAMERA_DISTANCE}: vanilla's third-person distance scaled to the set one, 0 for vanilla's. */
    public static double distance(float vanilla) {
        CameraConfig config = CameraConfig.get();
        if (!config.cameraTweaks) return 0.0;
        return vanilla * config.cameraDistance / 4.0;
    }

    public static boolean noHurtCam() {
        return CameraConfig.get().noHurtCam;
    }

    public static boolean noBob() {
        return CameraConfig.get().noBob;
    }

    /** {@code OP_ASPECT}: how much wider the picture is drawn, 1 for not at all. */
    public static double aspect() {
        CameraConfig config = CameraConfig.get();
        return config.aspect ? config.aspectStretch : 1.0;
    }

    /** {@code OP_CAMERA_ALIGNED}: the camera is placed and the field of view not yet worked out. */
    public static void aligned(Minecraft mc, Camera camera, float partialTicks) {
        SmoothCamera.place(mc, camera, partialTicks);
        noFov(mc, camera);
    }

    /**
     * The field of view modifier held at what it would be with the fov effects slider at 0: 1,
     * or the spyglass's own, so looking through one still zooms. Vanilla eases the modifier a
     * tick at a time; this writes both ends of that ease, so the frame's fov is the plain one.
     */
    private static void noFov(Minecraft mc, Camera camera) {
        if (!CameraConfig.get().noFov) return;
        LocalPlayer player = mc.player;
        if (player == null || !AbstractClientPlayer.class.isInstance(mc.getCameraEntity())) return;
        float modifier = ((AbstractClientPlayer) mc.getCameraEntity())
            .getFieldOfViewModifier(mc.options.getCameraType().isFirstPerson(), 0f);
        CameraAccessor accessor = (CameraAccessor) camera;
        accessor.mineux$setFovModifier(modifier);
        accessor.mineux$setOldFovModifier(modifier);
    }
}
