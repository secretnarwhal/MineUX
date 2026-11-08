package com.mineux.visuals.mixin;

import com.mineux.visuals.VisualBridge;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mineux.visuals.VisualOps.*;

/** A popped totem's cloud of particles, which comes as an emitter tracking whoever popped. */
@Mixin(ParticleEngine.class)
public abstract class WorldParticleMixin {

    @Inject(
        method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void mineux$totemParticles(Entity entity, ParticleOptions particle, int lifeTime, CallbackInfo ci) {
        if (particle == ParticleTypes.TOTEM_OF_UNDYING && VisualBridge.query(OP_WORLD_HIDE, HIDE_TOTEM_PARTICLES)) ci.cancel();
    }
}
