package com.mineux.visuals.mixin;

import com.mojang.authlib.GameProfile;
import com.mineux.visuals.VisualBridge;
import static com.mineux.visuals.VisualOps.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Your skin as this client sees it. Everything that draws you -- your model, the inventory, the tab
 * list -- asks your tab list entry for it, so the answer is swapped there, and only for your own
 * entry. Nothing about the skin is sent anywhere.
 */
@Mixin(PlayerInfo.class)
public abstract class SelfSkinMixin {

    @Shadow
    @Final
    private GameProfile profile;

    @Inject(method = "getSkin()Lnet/minecraft/world/entity/player/PlayerSkin;", at = @At("RETURN"), cancellable = true)
    private void mineux$selfSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        LocalPlayer me = Minecraft.getInstance().player;
        if (me == null || profile == null || !profile.id().equals(me.getUUID())) return;
        PlayerSkin skin = cir.getReturnValue();
        PlayerSkin swapped = VisualBridge.transform(OP_SELF_SKIN, skin, PlayerSkin.class);
        if (swapped != skin) cir.setReturnValue(swapped);
    }
}
