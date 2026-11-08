package com.mineux.visuals.mixin;

import com.mineux.visuals.VisualBridge;
import static com.mineux.visuals.VisualOps.*;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player's render state once the game has filled it in, every field set: the visuals' own-player
 * visuals turn the head to the server's look, read the walk and swap the body out from here.
 */
@Mixin(AvatarRenderer.class)
public abstract class SelfAvatarRendererMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("TAIL")
    )
    private void mineux$selfAvatarExtracted(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        VisualBridge.dispatch(OP_SELF_AVATAR_EXTRACTED, state, partialTicks);
    }
}
