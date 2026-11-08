package com.mineux.visuals.world;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;

import java.io.IOException;

/**
 * What the sky and world shaders share: their GLSL out of the jar ({@code /mineux/shaders/}),
 * the game's samplers they read with, and the colour space they mix in. They draw through
 * {@link com.mineux.core.gpu.Shader}, on the game's own render API, so both run on OpenGL and Vulkan.
 */
final class WorldGpu {

    private WorldGpu() {}

    static String read(String file) throws IOException {
        return com.mineux.core.gpu.Shader.read("shaders/" + file);
    }

    static GpuSampler linear() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    static GpuSampler nearest() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }

    static GpuSampler repeat() {
        return RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
    }

    /** sRGB to linear, as the shaders mix in. */
    static float lin(float c) {
        return (float) Math.pow(Math.max(c, 0.0F), 2.2);
    }
}
