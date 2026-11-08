package com.mineux.visuals.mixin;

import com.mineux.visuals.VisualBridge;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mineux.visuals.VisualOps.*;

/**
 * The crit stars and sparkles the client draws itself the moment you hit a player, ahead of
 * the server's ({@code Player#attack} runs on the client too, and a remote player takes the
 * simulated hit). The visuals say whether to leave them out ({@code OP_FX_CRIT_OWN}).
 */
@Mixin(LocalPlayer.class)
public abstract class OwnCritMixin {

    @Inject(method = "crit", at = @At("HEAD"), cancellable = true)
    private void mineux$crit(Entity entity, CallbackInfo ci) {
        if (VisualBridge.on() && VisualBridge.query(OP_FX_CRIT_OWN, entity, ParticleTypes.CRIT)) ci.cancel();
    }

    @Inject(method = "magicCrit", at = @At("HEAD"), cancellable = true)
    private void mineux$magicCrit(Entity entity, CallbackInfo ci) {
        if (VisualBridge.on() && VisualBridge.query(OP_FX_CRIT_OWN, entity, ParticleTypes.ENCHANTED_HIT)) ci.cancel();
    }
}
