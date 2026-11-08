package com.mineux.screens.fabric.mixin;

import com.mineux.screens.ScreensHooks;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The screens go into the level's submits with its entities, before its gizmos are collected. */
@Mixin(LevelRenderer.class)
public abstract class ScreensLevelRendererMixin {

    @Inject(
        method = "submitFeatures",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;finalizeGizmoCollection()V")
    )
    private void mineuxScreens$submit(LevelRenderState levelRenderState, SubmitNodeCollector submitNodeCollector, boolean renderOutline, CallbackInfo ci) {
        ScreensHooks.submit(submitNodeCollector, levelRenderState.cameraRenderState.pos);
    }
}
