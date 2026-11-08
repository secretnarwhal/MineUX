package com.mineux.visuals.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mineux.visuals.VisualBridge;
import static com.mineux.visuals.VisualOps.*;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The world's camera shake and the point between the world and the hand: the hurt shake (on the
 * hand too, as vanilla shakes both), the walk bob on the world only -- the hand's own bob is
 * left to the hand -- and motion blur over the world just drawn.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererCameraMixin {

    @Shadow protected abstract void bobView(CameraRenderState cameraState, PoseStack poseStack);

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void mineux$noHurtCam(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        if (VisualBridge.query(OP_NO_HURT_CAM)) ci.cancel();
    }

    @Redirect(method = "renderLevel",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void mineux$worldBob(GameRenderer renderer, CameraRenderState cameraState, PoseStack poseStack) {
        if (!VisualBridge.query(OP_NO_VIEW_BOB)) bobView(cameraState, poseStack);
    }

    @Inject(method = "renderLevel",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
            shift = At.Shift.AFTER))
    private void mineux$motionBlur(DeltaTracker deltaTracker, CallbackInfo ci) {
        VisualBridge.dispatch(OP_MOTION_BLUR, Minecraft.getInstance());
    }
}
