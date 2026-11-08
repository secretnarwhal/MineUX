package com.mineux.screens.render;

import com.mineux.screens.Screens;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws screens into the world through the game's render API (a {@link RenderPipeline} of our
 * own and {@code submitCustomGeometry}), the same on OpenGL and Vulkan.
 * <p>
 * A screen is a strip of quads between a top and a bottom edge, textured with its frame, drawn
 * unlit so it looks the same in the dark, and a second time facing the other way in black so it
 * has a back. Its shader comes from the jar, not from a resource pack (MineUX has no resource
 * loader): every frame asks the device for the pipeline with our sources first, which is a map
 * lookup once it is compiled and recompiles it after a resource reload threw it away.
 */
public final class ScreenRenderer {

    private static final Identifier SHADER = Identifier.fromNamespaceAndPath("mineux", "screens/screen");
    private static final Sources SOURCES = new Sources();

    private static final RenderPipeline.Snippet SCREEN = RenderPipeline.builder()
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
        .withVertexShader(SHADER)
        .withFragmentShader(SHADER)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withDepthStencilState(DepthStencilState.DEFAULT)
        .buildSnippet();

    private static final RenderPipeline SOLID = RenderPipeline.builder(SCREEN)
        .withLocation(Identifier.fromNamespaceAndPath("mineux", "pipeline/screen_solid"))
        .build();

    private static final RenderPipeline SEE_THROUGH = RenderPipeline.builder(SCREEN)
        .withLocation(Identifier.fromNamespaceAndPath("mineux", "pipeline/screen_see_through"))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .build();

    private static final Map<Identifier, RenderType> SOLID_TYPES = new HashMap<>();
    private static final Map<Identifier, RenderType> SEE_THROUGH_TYPES = new HashMap<>();

    /** Whether our pipelines compiled this frame; false falls back to the game's entity types. */
    private static boolean ready;
    private static boolean warned;

    private ScreenRenderer() {}

    /** Smooth when a screen is far away, exact pixels when it is close. */
    public static GpuSampler sampler() {
        return RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.NEAREST, false);
    }

    /** Once a frame before the screens are submitted: our two pipelines, compiled from our sources. */
    public static void prepare() {
        boolean ok = RenderSystem.getDevice().precompilePipeline(SOLID, SOURCES).isValid()
            && RenderSystem.getDevice().precompilePipeline(SEE_THROUGH, SOURCES).isValid();
        if (!ok && !warned) {
            warned = true;
            Screens.LOGGER.warn("The screens shader would not compile; screens are drawn with the game's entity shader (reason above)");
        }
        ready = ok;
    }

    private static RenderType renderType(Identifier texture, boolean seeThrough) {
        Map<Identifier, RenderType> types = seeThrough ? SEE_THROUGH_TYPES : SOLID_TYPES;
        RenderType type = types.get(texture);
        if (type != null) return type;
        RenderSetup setup = RenderSetup.builder(seeThrough ? SEE_THROUGH : SOLID)
            .withTexture("Sampler0", texture, ScreenRenderer::sampler)
            .createRenderSetup();
        type = Types.create(seeThrough ? "mineux_screen_see_through" : "mineux_screen_solid", setup);
        types.put(texture, type);
        return type;
    }

    /** Forget the render types of a texture that no longer exists. */
    public static void release(Identifier texture) {
        SOLID_TYPES.remove(texture);
        SEE_THROUGH_TYPES.remove(texture);
    }

    /**
     * Draw a screen. {@code top} and {@code bottom} are its edges from left to right, relative
     * to the camera, with as many points each. {@code facing} points out of the front.
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Identifier texture, Vec3[] top, Vec3[] bottom, Vec3 facing, float opacity) {
        boolean seeThrough = opacity < 0.999f;
        float alpha = seeThrough ? Math.clamp(opacity, 0.0f, 1.0f) : 1.0f;
        int image = ARGB.white(alpha);
        int back = ARGB.black(alpha);
        // Shader packs replace the game's shaders and do not know ours; so does a failed compile.
        if (!ready || Iris.shaderPackActive()) {
            RenderType type = seeThrough ? RenderTypes.entityTranslucent(texture) : RenderTypes.entityCutoutCull(texture);
            collector.submitCustomGeometry(poseStack, type, new Strip(top, bottom, image, false, facing));
            collector.submitCustomGeometry(poseStack, type, new Strip(top, bottom, back, true, facing.reverse()));
            return;
        }
        RenderType type = renderType(texture, seeThrough);
        collector.submitCustomGeometry(poseStack, type, new Strip(top, bottom, image, false, null));
        collector.submitCustomGeometry(poseStack, type, new Strip(top, bottom, back, true, null));
    }

    /** Draw a line through {@code points}, which are relative to the camera. */
    public static void outline(PoseStack poseStack, SubmitNodeCollector collector, Vec3[] points, int color, float width) {
        collector.submitCustomGeometry(poseStack, RenderTypes.lines(), new Line(points, color, width));
    }

    /** Our shader sources from the jar by id; any other the game's own, as it would read them. */
    private static final class Sources implements ShaderSource {
        private String vertex;
        private String fragment;

        @Override
        public String get(Identifier id, ShaderType type) {
            if (id == null || !id.equals(SHADER)) return Minecraft.getInstance().getShaderManager().getShader(id, type);
            if (vertex == null) {
                vertex = read("/mineux/screens/screen.vsh");
                fragment = read("/mineux/screens/screen.fsh");
            }
            return type == ShaderType.VERTEX ? vertex : fragment;
        }

        private static String read(String resource) {
            try (InputStream in = ScreenRenderer.class.getResourceAsStream(resource)) {
                if (in == null) return null;
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return null;
            }
        }
    }
}
