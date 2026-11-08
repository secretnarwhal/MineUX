package com.mineux.visuals.mixin;

import com.mineux.visuals.VisualBridge;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mineux.visuals.VisualOps.*;

/** The tail of every client tick: the visual modules' own ticks (zoom, weather, particles, self...). */
@Mixin(Minecraft.class)
public abstract class MinecraftTickMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void mineux$visualsTick(CallbackInfo ci) {
        VisualBridge.dispatch(OP_TICK, (Minecraft) (Object) this);
    }
}
