package com.mineux.screens.fabric.mixin;

import com.mineux.screens.ScreensHooks;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After the level is extracted for a frame: anchors, the held screen, the hovered one. */
@Mixin(LevelExtractor.class)
public abstract class ScreensLevelExtractorMixin {

    @Inject(method = "extract", at = @At("TAIL"))
    private void mineuxScreens$extracted(DeltaTracker deltaTracker, Camera camera, float deltaPartialTick, CallbackInfo ci) {
        ScreensHooks.extracted(camera);
    }
}
