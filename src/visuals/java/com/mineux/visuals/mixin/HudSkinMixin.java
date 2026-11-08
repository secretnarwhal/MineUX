package com.mineux.visuals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mineux.visuals.VisualBridge;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mineux.visuals.VisualOps.*;

/**
 * The game's HUD pieces the client can draw in its own look: the crosshair, the hotbar, the
 * hearts/armour/food/air, the experience bar and the scoreboard sidebar. Each is offered to the
 * visuals first, in its place in the frame, and left out when the visuals drew its own.
 */
@Mixin(Hud.class)
public abstract class HudSkinMixin {

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void mineux$crosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (VisualBridge.on() && VisualBridge.query(OP_HUD_CROSSHAIR, graphics)) ci.cancel();
    }

    @Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
    private void mineux$hotbar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (VisualBridge.on() && VisualBridge.query(OP_HUD_HOTBAR, graphics)) ci.cancel();
    }

    @Inject(method = "extractPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void mineux$status(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (VisualBridge.on() && VisualBridge.query(OP_HUD_STATUS, graphics)) ci.cancel();
    }

    @WrapOperation(
        method = "extractHotbarAndDecorations",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"
        )
    )
    private void mineux$experienceBack(ContextualBar bar, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, Operation<Void> original) {
        if (VisualBridge.on() && VisualBridge.query(OP_HUD_EXPERIENCE, graphics)) return;
        original.call(bar, graphics, deltaTracker);
    }

    @WrapOperation(
        method = "extractHotbarAndDecorations",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V"
        )
    )
    private void mineux$experienceLevel(GuiGraphicsExtractor graphics, Font font, int level, Operation<Void> original) {
        if (VisualBridge.on() && VisualBridge.query(OP_HUD_EXPERIENCE, graphics)) return;
        original.call(graphics, font, level);
    }

    @WrapOperation(
        method = "extractHotbarAndDecorations",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"
        )
    )
    private void mineux$experienceFill(ContextualBar bar, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, Operation<Void> original) {
        if (VisualBridge.on() && VisualBridge.query(OP_HUD_EXPERIENCE, graphics)) return;
        original.call(bar, graphics, deltaTracker);
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void mineux$scoreboard(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
        if (VisualBridge.on() && VisualBridge.query(OP_HUD_SCOREBOARD, graphics, objective)) ci.cancel();
    }
}
