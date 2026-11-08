package com.mineux.visuals.mixin;

import com.mineux.visuals.VisualBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.mineux.visuals.VisualOps.*;

/**
 * The mouse, for freelook: its key read at the head of each frame's mouse handling, and the
 * hand's turn taken for the camera while it looks around ({@code Freelook#turn}); otherwise the
 * player turns as ever.
 */
@Mixin(MouseHandler.class)
public abstract class MouseTurnMixin {

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void mineux$frame(CallbackInfo ci) {
        VisualBridge.dispatch(OP_MOUSE_FRAME, Minecraft.getInstance());
    }

    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void mineux$turn(LocalPlayer player, double dx, double dy) {
        if (!VisualBridge.turn(dx, dy)) player.turn(dx, dy);
    }
}
