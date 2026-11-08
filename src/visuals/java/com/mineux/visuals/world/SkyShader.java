package com.mineux.visuals.world;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mineux.core.gpu.Shader;
import com.mineux.core.gpu.Texture;
import com.mineux.core.gpu.Uniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.mineux.visuals.VisualOps.*;

/**
 * Sky shaders: the sky drawn by a shader of its own -- an aurora, a galaxy, a black hole, a
 * storm, a sunset and the rest of {@link SkyLook}'s presets -- in place of the game's sky disc
 * (and, if asked, the end's sky), with the stars, sunrise glow and dark disc left out under it
 * and the clouds too if asked. The world's fog takes the sky's horizon colour, so the two meet.
 * <p>
 * It is one full-screen pass at the point the game would draw its sky disc, onto the game's
 * main target with its depth, before anything else in the world is drawn: what is in front of
 * the sky still covers it as before. Underwater, in lava or in powder snow the game's own fog
 * sky is kept. A preset that will not build is logged once and the game's sky stays.
 * <p>
 * It draws through the game's own render API ({@link Shader}), so it runs on OpenGL and Vulkan alike.
 */
final class SkyShader {

    private static final List<String> RESOLUTIONS = Arrays.asList("full", "75", "50");
    private static final Vector3f CELESTIAL_AXIS = new Vector3f(0.0F, 0.766F, 0.643F);

    private static final Map<Integer, Shader> PROGRAMS = new HashMap<>();
    private static final Set<Integer> BROKEN = new HashSet<>();
    private static Shader upscale;
    private static boolean upscaleBroken;
    private static final Texture NOISE = new Texture("mineux sky noise", Texture.TABLE);
    private static final Texture LOW = new Texture("mineux sky low", Texture.TARGET);

    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f VIEW_PROJ = new Matrix4f();
    private static final Matrix4f INVERSE = new Matrix4f();
    private static final Matrix3f CELESTIAL = new Matrix3f();
    private static final Vector4f NEAR = new Vector4f();
    private static final Vector4f FAR = new Vector4f();
    private static final float[] MAT4 = new float[16];
    private static final float[] MAT3 = new float[9];
    private static CameraRenderState camera;
    private static float dirSign = 1.0F;
    private static float pixel = 0.002F;

    private static boolean drawnThisFrame;
    private static boolean drawnLastFrame;
    private static long lastNanos;
    private static double clock;
    private static double celestial;

    private SkyShader() {}

    static int resolutionIndex(String name) {
        return RESOLUTIONS.indexOf(name);
    }

    private static boolean on() {
        return World.allowed() && ShaderConfig.get().sky;
    }

    // ---- the loader's hooks ---------------------------------------------------------------------

    static void beginFrame(Matrix4fc view, CameraRenderState state) {
        drawnLastFrame = drawnThisFrame;
        drawnThisFrame = false;
        VIEW.set(view);
        camera = state;
    }

    /** Whether to leave out one of the sky's pieces; for the disc and the end's sky, the shader drew in its place. */
    static boolean hides(int piece) {
        if (!on()) return false;
        ShaderConfig config = ShaderConfig.get();
        if (piece == HIDE_CLOUDS) return config.skyHideClouds;
        if (piece == HIDE_SKY_DISC) return tryDraw();
        if (piece == HIDE_END_SKY) return config.skyInEnd && tryDraw();
        if (piece == HIDE_SKY_EXTRAS) return drawnThisFrame;
        if (piece == HIDE_SUN_MOON) return drawnThisFrame && !config.skySunMoon;
        return false;
    }

    /** The world's fog in the sky's horizon colour, once the shader has drawn a frame. */
    static void fog(FogData fog) {
        if (!on() || !drawnLastFrame || fog.color == null) return;
        ShaderConfig config = ShaderConfig.get();
        if (!config.skyMatchFog) return;
        WorldConfig world = WorldConfig.get();
        if (world.ambience && world.fogColour) return;
        int preset = Math.max(0, SkyLook.index(config.skyPreset));
        int[] colours = colours(config, preset);
        float[] h = SkyLook.horizon(preset, SkyLook.rgb(colours[0]), SkyLook.rgb(colours[1]));
        fog.color.set(h[0], h[1], h[2], fog.color.w);
        if (preset != 9) return;
        // The fog preset is a sky that comes down to meet you.
        float end = (float) config.skyFogDistance;
        float start = end * 0.04F;
        fog.environmentalStart = Math.min(fog.environmentalStart, start);
        fog.environmentalEnd = Math.min(fog.environmentalEnd, end);
        fog.renderDistanceStart = Math.min(fog.renderDistanceStart, start);
        fog.renderDistanceEnd = Math.min(fog.renderDistanceEnd, end);
        fog.cloudEnd = Math.min(fog.cloudEnd, end);
    }

