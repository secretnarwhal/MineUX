package com.mineux.visuals.mixin;

import com.mineux.visuals.VisualBridge;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mineux.visuals.VisualOps.*;

/** The frame's lightmap once the game has worked it out: ambience's light tint goes over it. */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapExtractorMixin {

    @Inject(method = "extract(Lnet/minecraft/client/renderer/state/LightmapRenderState;F)V", at = @At("TAIL"))
    private void mineux$lightmap(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
        VisualBridge.dispatch(OP_LIGHTMAP, state);
    }
}
