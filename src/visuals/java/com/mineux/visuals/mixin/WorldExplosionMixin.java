package com.mineux.visuals.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.mineux.visuals.VisualBridge;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static com.mineux.visuals.VisualOps.*;

/** An explosion's own puff, the one particle the packet handler adds itself. */
@Mixin(ClientPacketListener.class)
public abstract class WorldExplosionMixin {

    @WrapWithCondition(
        method = "handleExplosion",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V")
    )
    private boolean mineux$explosionPuff(ClientLevel level, ParticleOptions particle, double x, double y, double z,
                                             double dx, double dy, double dz) {
        return !VisualBridge.query(OP_WORLD_HIDE_AT, new Vec3(x, y, z));
    }
}
