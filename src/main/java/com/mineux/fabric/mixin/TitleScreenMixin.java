package com.mineux.fabric.mixin;

import com.mineux.core.Hooks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every title screen is handed to the home desk once its init is done, a task later so the
 * screen that put it up has finished. Until then it draws black instead of its buttons.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    @Unique
    private boolean mineux$handoff;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mineux$takeOver(CallbackInfo ci) {
        if (this.minecraft == null || !Hooks.takesMenus()) return;
        mineux$handoff = true;
        this.minecraft.execute(() -> {
            mineux$handoff = false;
            Hooks.titleScreen((TitleScreen) (Object) this);
        });
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void mineux$blackBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!mineux$handoff) return;
        g.fill(0, 0, this.width, this.height, 0xFF000000);
        ci.cancel();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void mineux$hideButtons(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (mineux$handoff) ci.cancel();
    }
}
