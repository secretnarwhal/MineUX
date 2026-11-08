package com.mineux.visuals.mixin;

import com.mineux.visuals.VisualBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mineux.visuals.VisualOps.*;

/**
 * The events the visuals draw on, handed over as the packets they arrived in: an entity event
 * (a death for the crosshair's kill burst and the kill effects, a totem pop for totem pop) and
 * a damage event (your hit, for the hit effects). Only read; the game handles them as ever.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void mineux$onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        // Vanilla runs this once on the netty thread, which only bounces it to the client
        // thread, and then again there. Only the second is the event.
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread() || minecraft.level == null) return;
        VisualBridge.dispatch(OP_ENTITY_EVENT, packet);
    }

    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void mineux$onDamageEvent(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        VisualBridge.dispatch(OP_DAMAGE_EVENT, packet);
    }
}
