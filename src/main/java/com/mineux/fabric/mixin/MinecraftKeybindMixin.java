package com.mineux.fabric.mixin;

import com.mineux.core.Hooks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Just before vanilla drains the keybind queue: SUPER+number takes the number keys off the hotbar. */
@Mixin(Minecraft.class)
public abstract class MinecraftKeybindMixin {

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void mineux$beforeKeybinds(CallbackInfo ci) {
        Hooks.beforeKeybinds((Minecraft) (Object) this);
    }
}
