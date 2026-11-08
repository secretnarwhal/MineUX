package com.mineux.visuals.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mineux.visuals.VisualBridge;
import static com.mineux.visuals.VisualOps.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player handed to its renderer, the pose stack at its feet: where the player model goes in.
 * Players only -- the renderer draws every living thing, and the rest are left without a call.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class SelfLivingRendererMixin {

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At("HEAD")
    )
    private void mineux$selfAvatarSubmit(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                             CameraRenderState camera, CallbackInfo ci) {
        if (!AvatarRenderState.class.isInstance(state)) return;
        VisualBridge.dispatch(OP_SELF_AVATAR_SUBMIT, new Object[] {state, poseStack, collector});
    }
}
