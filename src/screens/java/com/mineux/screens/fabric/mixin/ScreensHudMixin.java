package com.mineux.screens.fabric.mixin;

import com.mineux.screens.ScreensHooks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The hover marks round the crosshair and the owner's LIVE line. */
@Mixin(Hud.class)
public abstract class ScreensHudMixin {

    @Shadow public abstract boolean isHidden();

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void mineuxScreens$hud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        ScreensHooks.hud(graphics, isHidden());
    }
}
