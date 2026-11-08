package com.mineux.core.gpu;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

/**
 * A texture of this client's own on the game's device, made again whenever it is asked for in
 * another size or format: a copy of the frame, a low-resolution target, a lookup table.
 */
public final class Texture {

    /** Drawn into and read from. */
    public static final int TARGET = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
    /** Copied into and read from. */
    public static final int COPY = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING;
    /** Written once from the CPU and read from. */
    public static final int TABLE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING;

    private final String label;
    private final int usage;
    private GpuTexture texture;
    private GpuTextureView view;

    public Texture(String label, int usage) {
        this.label = label;
        this.usage = usage;
    }

    /** Whether the texture as it stands is this size and format. */
    public boolean is(GpuFormat format, int width, int height) {
        return texture != null && !texture.isClosed() && texture.getFormat() == format
            && texture.getWidth(0) == width && texture.getHeight(0) == height;
    }

    /** The texture in this size and format, made (again) if it is not; its old contents are gone then. */
    public GpuTextureView ensure(GpuFormat format, int width, int height) {
        if (is(format, width, height)) return view;
        close();
        GpuDevice device = RenderSystem.getDevice();
        texture = device.createTexture(label, usage, format, width, height, 1, 1);
        view = device.createTextureView(texture);
        return view;
    }

    public GpuTexture texture() {
        return texture;
    }

    public GpuTextureView view() {
        return view;
    }

    public void close() {
        if (view != null) view.close();
        if (texture != null) texture.close();
        view = null;
        texture = null;
    }
}
