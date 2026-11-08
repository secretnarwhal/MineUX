package com.mineux.visuals.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mineux.visuals.VisualBridge;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mineux.visuals.VisualOps.*;

/**
 * A dropped item: what it is and how it moves at the end of its extract, and its drawing, which
 * the visuals may do itself -- lying flat, tumbling -- in place of the hover and the spin. The
 * name tag and the rest of what every entity draws still follow, from the super call.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class WorldItemMixin extends EntityRenderer<ItemEntity, ItemEntityRenderState> {

    protected WorldItemMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
        at = @At("TAIL")
    )
    private void mineux$extract(ItemEntity item, ItemEntityRenderState state, float partialTicks, CallbackInfo ci) {
        VisualBridge.query(OP_WORLD_ITEM, item, state);
    }

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void mineux$submit(ItemEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                   CameraRenderState camera, CallbackInfo ci) {
        if (!VisualBridge.query(OP_WORLD_ITEM_DRAW, new Object[] {state, poseStack, collector})) return;
        super.submit(state, poseStack, collector, camera);
        ci.cancel();
    }
}
