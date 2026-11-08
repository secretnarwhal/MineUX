package com.mineux.visuals.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mineux.visuals.VisualBridge;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mineux.visuals.VisualOps.*;

/**
 * The sky: its colours once they are extracted, and each of its pieces as it is about to be
 * drawn, any of which the visuals may take over (a sky shader drawing the disc) or leave out
 * (the stars and the sunset glow under that shader).
 */
@Mixin(SkyRenderer.class)
public abstract class WorldSkyMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/state/level/SkyRenderState;)V",
        at = @At("TAIL")
    )
    private void mineux$sky(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
        VisualBridge.dispatch(OP_WORLD_SKY, state);
    }

    @Inject(method = "renderSkyDisc(I)V", at = @At("HEAD"), cancellable = true)
    private void mineux$skyDisc(int skyColor, CallbackInfo ci) {
        if (VisualBridge.query(OP_WORLD_HIDE, HIDE_SKY_DISC)) ci.cancel();
    }

    @Inject(method = "renderEndSky()V", at = @At("HEAD"), cancellable = true)
    private void mineux$endSky(CallbackInfo ci) {
        if (VisualBridge.query(OP_WORLD_HIDE, HIDE_END_SKY)) ci.cancel();
    }

    @Inject(method = "renderSunriseAndSunset(Lcom/mojang/blaze3d/vertex/PoseStack;FI)V", at = @At("HEAD"), cancellable = true)
    private void mineux$sunrise(PoseStack poseStack, float angle, int color, CallbackInfo ci) {
        if (VisualBridge.query(OP_WORLD_HIDE, HIDE_SKY_EXTRAS)) ci.cancel();
    }

    @Inject(method = "renderEndFlash(Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V", at = @At("HEAD"), cancellable = true)
    private void mineux$endFlash(PoseStack poseStack, float intensity, float xAngle, float yAngle, CallbackInfo ci) {
        if (VisualBridge.query(OP_WORLD_HIDE, HIDE_SKY_EXTRAS)) ci.cancel();
    }

    @Inject(method = "renderDarkDisc()V", at = @At("HEAD"), cancellable = true)
    private void mineux$darkDisc(CallbackInfo ci) {
        if (VisualBridge.query(OP_WORLD_HIDE, HIDE_SKY_EXTRAS)) ci.cancel();
    }

    @Inject(
        method = "renderSunMoonAndStars(Lcom/mojang/blaze3d/vertex/PoseStack;FFFLnet/minecraft/world/level/MoonPhase;FF)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void mineux$sunMoon(PoseStack poseStack, float sun, float moon, float stars, MoonPhase phase,
                                    float rain, float starBrightness, CallbackInfo ci) {
        if (VisualBridge.query(OP_WORLD_HIDE, HIDE_SUN_MOON)) ci.cancel();
    }
}
