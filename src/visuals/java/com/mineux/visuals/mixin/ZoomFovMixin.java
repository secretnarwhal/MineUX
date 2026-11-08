package com.mineux.visuals.mixin;

import com.mineux.visuals.VisualBridge;
import static com.mineux.visuals.VisualOps.*;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The zoom's field of view: the frame's own, as {@code Camera#calculateFov} works it out (the
 * video setting, sprinting, the spyglass, water), handed to the visuals to divide. A float once
 * a frame, where the video setting is whole degrees -- the visuals' zoom used to write that
 * setting, and its whole-degree steps were the judder. A panorama keeps its 90.
 */
@Mixin(Camera.class)
public abstract class ZoomFovMixin {

    @Shadow private boolean isPanoramicMode;

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void mineux$zoom(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (isPanoramicMode) return;
        double fov = VisualBridge.queryDouble(OP_ZOOM_FOV, cir.getReturnValueF());
        if (fov > 0.0) cir.setReturnValue((float) fov);
    }
}
