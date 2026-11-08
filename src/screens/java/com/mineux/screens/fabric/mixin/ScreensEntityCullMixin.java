package com.mineux.screens.fabric.mixin;

import com.mineux.screens.ScreensHooks;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** In the screens freecam no other player is drawn: {@link ScreensHooks#hidesEntity}. */
@Mixin(EntityRenderDispatcher.class)
public abstract class ScreensEntityCullMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void mineuxScreens$hideOthers(E entity, Frustum culler, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (ScreensHooks.hidesEntity(entity)) cir.setReturnValue(false);
    }
}
