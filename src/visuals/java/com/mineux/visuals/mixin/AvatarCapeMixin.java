package com.mineux.visuals.mixin;

import com.mineux.visuals.VisualBridge;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mineux.visuals.VisualOps.*;

/**
 * The cosmetics' cape: the render state a player is drawn from, handed to the visuals once it is
 * filled in, so it can put a cape of its own on yours. TAIL, after {@code state.skin} and
 * {@code state.showCape} have been read off the entity, so what the visuals writes is what the
 * cape layer and the elytra layer draw this frame. Only the state changes, never the entity or
 * its {@code PlayerInfo}: nothing about it leaves this client.
 * <p>
 * The class's type variable is bounded by {@code Avatar} first, so that is the erased
 * parameter; the compiler's bridge for {@code LivingEntityRenderer}'s signature calls this one.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarCapeMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;"
            + "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("TAIL")
    )
    private void mineux$cape(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        VisualBridge.dispatch(OP_COSMETIC_CAPE, state);
    }
}
