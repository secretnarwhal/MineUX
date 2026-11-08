package com.mineux.fabric.mixin;

import com.mineux.core.Hooks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The client tick, for the overlay's screen. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void mineux$tick(CallbackInfo ci) {
        Hooks.tick((Minecraft) (Object) this);
    }
}
