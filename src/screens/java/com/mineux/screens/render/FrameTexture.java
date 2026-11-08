package com.mineux.screens.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * The latest frame of a screen, a GPU texture made and written only through the game's render
 * API ({@code GpuDevice.createTexture}, {@code CommandEncoder.writeToTexture}), so it is the same
 * on OpenGL and Vulkan. Frames arrive as RGBA8 with alpha 255 (the native side and the JPEG
 * decoder both make them so) and go up as they are. Registered with the texture manager under
 * its own id, so a render type and the menu's preview can bind it by name. Render thread only.
 */
public final class FrameTexture {

    private static int next;

    private Identifier location;
    private GpuTexture gpu;
    private int width;
    private int height;
    private float scale = 1.0f;

    /** Uploads {@code bytes} of tightly packed RGBA8 at {@code ptr}, reallocating on a size change. */
    public void upload(long ptr, int w, int h, float frameScale) {
        upload(MemoryUtil.memByteBuffer(ptr, w * h * 4), w, h, frameScale);
    }

    public void upload(ByteBuffer rgba, int w, int h, float frameScale) {
        if (location == null || w != width || h != height) {
            destroy();
            allocate(w, h);
        }
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(gpu, rgba, 0, 0, 0, 0, w, h);
        scale = frameScale;
    }

    private void allocate(int w, int h) {
        width = w;
        height = h;
        String name = "mineux-screen-" + (next++);
        GpuDevice device = RenderSystem.getDevice();
        gpu = device.createTexture(name, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
            GpuFormat.RGBA8_UNORM, w, h, 1, 1);
        location = Identifier.fromNamespaceAndPath("mineux", "screens/" + name);
        Minecraft.getInstance().getTextureManager().register(location, new Held(gpu));
    }

    public void destroy() {
        if (location == null) return;
        ScreenRenderer.release(location);
        Minecraft.getInstance().getTextureManager().release(location);
        location = null;
        gpu = null;
        width = 0;
        height = 0;
    }

    public boolean isValid() {
        return location != null;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** DPI scale of the monitor the frame came from; 1 for frames from the relay. */
    public float scale() {
        return scale;
    }

    public Identifier location() {
        return location;
    }

    /** The texture manager's handle on one frame texture: closing it frees the GPU texture. */
    private static final class Held extends AbstractTexture {
        Held(GpuTexture gpu) {
            this.texture = gpu;
            this.textureView = RenderSystem.getDevice().createTextureView(gpu);
            this.sampler = ScreenRenderer.sampler();
        }
    }
}
