package com.mineux.visuals.mixin;

import com.mineux.visuals.VisualBridge;
import static com.mineux.visuals.VisualOps.*;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A humanoid model just posed for a state, right before it is drawn: the walk styles repose it and
 * the body view takes the head off your own. The player model calls this through its own pose, so
 * the armour models, which share the state, follow.
 */
@Mixin(HumanoidModel.class)
public abstract class SelfHumanoidModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void mineux$selfPose(HumanoidRenderState state, CallbackInfo ci) {
        VisualBridge.query(OP_SELF_HUMANOID_POSE, this, state);
    }
}
