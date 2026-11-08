package com.mineux.visuals.mixin;

import com.mineux.visuals.VisualBridge;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mineux.visuals.VisualOps.*;

/** The first-person hand drawn, its depth still the hand's alone: the hand shaders' pass. */
@Mixin(GameRenderer.class)
public abstract class GameRendererHandMixin {

    @Inject(
        method = "renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V",
        at = @At("TAIL")
    )
    private void mineux$handDrawn(CameraRenderState camera, float partialTicks, Matrix4fc projection, CallbackInfo ci) {
        VisualBridge.dispatch(OP_HAND_DRAWN);
    }
}
