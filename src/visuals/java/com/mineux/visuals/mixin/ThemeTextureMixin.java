package com.mineux.visuals.mixin;

import com.mineux.visuals.VisualBridge;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.mineux.visuals.VisualOps.*;

/**
 * Theme textures: every whole texture (the ones not in an atlas: the title screen's art, the
 * gui backgrounds) offered to the visuals to recolour in place as it loads.
 */
@Mixin(TextureContents.class)
public abstract class ThemeTextureMixin {

    @Inject(method = "load", at = @At("RETURN"))
    private static void mineux$themeTexture(ResourceManager manager, Identifier location, CallbackInfoReturnable<TextureContents> cir) {
        TextureContents contents = cir.getReturnValue();
        if (contents != null && VisualBridge.on()) VisualBridge.query(OP_THEME_TEXTURE, location, contents.image());
    }
}
