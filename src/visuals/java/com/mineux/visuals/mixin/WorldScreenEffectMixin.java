package com.mineux.visuals.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mineux.visuals.VisualBridge;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mineux.visuals.VisualOps.*;

/** What is drawn over your own view: the flames while you burn, and a popped totem's spin. */
@Mixin(ScreenEffectRenderer.class)
public abstract class WorldScreenEffectMixin {

    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void mineux$fire(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (VisualBridge.query(OP_WORLD_HIDE, HIDE_FIRE)) ci.cancel();
    }

    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
    private void mineux$totem(PoseStack poseStack, float partialTicks, SubmitNodeCollector collector, CallbackInfo ci) {
        if (VisualBridge.query(OP_WORLD_HIDE, HIDE_TOTEM_ANIMATION)) ci.cancel();
    }
}
