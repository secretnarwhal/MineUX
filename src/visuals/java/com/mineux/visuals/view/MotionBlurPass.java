package com.mineux.visuals.view;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mineux.core.gpu.Shader;
import com.mineux.core.gpu.Texture;

/**
 * {@link MotionBlur}'s drawing: the scene's colour copied aside, then one full-screen triangle
 * drawn back over it that samples the copy along each pixel's streak. On the game's own render
 * API ({@link Shader}), so it runs on OpenGL and Vulkan alike.
 * <p>
 * The taps a pixel takes scale with how far it moves -- about one every 2.5 px of streak, never
 * more than the quality setting -- so most of a slow turn costs three. A per-pixel jitter on the
 * tap positions turns the steps between taps into fine noise.
 */
final class MotionBlurPass {

    static final String VERTEX = """
        #version 330 core
        out vec2 uv;
        void main() {
            vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2)) * 2.0 - 1.0;
            gl_Position = vec4(p, 0.0, 1.0);
            uv = p * 0.5 + 0.5;
        }
        """;

    static final String FRAGMENT = """
        #version 330 core
        uniform sampler2D uScene;
        uniform mat3 uDelta;
        uniform vec2 uFocal;
        uniform float uScale;
        uniform float uMaxLen;
        uniform vec2 uSize;
        uniform int uSamples;
        in vec2 uv;
        out vec4 fragColor;
        float ign(vec2 p) {
            return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
        }
        void main() {
            vec2 ndc = uv * 2.0 - 1.0;
            vec3 p = uDelta * vec3(ndc / uFocal, -1.0);
            if (p.z > -1e-3) { fragColor = texture(uScene, uv); return; }
            vec2 prev = p.xy * uFocal / -p.z;
            vec2 vel = (ndc - prev) * (0.5 * uScale);
            float len = length(vel);
            if (len > uMaxLen) { vel *= uMaxLen / len; len = uMaxLen; }
            float px = len * max(uSize.x, uSize.y);
            if (px < 1.0) { fragColor = texture(uScene, uv); return; }
            int n = int(clamp(ceil(px / 2.5), 3.0, float(uSamples)));
            float inv = 1.0 / float(n);
            float j = ign(gl_FragCoord.xy) - 0.5;
            vec3 acc = vec3(0.0);
            float wsum = 0.0;
            for (int i = 0; i < 32; i++) {
                if (i >= n) break;
                float t = (float(i) + 0.5 + j) * inv - 0.5;
                float w = 1.0 - abs(t) * 0.6;
                acc += texture(uScene, uv + vel * t).rgb * w;
                wsum += w;
            }
            fragColor = vec4(acc / wsum, texture(uScene, uv).a);
        }
        """;

    private static final Texture COPY = new Texture("mineux motion blur copy", Texture.COPY);
    private static Shader shader;
    private static boolean failed;

    private MotionBlurPass() {}

    static void draw(RenderTarget main, float[] delta, float focalX, float focalY, float scale, float maxLength, int samples) {
        if (failed) return;
        GpuTexture colour = main.getColorTexture();
        GpuTextureView target = main.getColorTextureView();
        if (colour == null || colour.isClosed() || target == null) return;
        if (shader == null) {
            try {
                shader = Shader.of("motion_blur", VERTEX, FRAGMENT, null);
            } catch (RuntimeException e) {
                failed = true;
                throw e;
            }
        }
        int width = main.width;
        int height = main.height;
        GpuTextureView copy = COPY.ensure(colour.getFormat(), width, height);
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(colour, COPY.texture(), 0, 0, 0, 0, 0, width, height);
        shader.uniforms.setMat3("uDelta", delta);
        shader.uniforms.set("uFocal", focalX, focalY);
        shader.uniforms.set("uScale", scale);
        shader.uniforms.set("uMaxLen", maxLength);
        shader.uniforms.set("uSize", width, height);
        shader.uniforms.setInt("uSamples", samples);
        boolean drawn = shader.into(target)
            .texture("uScene", copy, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            .run();
        if (!drawn && shader.broken()) failed = true;
    }
}
