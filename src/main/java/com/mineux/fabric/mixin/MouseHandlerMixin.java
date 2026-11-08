package com.mineux.fabric.mixin;

import com.mineux.core.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The mouse, to the overlay while it is open: buttons, the wheel, moves, and the grab kept off. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void mineux$button(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (handle != Minecraft.getInstance().getWindow().handle()) return;
        if (Hooks.mouseButton(info, action)) ci.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void mineux$scroll(long handle, double x, double y, CallbackInfo ci) {
        if (handle != Minecraft.getInstance().getWindow().handle()) return;
        if (Hooks.mouseScroll(x, y)) ci.cancel();
    }

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void mineux$holdsMouse(CallbackInfo ci) {
        if (Hooks.holdsMouse()) ci.cancel();
    }

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void mineux$frame(CallbackInfo ci) {
        Hooks.mouseFrame(Minecraft.getInstance());
    }
}
