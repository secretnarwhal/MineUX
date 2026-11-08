package com.mineux.visuals.world;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mineux.core.gpu.Shader;
import com.mineux.core.gpu.Texture;
import com.mineux.core.gpu.Uniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;
import java.util.List;

/**
 * World shaders: a look laid over the finished world each frame -- sunlight and its colour,
 * contact shadows, god rays, low fog that drifts and glows toward the sun, bloom, exposure,
 * contrast, saturation, warmth and a vignette -- from a preset ({@link WorldLook}) and your own
 * strengths on top of it.
 * <p>
 * It runs once {@code LevelRenderer#render} has drawn the world and before the hand: the
 * frame's colour is copied, the marching parts (shadows, rays, fog, bloom) are worked out at a
 * fraction of the screen's size by {@code world_lo.fsh}, and {@code world.fsh} puts them
 * together over the copy, reading the world's depth for where each pixel is. The view is the
 * camera's projection without the walking bob, so shadows can sway a hair while you walk.
 * <p>
 * A shader that will not build is logged once and the pass is off until the game restarts. Both
 * passes draw through the game's own render API ({@link Shader}), so it runs on OpenGL and Vulkan.
 */
final class WorldShader {

    private static final List<String> QUALITIES = Arrays.asList("low", "medium", "high", "ultra");
    private static final long START = System.nanoTime();

    /** The sun's angle the sky was last extracted with, radians. */
    private static float sunAngle = 0.6F;

    private static boolean broken;
    private static Shader program;
    private static Shader lowProgram;
    private static final Texture LOW_A = new Texture("mineux world low a", Texture.TARGET);
    private static final Texture LOW_B = new Texture("mineux world low b", Texture.TARGET);
    private static final Texture COPY = new Texture("mineux world copy", Texture.COPY);
    private static final Texture BLANK = new Texture("mineux world blank", Texture.TABLE);
    private static int lowW;
    private static int lowH;

    private static final Matrix4f VIEW_PROJ = new Matrix4f();
    private static final Matrix4f INVERSE = new Matrix4f();
    private static final Vector4f SUN = new Vector4f();
    private static final float[] MAT4 = new float[16];

    private WorldShader() {}

    static int qualityIndex(String name) {
        return QUALITIES.indexOf(name);
    }

    static void sunAngle(float angle) {
        sunAngle = angle;
    }

    static void apply() {
        if (broken || !World.allowed() || !ShaderConfig.get().world) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) return;
        GpuTextureView target = main.getColorTextureView();
        GpuTextureView depth = main.getDepthTextureView();
        if (target == null || depth == null || !SkyShader.viewProjection(VIEW_PROJ)) return;
        VIEW_PROJ.invert(INVERSE);
        if (!INVERSE.isFinite() || !build()) return;
        GpuTextureView scene = copy(main);
        if (scene == null) return;
        ShaderConfig config = ShaderConfig.get();
        int preset = Math.max(0, WorldLook.index(config.worldPreset));
        WorldLight light = WorldLight.of(config, preset, sunAngle, VIEW_PROJ);

        int quality = Math.max(0, qualityIndex(config.quality));
        int div = quality == 3 ? 2 : quality == 2 ? 3 : 4;
        lowW = Math.max(1, main.width / div);
        lowH = Math.max(1, main.height / div);
        GpuTextureView a = LOW_A.ensure(GpuFormat.RGBA16_FLOAT, lowW, lowH);
        GpuTextureView b = LOW_B.ensure(GpuFormat.RGBA16_FLOAT, lowW, lowH);
        common(lowProgram.uniforms, main, light);
        lowUniforms(lowProgram.uniforms, config, preset, quality, light);
        boolean low = lowProgram.into(a, b)
            .texture("uScene", scene, WorldGpu.linear())
            .texture("uDepth", depth, WorldGpu.nearest())
            .texture("uNoise3", Noise3.view(), WorldGpu.linear())
            .run();
        if (!low) {
            broken = lowProgram.broken();
            return;
        }

