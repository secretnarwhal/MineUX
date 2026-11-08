package com.mineux.visuals.mixin;

import com.mineux.visuals.VisualBridge;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.mineux.visuals.VisualOps.*;

/**
 * The frame's fog, the moment it is worked out and before anything is drawn in it: its colour
 * and its distances are plain fields, written over in place by the visuals.
 */
@Mixin(FogRenderer.class)
public abstract class WorldFogMixin {

    @Inject(
        method = "setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lnet/minecraft/client/renderer/fog/FogData;",
        at = @At("RETURN")
    )
    private void mineux$fog(Camera camera, int renderDistance, DeltaTracker deltaTracker, float partialTick,
                                ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        FogData fog = cir.getReturnValue();
        if (fog != null) VisualBridge.dispatch(OP_WORLD_FOG, fog);
    }
}
