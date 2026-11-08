package com.mineux.screens.fabric.mixin;

import com.mineux.screens.ScreensHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Screens take mouse input before the game, only while playing: in a world, no menu, mouse grabbed. */
@Mixin(MouseHandler.class)
public abstract class ScreensMouseMixin {

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Shadow public abstract boolean isMouseGrabbed();

    private boolean mineuxScreens$playing() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.gui.screen() == null && isMouseGrabbed();
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void mineuxScreens$button(long handle, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (mineuxScreens$playing() && ScreensHooks.mouseButton(button.button(), action)) ci.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void mineuxScreens$scroll(long handle, double horizontal, double vertical, CallbackInfo ci) {
        if (mineuxScreens$playing() && ScreensHooks.mouseScroll(vertical)) ci.cancel();
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void mineuxScreens$turn(double timeDelta, CallbackInfo ci) {
        if (ScreensHooks.mouseTurn(accumulatedDX, accumulatedDY)) ci.cancel();
    }
}
