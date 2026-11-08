package com.mineux.fabric.mixin;

import com.mineux.core.Hooks;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keys and typed characters, to the overlay and the desk key before the game sees them. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true)
    private void mineux$keyPress(long handle, int action, KeyEvent event, CallbackInfo ci) {
        if (Hooks.keyPress(event, action)) ci.cancel();
    }

    @Inject(method = "charTyped(JLnet/minecraft/client/input/CharacterEvent;)V", at = @At("HEAD"), cancellable = true)
    private void mineux$charTyped(long handle, CharacterEvent event, CallbackInfo ci) {
        if (Hooks.charTyped(event)) ci.cancel();
    }
}
