package com.mineux.visuals.hand;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.io.InputStream;

/**
 * Enchant glint colour: the game's glint textures (items, and armour) swapped for copies in your
 * colour, their brightness kept, so every enchanted thing shimmers in it -- in your hand, on the
 * ground, in the inventory and on whoever wears it. Your screen only. Switched off, or after a
 * resource reload threw the copies away, the game's own are loaded back.
 */
public final class Glint {

    private static final Identifier ITEM = Identifier.withDefaultNamespace("textures/misc/enchanted_glint_item.png");
    private static final Identifier ARMOR = Identifier.withDefaultNamespace("textures/misc/enchanted_glint_armor.png");

    private static DynamicTexture item;
    private static DynamicTexture armor;
    private static int applied = 0 - 1;
    /** Ticks to wait before trying again after the textures could not be read. */
    private static int retry;

    private Glint() {}

    public static void onClientTick(Minecraft mc) {
        HandConfig config = HandConfig.get();
        TextureManager textures = mc.getTextureManager();
        if (textures == null) return;
        if (!config.glint) {
            if (applied >= 0) restore(textures);
            return;
        }
        int rgb = HandConfig.rgb(config.glintColor, 0x8040ff);
        boolean ours = textures.getTexture(ITEM) == item && textures.getTexture(ARMOR) == armor;
        if (applied == rgb && ours) return;
        if (retry > 0) {
            retry--;
            return;
        }
        item = tinted(mc, textures, ITEM, rgb);
        armor = tinted(mc, textures, ARMOR, rgb);
        applied = item != null || armor != null ? rgb : 0 - 1;
        if (applied < 0) retry = 40;
    }

    private static void restore(TextureManager textures) {
        // Released, the game loads its own from the resource pack the next time one is drawn.
        if (textures.getTexture(ITEM) == item) textures.release(ITEM);
        if (textures.getTexture(ARMOR) == armor) textures.release(ARMOR);
        item = null;
        armor = null;
        applied = 0 - 1;
    }

    /** The texture's copy in the colour: each pixel's brightness, times the colour, its alpha kept. */
    private static DynamicTexture tinted(Minecraft mc, TextureManager textures, Identifier id, int rgb) {
        NativeImage img;
        try (InputStream in = mc.getResourceManager().getResource(id).orElseThrow().open()) {
            img = NativeImage.read(in);
        } catch (Exception e) {
            return null;
        }
        float cr = (rgb >> 16 & 0xFF) / 255f;
        float cg = (rgb >> 8 & 0xFF) / 255f;
        float cb = (rgb & 0xFF) / 255f;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getPixel(x, y);
                float lum = Math.max(p >>> 16 & 0xFF, Math.max(p >>> 8 & 0xFF, p & 0xFF)) / 255f * 1.15f;
                int r = Math.min(255, Math.round(lum * cr * 255f));
                int g = Math.min(255, Math.round(lum * cg * 255f));
                int b = Math.min(255, Math.round(lum * cb * 255f));
                img.setPixel(x, y, (p >>> 24 & 0xFF) << 24 | r << 16 | g << 8 | b);
            }
        }
        DynamicTexture texture = new DynamicTexture(() -> "mineux_glint", img);
        textures.register(id, texture);
        return texture;
    }
}