        common(program.uniforms, main, light);
        composite(program.uniforms, config, preset, light);
        boolean drawn = program.into(target)
            .texture("uScene", scene, WorldGpu.linear())
            .texture("uDepth", depth, WorldGpu.nearest())
            .texture("uLoA", a, WorldGpu.nearest())
            .texture("uLoB", b, WorldGpu.linear())
            .texture("uHeight", blank(), WorldGpu.linear())
            .run();
        if (!drawn) broken = program.broken();
    }

    private static boolean build() {
        if (program != null) return true;
        try {
            String vertex = WorldGpu.read("world/world.vsh");
            String common = WorldGpu.read("world/common.glsl");
            program = Shader.of("world", vertex, "#version 330 core\n" + common + WorldGpu.read("world/world.fsh"), null);
            lowProgram = Shader.of("world_lo", vertex, "#version 330 core\n" + common + WorldGpu.read("world/world_lo.fsh"), null);
            return true;
        } catch (java.io.IOException | RuntimeException e) {
            broken = true;
            program = null;
            org.slf4j.LoggerFactory.getLogger("mineux/world").warn("the world shader would not build; it is off", e);
            return false;
        }
    }

    /** The frame's colour, copied: the composite reads it while it writes over the real one. */
    private static GpuTextureView copy(RenderTarget main) {
        GpuTexture colour = main.getColorTexture();
        if (colour == null || colour.isClosed()) return null;
        GpuTextureView view = COPY.ensure(colour.getFormat(), main.width, main.height);
        RenderSystem.getDevice().createCommandEncoder()
            .copyTextureToTexture(colour, COPY.texture(), 0, 0, 0, 0, 0, main.width, main.height);
        return view;
    }

    /** One black texel, for the heightmap the composite has no rain to read from. */
    private static GpuTextureView blank() {
        if (BLANK.is(GpuFormat.RGBA8_UNORM, 1, 1)) return BLANK.view();
        GpuTextureView view = BLANK.ensure(GpuFormat.RGBA8_UNORM, 1, 1);
        java.nio.ByteBuffer zero = MemoryUtil.memCalloc(4);
        try {
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(BLANK.texture(), zero, 0, 0, 0, 0, 1, 1);
        } finally {
            MemoryUtil.memFree(zero);
        }
        return view;
    }

    // ---- uniforms -----------------------------------------------------------------------------

    private static void common(Uniforms u, RenderTarget main, WorldLight light) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        INVERSE.get(MAT4);
        u.setMat4("uInvViewProj", MAT4);
        VIEW_PROJ.get(MAT4);
        u.setMat4("uViewProj", MAT4);
        u.set("uCamPos", (float) (camera.x % 2048.0), (float) (camera.y % 2048.0), (float) (camera.z % 2048.0));
        u.set("uLightDir", light.x, light.y, light.z);
        u.set("uLightCol", WorldGpu.lin(light.colour[0]), WorldGpu.lin(light.colour[1]), WorldGpu.lin(light.colour[2]));
        u.set("uDay", light.day);
        u.set("uTexel", 1.0F / main.width, 1.0F / main.height);
        u.set("uTime", (float) ((System.nanoTime() - START) / 1.0E9 % 3600.0));
        u.set("uSunScreen", light.sx, light.sy, light.onScreen, light.strength * (light.sunUp ? 1.0F : 0.35F));
    }

    private static void lowUniforms(Uniforms u, ShaderConfig config, int preset, int quality, WorldLight light) {
        u.set("uShadowK", WorldLook.get(preset, WorldLook.SHADOWS) * (float) config.shadows * light.strength);
        u.set("uRayK", WorldLook.get(preset, WorldLook.RAYS) * (float) config.godRays);
        u.set("uFogK", WorldLook.get(preset, WorldLook.FOG) * (float) config.fog);
        u.set("uFogH", (float) config.fogHeight);
        u.set("uFogStart", (float) config.clearRadius);
        u.set("uDrift", (float) config.fogDrift);
        u.set("uBloomK", WorldLook.get(preset, WorldLook.BLOOM) * (float) config.bloom);
        // Steps a march takes for fog, shadows and rays: low, medium, high, ultra.
        u.setInt("uFogSteps", quality == 0 ? 6 : quality == 1 ? 8 : quality == 3 ? 14 : 10);
        u.setInt("uShadowSteps", quality == 0 ? 7 : quality == 1 ? 10 : quality == 3 ? 18 : 12);
        u.setInt("uRaySteps", quality == 0 ? 10 : quality == 1 ? 12 : quality == 3 ? 22 : 16);
        u.set("uLoTexel", 1.0F / lowW, 1.0F / lowH);
    }

    private static void composite(Uniforms u, ShaderConfig config, int preset, WorldLight light) {
        u.set("uLoSize", lowW, lowH);
        u.setInt("uWorld", 1);
        u.set("uShadeCol", WorldGpu.lin(light.shade[0]) * 1.6F + 0.25F, WorldGpu.lin(light.shade[1]) * 1.6F + 0.25F, WorldGpu.lin(light.shade[2]) * 1.6F + 0.25F);
        u.set("uFogCol", WorldGpu.lin(light.fog[0]), WorldGpu.lin(light.fog[1]), WorldGpu.lin(light.fog[2]));
        u.set("uSunK", WorldLook.get(preset, WorldLook.SUN) * (float) config.sunLight * Math.max(light.strength, 0.25F));
        u.set("uShadowK", WorldLook.get(preset, WorldLook.SHADOWS) * (float) config.shadows * light.strength);
        u.set("uRayK", WorldLook.get(preset, WorldLook.RAYS) * (float) config.godRays);
        u.set("uFogK", WorldLook.get(preset, WorldLook.FOG) * (float) config.fog);
        u.set("uFogSun", config.sunGlowInFog ? 1.0F : 0.0F);
        u.set("uBloomK", WorldLook.get(preset, WorldLook.BLOOM) * (float) config.bloom);
        u.set("uExposure", WorldLook.get(preset, WorldLook.EXPOSURE) * (float) config.exposure);
        u.set("uContrast", WorldLook.get(preset, WorldLook.CONTRAST) * (float) config.contrast);
        u.set("uSat", WorldLook.get(preset, WorldLook.SATURATION) * (float) config.saturation);
        u.set("uWarmth", Math.max(0.0F - 1.0F, Math.min(1.0F, WorldLook.get(preset, WorldLook.WARMTH) + (float) config.warmth)));
        u.set("uVignette", WorldLook.get(preset, WorldLook.VIGNETTE) * (float) config.vignette);
        // No rain pass here: the ground stays dry and nothing is reflected.
        u.set("uWet", 0.0F);
        u.set("uPuddles", 0.0F);
        u.set("uRipples", 0.0F);
        u.set("uGloss", 0.0F);
        u.setInt("uSsrSteps", 0);
        float dk = 0.25F + 0.75F * light.day;
        u.set("uSkyZenith", WorldGpu.lin(0.3F) * dk + 0.004F, WorldGpu.lin(0.34F) * dk + 0.005F, WorldGpu.lin(0.4F) * dk + 0.01F);
        u.set("uSkyHorizon", WorldGpu.lin(0.52F) * dk + 0.006F, WorldGpu.lin(0.56F) * dk + 0.007F, WorldGpu.lin(0.62F) * dk + 0.012F);
        u.set("uWaterTint", 0.2F, 0.3F, 0.35F);
        u.set("uHmOff", 0.0F, 0.0F);
        u.set("uHmCamY", 0.0F);
        u.set("uHmSize", 1.0F);
        u.setInt("uUseHeight", 0);
    }
}
