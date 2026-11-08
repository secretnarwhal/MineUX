package com.mineux.screens.fabric.mixin;

import com.mineux.screens.ScreensHooks;
import com.mineux.screens.capture.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The freecam: the camera goes where it flies, and the player's own model shows. */
@Mixin(Camera.class)
public abstract class ScreensCameraMixin {

    @Shadow private boolean detached;

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Shadow protected abstract void setPosition(Vec3 position);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void mineuxScreens$freecam(float partialTicks, CallbackInfo ci) {
        Freecam freecam = ScreensHooks.freecam();
        if (freecam == null) return;
        freecam.update();
        setRotation(freecam.yRot(), freecam.xRot());
        setPosition(freecam.pos());
        detached = true;
    }
}
