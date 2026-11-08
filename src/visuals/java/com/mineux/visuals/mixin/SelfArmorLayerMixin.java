package com.mineux.visuals.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mineux.visuals.VisualBridge;
import static com.mineux.visuals.VisualOps.*;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Each piece of armour (and an elytra) as the equipment layer draws it, for the armour shaders:
 * whose it is at the head, then each layer's texture, render type and tint on their way to the
 * draw. Only the piece's own draw is touched -- the glint's and the trim's after it are the game's.
 * Argument hooks rather than redirects, so another mod's hook on the same calls still runs.
 */
@Mixin(EquipmentLayerRenderer.class)
public abstract class SelfArmorLayerMixin {

    private static final String RENDER_LAYERS = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
        + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;"
        + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V";

    private static final String SUBMIT_MODEL = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel("
        + "Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;I"
        + "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V";

    @Inject(method = RENDER_LAYERS, at = @At("HEAD"))
    private void mineux$selfArmorLayer(EquipmentClientInfo.LayerType layerType, ResourceKey<EquipmentAsset> asset, Model<?> model,
                                           Object state, ItemStack stack, PoseStack poseStack, SubmitNodeCollector collector, int light,
                                           Identifier playerTexture, int outline, int order, CallbackInfo ci) {
        VisualBridge.dispatch(OP_SELF_ARMOR_LAYER, state);
    }

    @ModifyArg(
        method = RENDER_LAYERS,
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)"
                + "Lnet/minecraft/client/renderer/rendertype/RenderType;"),
        index = 0
    )
    private Identifier mineux$selfArmorTexture(Identifier texture) {
        VisualBridge.dispatch(OP_SELF_ARMOR_TEXTURE, texture);
        return texture;
    }

    @ModifyArg(method = RENDER_LAYERS, at = @At(value = "INVOKE", target = SUBMIT_MODEL, ordinal = 0), index = 3)
    private RenderType mineux$selfArmorType(RenderType type) {
        return VisualBridge.transform(OP_SELF_ARMOR_TYPE, type, RenderType.class);
    }

    @ModifyArg(method = RENDER_LAYERS, at = @At(value = "INVOKE", target = SUBMIT_MODEL, ordinal = 0), index = 6)
    private int mineux$selfArmorTint(int tint) {
        return VisualBridge.transform(OP_SELF_ARMOR_TINT, Integer.valueOf(tint), Integer.class).intValue();
    }
}
