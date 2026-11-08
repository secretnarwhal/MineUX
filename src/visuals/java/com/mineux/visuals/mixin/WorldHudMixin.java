package com.mineux.visuals.mixin;

import com.mineux.visuals.VisualBridge;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mineux.visuals.VisualOps.*;

/** The screen overlays no option turns off, each asked about before it is drawn. */
@Mixin(Hud.class)
public abstract class WorldHudMixin {

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void mineux$scoreboard(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (VisualBridge.query(OP_WORLD_HIDE, HIDE_SCOREBOARD)) ci.cancel();
    }

    @Inject(method = "extractConfusionOverlay", at = @At("HEAD"), cancellable = true)
    private void mineux$nausea(GuiGraphicsExtractor graphics, float strength, CallbackInfo ci) {
        if (VisualBridge.query(OP_WORLD_HIDE, HIDE_NAUSEA)) ci.cancel();
    }

    @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void mineux$portal(GuiGraphicsExtractor graphics, float alpha, CallbackInfo ci) {
        if (VisualBridge.query(OP_WORLD_HIDE, HIDE_PORTAL)) ci.cancel();
    }

    @Inject(method = "extractSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void mineux$spyglass(GuiGraphicsExtractor graphics, float scale, CallbackInfo ci) {
        if (VisualBridge.query(OP_WORLD_HIDE, HIDE_SPYGLASS)) ci.cancel();
    }

    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
    private void mineux$vignette(GuiGraphicsExtractor graphics, Entity camera, CallbackInfo ci) {
        if (VisualBridge.query(OP_WORLD_HIDE, HIDE_VIGNETTE)) ci.cancel();
    }
}
