package com.mineux.fabric.mixin;

import com.mineux.core.Hooks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Esc menu (one that shows its buttons, which F3+Esc's does not) is handed to the Esc desk. */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {

    @Unique
    private boolean mineux$handoff;

    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mineux$takeOver(CallbackInfo ci) {
        if (this.minecraft == null || !Hooks.takesMenus() || !((PauseScreen) (Object) this).showsPauseMenu()) return;
        mineux$handoff = true;
        this.minecraft.execute(() -> {
            mineux$handoff = false;
            Hooks.pauseScreen((PauseScreen) (Object) this);
        });
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void mineux$hideButtons(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (mineux$handoff) ci.cancel();
    }
}
