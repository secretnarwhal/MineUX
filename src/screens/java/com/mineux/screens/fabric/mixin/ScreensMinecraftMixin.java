package com.mineux.screens.fabric.mixin;

import com.mineux.screens.ScreensHooks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every frame, every tick, and the crosshair's pick. */
@Mixin(Minecraft.class)
public abstract class ScreensMinecraftMixin {

    @Inject(method = "runTick", at = @At("HEAD"))
    private void mineuxScreens$frame(boolean advanceGameTime, CallbackInfo ci) {
        ScreensHooks.frame();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void mineuxScreens$tick(CallbackInfo ci) {
        ScreensHooks.tick((Minecraft) (Object) this);
    }

    @Inject(method = "pick", at = @At("RETURN"))
    private void mineuxScreens$pick(float partialTicks, CallbackInfo ci) {
        ScreensHooks.afterPick((Minecraft) (Object) this, partialTicks);
    }
}
