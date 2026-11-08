package com.mineux.visuals.mixin;

import com.mineux.visuals.VisualBridge;
import static com.mineux.visuals.VisualOps.*;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The end of the frame's entity extraction, where the game has left the camera's own entity out in
 * first person: the body view adds you there. The partial tick is the one the extraction itself
 * read off the same tracker.
 */
@Mixin(LevelExtractor.class)
public abstract class SelfLevelExtractorMixin {

    @Inject(
        method = "extractVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;"
            + "Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V",
        at = @At("TAIL")
    )
    private void mineux$selfEntitiesExtracted(Camera camera, Frustum frustum, DeltaTracker deltaTracker, LevelRenderState output,
                                                  CallbackInfo ci) {
        VisualBridge.dispatch(OP_SELF_ENTITIES_EXTRACTED, output, deltaTracker.getGameTimeDeltaPartialTick(false));
    }
}