    /** The two colours the sky is drawn in this frame, 0xRRGGBB each. */
    private static int[] colours(ShaderConfig config, int preset) {
        int[] natural = SkyLook.natural(preset);
        int c1 = config.skyNatural ? natural[0] : World.rgb(config.skyColor, natural[0]);
        int c2 = config.skyNatural ? natural[1] : World.rgb(config.skyColor2, natural[1]);
        return new int[] {c1, c2};
    }

    // ---- drawing ------------------------------------------------------------------------------

    private static boolean tryDraw() {
        CameraRenderState state = camera;
        if (state == null) return false;
        if (state.fogType != null && state.fogType != FogType.NONE && state.fogType != FogType.ATMOSPHERIC) return false;
        boolean drawn;
        try {
            drawn = draw(Minecraft.getInstance());
        } catch (RuntimeException | LinkageError e) {
            World.fail(World.SKY, e);
            drawn = false;
        }
        drawnThisFrame = drawn;
        return drawn;
    }

    private static boolean draw(Minecraft mc) {
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0 || !matrices()) return false;
        GpuTextureView target = main.getColorTextureView();
        if (target == null) return false;
        ShaderConfig config = ShaderConfig.get();
        int preset = Math.max(0, SkyLook.index(config.skyPreset));
        if (BROKEN.contains(preset)) return false;
        tick(config);
        Shader shader = program(preset);
        if (shader == null) return false;
        int res = Math.max(0, resolutionIndex(config.skyResolution));
        float share = res == 2 ? 0.5F : res == 1 ? 0.75F : 1.0F;
        Shader up = share < 0.999F ? upscale() : null;
        int w = main.width;
        int h = main.height;
        int lw = up != null ? Math.max(1, Math.round(w * share)) : w;
        int lh = up != null ? Math.max(1, Math.round(h * share)) : h;
        GpuTextureView noise = noise();
        uniforms(shader.uniforms, config, preset, lh);
        if (up == null) return drawn(preset, shader, shader.into(target).texture("uNoise", noise, WorldGpu.repeat()).run());
        GpuTextureView low = LOW.ensure(GpuFormat.RGBA16_FLOAT, lw, lh);
        if (!drawn(preset, shader, shader.into(low).texture("uNoise", noise, WorldGpu.repeat()).run())) return false;
        return up.into(target).texture("uSource", low, WorldGpu.linear()).run();
    }

    /** Whether the preset drew; one that will not compile is not asked again. */
    private static boolean drawn(int preset, Shader shader, boolean drawn) {
        if (!drawn && shader.broken()) BROKEN.add(preset);
        return drawn;
    }

    /** The camera's projection times this frame's view, into {@code dest}; false before the first frame. */
    static boolean viewProjection(Matrix4f dest) {
        CameraRenderState state = camera;
        if (state == null || state.projectionMatrix == null) return false;
        state.projectionMatrix.mul(VIEW, dest);
        return dest.isFinite();
    }

    /** The view and projection this frame, inverted: each pixel's ray out into the sky. */
    private static boolean matrices() {
        CameraRenderState state = camera;
        if (state == null || state.projectionMatrix == null) return false;
        Matrix4f projection = state.projectionMatrix;
        projection.mul(VIEW, VIEW_PROJ);
        if (!VIEW_PROJ.isFinite() || Math.abs(VIEW_PROJ.determinant()) < 1.0E-12F) return false;
        VIEW_PROJ.invert(INVERSE);
        if (!INVERSE.isFinite()) return false;
        INVERSE.transform(0.0F, 0.0F, 0.0F - 1.0F, 1.0F, NEAR);
        INVERSE.transform(0.0F, 0.0F, 1.0F, 1.0F, FAR);
        float rx = FAR.x * NEAR.w - NEAR.x * FAR.w;
        float ry = FAR.y * NEAR.w - NEAR.y * FAR.w;
        float rz = FAR.z * NEAR.w - NEAR.z * FAR.w;
        float fx = 0.0F - VIEW.m02();
        float fy = 0.0F - VIEW.m12();
        float fz = 0.0F - VIEW.m22();
        dirSign = rx * fx + ry * fy + rz * fz < 0.0F ? 0.0F - 1.0F : 1.0F;
        float m11 = Math.abs(projection.m11());
        pixel = m11;
        return true;
    }

    private static void tick(ShaderConfig config) {
        long now = System.nanoTime();
        double dt = lastNanos == 0L ? 0.0 : (now - lastNanos) / 1.0E9;
        lastNanos = now;
        dt = Math.max(0.0, Math.min(dt, 0.1));
        clock = (clock + dt * config.skySpeed) % 10000.0;
        celestial = (celestial + dt * config.skyRotation * 0.010471975511965976) % (Math.PI * 2.0);
    }

    private static void uniforms(Uniforms u, ShaderConfig config, int preset, int height) {
        int[] colours = colours(config, preset);
        float[] c1 = SkyLook.rgb(colours[0]);
        float[] c2 = SkyLook.rgb(colours[1]);
        float[] horizon = SkyLook.horizon(preset, c1, c2);
        INVERSE.get(MAT4);
        u.setMat4("uInvViewProj", MAT4);
        u.set("uTime", (float) clock);
        u.set("uColor1", WorldGpu.lin(c1[0]), WorldGpu.lin(c1[1]), WorldGpu.lin(c1[2]));
        u.set("uColor2", WorldGpu.lin(c2[0]), WorldGpu.lin(c2[1]), WorldGpu.lin(c2[2]));
        u.set("uIntensity", (float) config.skyIntensity);
        u.set("uScale", (float) config.skyScale);
        u.set("uStars", (float) config.skyStars);
        u.set("uBrightness", (float) config.skyBrightness);
        u.set("uHorizon", horizon[0], horizon[1], horizon[2]);
        u.set("uHorizonFog", (float) config.skyHorizon);
        u.set("uDirSign", dirSign);
        CELESTIAL.rotation((float) celestial, CELESTIAL_AXIS).get(MAT3);
        u.setMat3("uCelestial", MAT3);
        u.set("uPixel", pixel > 1.0E-4F ? 2.0F / (pixel * height) : 0.002F);
    }

    private static Shader program(int preset) {
        Shader built = PROGRAMS.get(preset);
        if (built != null) return built;
        if (BROKEN.contains(preset)) return null;
        String name = SkyLook.file(preset);
        try {
            Shader shader = Shader.of("sky_" + name, WorldGpu.read("sky/sky.vsh"), fragment(name), null);
            PROGRAMS.put(preset, shader);
            return shader;
        } catch (java.io.IOException | RuntimeException e) {
            BROKEN.add(preset);
            org.slf4j.LoggerFactory.getLogger("mineux/world").warn("sky shader {} would not build; the game's sky stays", name, e);
            return null;
        }
    }

    /** A preset's fragment source: the shared helpers, then the preset. */
    static String fragment(String name) throws java.io.IOException {
        return "#version 330 core\n" + WorldGpu.read("sky/common.glsl") + "\n" + WorldGpu.read("sky/" + name + ".fsh");
    }

    private static Shader upscale() {
        if (upscale != null) return upscale.broken() ? null : upscale;
        if (upscaleBroken) return null;
        try {
            upscale = Shader.of("sky_upscale", WorldGpu.read("sky/upscale.vsh"), WorldGpu.read("sky/upscale.fsh"), null);
            return upscale;
        } catch (java.io.IOException | RuntimeException e) {
            upscaleBroken = true;
            return null;
        }
    }

    /** A 256 x 256 tile of random bytes the presets read their noise from, the same every time. */
    private static GpuTextureView noise() {
        if (NOISE.is(GpuFormat.RGBA8_UNORM, 256, 256)) return NOISE.view();
        GpuTextureView view = NOISE.ensure(GpuFormat.RGBA8_UNORM, 256, 256);
        ByteBuffer pixels = MemoryUtil.memAlloc(256 * 256 * 4);
        try {
            pixels.put(noiseBytes()).flip();
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(NOISE.texture(), pixels, 0, 0, 0, 0, 256, 256);
        } finally {
            MemoryUtil.memFree(pixels);
        }
        return view;
    }

    /** The tile's bytes, RGBA row by row. */
    static byte[] noiseBytes() {
        int size = 256 * 256;
        byte[] r = new byte[size];
        byte[] b = new byte[size];
        byte[] a = new byte[size];
        int s = 0x9E3779B9;
        for (int i = 0; i < size; i++) {
            s = xorshift(s);
            r[i] = (byte) (s >>> 24);
            s = xorshift(s);
            b[i] = (byte) (s >>> 24);
            s = xorshift(s);
            a[i] = (byte) (s >>> 24);
        }
        byte[] pixels = new byte[size * 4];
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                int i = y * 256 + x;
                int j = ((y - 17) & 0xFF) * 256 + ((x - 37) & 0xFF);
                pixels[i * 4] = r[i];
                pixels[i * 4 + 1] = r[j];
                pixels[i * 4 + 2] = b[i];
                pixels[i * 4 + 3] = a[i];
            }
        }
        return pixels;
    }

    private static int xorshift(int s) {
        int x = s ^ (s << 13);
        x ^= x >>> 17;
        return x ^ (x << 5);
    }
}
