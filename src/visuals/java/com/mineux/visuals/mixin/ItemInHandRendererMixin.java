package com.mineux.visuals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mineux.visuals.VisualBridge;
import static com.mineux.visuals.VisualOps.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The first-person hand and the item in it, for the visuals' hand visuals: where each arm sits,
 * how it swings, how the item is held. Drawn only -- the swing progress is read and changed
 * here for the picture, never on the player, so the swing, the attack and its packet are the
 * game's own. Every hook leaves vanilla's drawing as it was when the visuals says nothing.
 * See {@code VisualOps#OP_HAND_ATTACK}.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @WrapOperation(
        method = "submitHandsWithItems",
        at = @At(value = "INVOKE", target = "getAttackAnim(F)F")
    )
    private float mineux$attack(LocalPlayer player, float frameInterp, Operation<Float> original) {
        Float drawn = original.call(player, frameInterp);
        return VisualBridge.transform(OP_HAND_ATTACK, drawn, Float.class);
    }

    /** Each arm on a pose of its own, so what the visuals moves it by goes with it. */
    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void mineux$armHead(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand,
                                    float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
                                    SubmitNodeCollector collector, int lightCoords, CallbackInfo ci) {
        poseStack.pushPose();
        if (VisualBridge.query(OP_HAND_BEGIN, new Object[] {player, hand, itemStack, poseStack, attack, inverseArmHeight})) {
            poseStack.popPose();
            ci.cancel();
        }
    }

    @Inject(method = "submitArmWithItem", at = @At("RETURN"))
    private void mineux$armReturn(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand,
                                      float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
                                      SubmitNodeCollector collector, int lightCoords, CallbackInfo ci) {
        poseStack.popPose();
    }

    /** The arm's place before a plain (whack) swing, the one an old-style swing replaces. */
    @WrapOperation(
        method = "submitArmWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V",
            ordinal = 4
        )
    )
    private void mineux$equip(ItemInHandRenderer self, PoseStack poseStack, HumanoidArm arm, float inverseArmHeight,
                                  Operation<Void> original) {
        if (!VisualBridge.query(OP_HAND_EQUIP, new Object[] {poseStack, arm, inverseArmHeight})) {
            original.call(self, poseStack, arm, inverseArmHeight);
        }
    }

    @WrapOperation(
        method = "submitArmWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;swingArm(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V",
            ordinal = 2
        )
    )
    private void mineux$swing(ItemInHandRenderer self, float attack, PoseStack poseStack, int invert, HumanoidArm arm,
                                  Operation<Void> original) {
        if (!VisualBridge.query(OP_HAND_SWING, new Object[] {poseStack, arm, attack})) {
            original.call(self, attack, poseStack, invert, arm);
        }
    }

    @WrapOperation(
        method = "submitArmWithItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"
        )
    )
    private void mineux$item(ItemInHandRenderer self, LivingEntity mob, ItemStack stack, ItemDisplayContext type,
                                 PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, Operation<Void> original,
                                 AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand,
                                 float attack, ItemStack itemStack, float inverseArmHeight) {
        Object[] args = {self, mob, stack, type, poseStack, collector, lightCoords, hand, attack, inverseArmHeight};
        if (!VisualBridge.query(OP_HAND_ITEM, args)) {
            original.call(self, mob, stack, type, poseStack, collector, lightCoords);
        }
    }
}
