package com.mineux.visuals.trail;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * One dynamically-loaded sprite texture: read/decode/validate a custom image from disk when
 * the config names one, fall back to a procedurally-drawn image otherwise, and only redo any
 * of that when the configured path actually changes. Shared by {@link TrailRenderer} and
 * {@link FakeCursorRenderer}, which otherwise had this sequence copy-pasted between them.
 */
final class SpriteSlot {
    private static final int MAX_SPRITE_PIXELS = 256;

    private final Identifier textureId;
    private final String label;

    private String cachedPath; // null = never loaded yet
    private int width;
    private int height;

    SpriteSlot(Identifier textureId, String label, int fallbackSize) {
        this.textureId = textureId;
        this.label = label;
        this.width = fallbackSize;
        this.height = fallbackSize;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    void ensureLoaded(String spritePath, Supplier<NativeImage> fallback) {
        String normalized = spritePath == null ? "" : spritePath;
        if (normalized.equals(cachedPath)) return;

        NativeImage image = decode(normalized);
        if (image == null) image = fallback.get();

        if (cachedPath != null) {
            Minecraft.getInstance().getTextureManager().release(textureId);
        }
        DynamicTexture texture = new DynamicTexture(() -> label, image);
        texture.upload();
        Minecraft.getInstance().getTextureManager().register(textureId, texture);
        width = image.getWidth();
        height = image.getHeight();
        cachedPath = normalized;
    }

    private static NativeImage decode(String spritePath) {
        if (spritePath.isEmpty()) return null;

        try {
            byte[] bytes = Files.readAllBytes(Path.of(spritePath));
            NativeImage loaded = NativeImage.read(bytes);
            if (loaded.getWidth() > 0 && loaded.getHeight() > 0
                    && loaded.getWidth() <= MAX_SPRITE_PIXELS && loaded.getHeight() <= MAX_SPRITE_PIXELS) {
                return loaded;
            }
            loaded.close();
        } catch (IOException ignored) {
            // Falls back to the caller's procedural sprite.
        }
        return null;
    }
}
