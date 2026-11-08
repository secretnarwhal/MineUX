package com.mineux.visuals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mineux.visuals.VisualBridge;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static com.mineux.visuals.VisualOps.*;

/**
 * The server's crit stars and enchanted-hit sparkles (animate actions 4 and 5), which the hit
 * effects' crit slot stands in for. Wrapped at the emitter rather than at the head of
 * {@code handleAnimate}, so it only ever runs on the client thread, after the packet has been
 * bounced there; the visuals says whether to leave them out ({@code OP_FX_CRIT_PACKET}).
 */
@Mixin(ClientPacketListener.class)
public abstract class CritParticleMixin {

    @WrapOperation(
        method = "handleAnimate",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleEngine;createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V"
        )
    )
    private void mineux$crit(ParticleEngine engine, Entity entity, ParticleOptions particle, Operation<Void> original) {
        if (VisualBridge.on() && VisualBridge.query(OP_FX_CRIT_PACKET, entity, particle)) return;
        original.call(engine, entity, particle);
    }
}
