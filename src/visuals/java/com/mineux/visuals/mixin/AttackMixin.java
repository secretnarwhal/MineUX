package com.mineux.visuals.mixin;

import com.mineux.visuals.VisualBridge;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mineux.visuals.VisualOps.*;

/** Your attack as you make it: the custom crosshair's hit marker. Only read. */
@Mixin(MultiPlayerGameMode.class)
public abstract class AttackMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void mineux$attack(Player player, Entity target, CallbackInfo ci) {
        VisualBridge.dispatch(OP_ATTACK, target);
    }
}
