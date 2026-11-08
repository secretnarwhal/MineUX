package com.mineux.visuals.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.mineux.visuals.VisualBridge;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

import static com.mineux.visuals.VisualOps.*;

/**
 * Theme textures: every atlas sprite's image, as it is made, offered to the visuals to recolour
 * in place. The three-argument constructor goes through this one.
 */
@Mixin(SpriteContents.class)
public abstract class ThemeSpriteMixin {

    @Inject(
        method = "<init>(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/resources/metadata/animation/FrameSize;Lcom/mojang/blaze3d/platform/NativeImage;Ljava/util/Optional;Ljava/util/List;Ljava/util/Optional;)V",
        at = @At("TAIL")
    )
    private void mineux$themeSprite(Identifier name, FrameSize frameSize, NativeImage image, Optional<?> animation,
                                        List<?> metadata, Optional<?> texture, CallbackInfo ci) {
        if (VisualBridge.on()) VisualBridge.query(OP_THEME_TEXTURE, name, image);
    }
}
