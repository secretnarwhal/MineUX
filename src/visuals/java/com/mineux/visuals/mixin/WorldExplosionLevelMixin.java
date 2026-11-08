package com.mineux.visuals.mixin;

import com.mineux.visuals.VisualBridge;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mineux.visuals.VisualOps.*;

/** The rest of an explosion's particles: the blast and the blocks' bits, tracked from its centre. */
@Mixin(ClientLevel.class)
public abstract class WorldExplosionLevelMixin {

    @Inject(method = "trackExplosionEffects", at = @At("HEAD"), cancellable = true)
    private void mineux$explosion(Vec3 center, float radius, int blockCount, WeightedList<?> particles, CallbackInfo ci) {
        if (VisualBridge.query(OP_WORLD_HIDE_AT, center)) ci.cancel();
    }
}
