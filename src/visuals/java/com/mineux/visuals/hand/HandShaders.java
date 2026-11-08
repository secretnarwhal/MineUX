package com.mineux.visuals.hand;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mineux.core.gpu.Shader;
import com.mineux.core.gpu.Texture;
import com.mineux.core.gpu.Uniforms;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Hand shaders: a screen-space effect over your first-person hand and the item in it. Once the
 * hand is drawn the depth buffer holds the hand and nothing else, so it is the hand's mask: the
 * picture is copied aside and a shader draws over the hand's pixels, reading the copy.
 * <p>
 * chroma: moving rainbow bands. invert: the hand's colours inverted, with a glowing rim if asked.
 * outline: a line round the hand, the hand tinted if asked. pixelate: big pixels of a few
 * colours. prism: drifting pools of light in your two accent colours.
 * <p>
 * Drawn on the game's own render API ({@link Shader}), so OpenGL and Vulkan alike; a shader that
 * will not build stays off and says so once in the log.
 */
public final class HandShaders {

    static final Logger LOGGER = LoggerFactory.getLogger("mineux/hand");

    private static final List<String> SHADERS = Arrays.asList("chroma", "invert", "outline", "pixelate", "prism");

    /** Each shader, built on first use; {@link #FAILED} marks one that would not build or compile. */
    private static final Shader[] PROGRAMS = new Shader[SHADERS.size()];
    private static final boolean[] FAILED = new boolean[SHADERS.size()];
    private static final Texture COPY = new Texture("mineux hand copy", Texture.COPY);
    private static boolean broken;

    private HandShaders() {}

    static int index(String name) {
        return SHADERS.indexOf(name);
    }

    /** The hand drawn, its depth still the hand's alone. */
    public static void afterHand() {
        if (broken) return;
        HandConfig config = HandConfig.get();
        if (!config.shaders || !com.mineux.visuals.Visuals.on()) return;
        try {
            draw(config, Minecraft.getInstance());
        } catch (RuntimeException | LinkageError e) {
            broken = true;
            LOGGER.error("Hand shaders failed; they stay off until the game restarts", e);
        }
    }

    private static void draw(HandConfig c, Minecraft mc) {
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) return;
        GpuTexture colour = main.getColorTexture();
        GpuTextureView target = main.getColorTextureView();
        GpuTextureView depth = main.getDepthTextureView();
        if (colour == null || colour.isClosed() || target == null || depth == null) return;
        int which = Math.max(0, SHADERS.indexOf(c.shader));
        Shader shader = program(which);
        if (shader == null) return;

        // The picture as it is, to read from while drawing over it.
        GpuTextureView aside = COPY.ensure(colour.getFormat(), main.width, main.height);
        RenderSystem.getDevice().createCommandEncoder()
            .copyTextureToTexture(colour, COPY.texture(), 0, 0, 0, 0, 0, main.width, main.height);

        uniforms(c, shader.uniforms, main);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        boolean drawn = shader.into(target)
            .texture("ColorTexture", aside, nearest)
            .texture("DepthTexture", depth, nearest)
            .run();
        if (!drawn && shader.broken()) FAILED[which] = true;
    }

    private static Shader program(int which) {
        if (FAILED[which]) return null;
        if (PROGRAMS[which] == null) {
            String name = SHADERS.get(which);
            try {
                PROGRAMS[which] = Shader.of("hand_" + name, Shader.read("hand/hand.vsh"), Shader.read("hand/" + name + ".fsh"),
                    BlendFunction.TRANSLUCENT);
            } catch (java.io.IOException | RuntimeException e) {
                FAILED[which] = true;
                LOGGER.warn("Hand shader {} would not build", name, e);
                return null;
            }
        }
        return PROGRAMS[which];
    }

    /** Every shader's uniforms; a shader without one of them leaves it unread. */
    private static void uniforms(HandConfig c, Uniforms u, RenderTarget main) {
        u.set("time", (float) (System.nanoTime() / 1.0e9 % 3600.0));
        u.set("aspect", (float) main.width / Math.max(1, main.height));
        u.set("texel", 1f / Math.max(1, main.width), 1f / Math.max(1, main.height));
        u.set("pxScale", main.height / 1080f);
        u.set("chromaSpeed", (float) c.chromaSpeed);
        u.set("chromaSaturation", (float) c.chromaSaturation);
        u.set("chromaBrightness", (float) c.chromaBrightness);
        u.set("invertIntensity", (float) c.invertIntensity);
        u.set("edgeGlow", (float) c.edgeGlow);
        colour(u, "glowColor", HandConfig.rgb(c.glowColor, 0x00e5ff));
        colour(u, "outlineColor", HandConfig.rgb(c.outlineColor, 0xffffff));
        u.set("outlineThickness", (float) c.outlineThickness);
        u.set("tintEnabled", c.tint ? 1f : 0f);
        colour(u, "tintColor", HandConfig.rgb(c.tintColor, 0xff0000));
        u.set("tintAmount", (float) c.tintAmount);
        u.set("pixelSize", (float) c.pixelSize);
        colour(u, "uAccent", HandConfig.rgb(c.accent, 0xa855f7));
        colour(u, "uAccent2", HandConfig.rgb(c.accent2, 0x22d3ee));
    }

    /** A colour as 0xRRGGBB, to a vec3. */
    private static void colour(Uniforms u, String name, int rgb) {
        u.set(name, (rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f);
    }
}
