package com.mineux.visuals.mixin;

import com.mineux.visuals.VisualBridge;
import static com.mineux.visuals.VisualOps.*;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The camera's own look, distance and shape, for the visuals' camera settings: freelook and the
 * smooth camera turn it apart from the body, camera tweaks move it out (still pulled in front of
 * blocks, as vanilla's is), the stretch widens its picture.
 */
@Mixin(Camera.class)
public abstract class CameraViewMixin {

    @Shadow protected abstract float getMaxZoom(float cameraDist);

    @Redirect(method = "alignWithEntity(F)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float mineux$lookYaw(Entity entity, float partialTicks) {
        if (!VisualBridge.query(OP_CAMERA_LOOK)) return entity.getViewYRot(partialTicks);
        return (float) VisualBridge.queryDouble(OP_CAMERA_YAW, partialTicks);
    }

    @Redirect(method = "alignWithEntity(F)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float mineux$lookPitch(Entity entity, float partialTicks) {
        if (!VisualBridge.query(OP_CAMERA_LOOK)) return entity.getViewXRot(partialTicks);
        return (float) VisualBridge.queryDouble(OP_CAMERA_PITCH, partialTicks);
    }

    @Redirect(method = "alignWithEntity(F)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
    private float mineux$distance(Camera camera, float cameraDist) {
        float distance = (float) VisualBridge.queryDouble(OP_CAMERA_DISTANCE, cameraDist);
        if (distance <= 0f) return getMaxZoom(cameraDist);
        return getMaxZoom(distance);
    }

    @Inject(method = "alignWithEntity(F)V", at = @At("TAIL"))
    private void mineux$aligned(float partialTicks, CallbackInfo ci) {
        VisualBridge.dispatch(OP_CAMERA_ALIGNED, this, partialTicks);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void mineux$aspect(CameraRenderState state, float partialTicks, CallbackInfo ci) {
        float stretch = (float) VisualBridge.queryDouble(OP_ASPECT);
        if (stretch > 0f && stretch != 1f) state.projectionMatrix.scale(stretch, 1f, 1f);
    }
}
