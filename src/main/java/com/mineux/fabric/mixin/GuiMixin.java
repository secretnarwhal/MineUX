package com.mineux.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mineux.core.Hooks;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The overlay's two hooks on the GUI: every screen change, and its draw at the end of the frame. */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void mineux$setScreen(Screen screen, CallbackInfo ci) {
        if (Hooks.setScreen(screen)) ci.cancel();
    }

    @WrapOperation(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;applyCursor(Lcom/mojang/blaze3d/platform/Window;)V"
        )
    )
    private void mineux$drawOverlay(GuiGraphicsExtractor graphics, Window window, Operation<Void> original) {
        Hooks.drawOverlay(graphics);
        original.call(graphics, window);
    }
}
