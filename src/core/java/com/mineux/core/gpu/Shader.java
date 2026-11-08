package com.mineux.core.gpu;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * One of this client's full-screen shaders on the game's own render API, so it draws the same
 * on OpenGL and on Vulkan: a {@link RenderPipeline} per set of target formats, compiled from the
 * mod's GLSL (rewritten by {@link Glsl}), its numbers in {@link #uniforms}, and one triangle
 * that covers the target drawn with no vertex buffer (the vertex shaders make it from
 * {@code gl_VertexID}).
 * <p>
 * The game throws every compiled pipeline away on a resource reload and would then look for our
 * sources among its own, so each draw asks for its pipeline again with our sources first
 * ({@code precompilePipeline}), a map lookup once it is compiled. A shader that will not compile
 * says so once in the log and draws nothing from then on.
 */
public final class Shader {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/gpu");
    private static final Sources SOURCES = new Sources();

    public final Uniforms uniforms;
    private final String name;
    private final Identifier id;
    private final Glsl.Program program;
    private final BlendFunction blend;
    private final Map<List<GpuFormat>, RenderPipeline> pipelines = new HashMap<>();
    private boolean broken;

    private Shader(String name, Glsl.Program program, BlendFunction blend) {
        this.name = name;
        this.id = Identifier.fromNamespaceAndPath("mineux", "fullscreen/" + name);
        this.program = program;
        this.blend = blend;
        this.uniforms = new Uniforms(program, "mineux " + name);
        SOURCES.put(id, program);
    }

    /**
     * A shader from its two sources; {@code blend} null draws over the target, otherwise blends
     * into it. Throws when the sources have a uniform no block can hold.
     */
    public static Shader of(String name, String vertex, String fragment, BlendFunction blend) {
        return new Shader(name.toLowerCase(Locale.ROOT), Glsl.of(vertex, fragment), blend);
    }

    /** A shader file on the classpath: {@code /mineux/<path>}, or {@code path} itself when it starts with a slash. */
    public static String read(String path) throws IOException {
        String resource = path.startsWith("/") ? path : "/mineux/" + path;
        InputStream in = Shader.class.getResourceAsStream(resource);
        if (in == null) throw new IOException("no " + resource);
        try (InputStream open = in) {
            return new String(open.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public boolean broken() {
        return broken;
    }

    /** The pipeline for targets of these formats, compiled; null when it will not compile. */
    RenderPipeline ready(List<GpuFormat> formats) {
        if (broken) return null;
        RenderPipeline pipeline = pipelines.get(formats);
        if (pipeline == null) {
            pipeline = build(formats);
            pipelines.put(formats, pipeline);
        }
        if (RenderSystem.getDevice().precompilePipeline(pipeline, SOURCES).isValid()) return pipeline;
        broken = true;
        LOGGER.warn("Shader {} would not compile on {}; it is off until the game restarts (the reason is above)", name, Backend.name());
        return null;
    }

    private RenderPipeline build(List<GpuFormat> formats) {
        BindGroupLayout.Builder layout = BindGroupLayout.builder();
        for (String sampler : program.samplers) layout.withSampler(sampler);
        if (program.size > 0) layout.withUniform(Glsl.BLOCK, UniformType.UNIFORM_BUFFER);
        StringBuilder location = new StringBuilder("pipeline/fullscreen/").append(name);
        for (GpuFormat format : formats) location.append('_').append(format.name().toLowerCase(Locale.ROOT));
        RenderPipeline.Builder builder = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("mineux", location.toString()))
            .withVertexShader(id)
            .withFragmentShader(id)
            .withBindGroupLayout(layout.build())
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false);
        Optional<BlendFunction> blending = Optional.ofNullable(blend);
        for (int i = 0; i < formats.size(); i++) {
            builder.withColorTargetState(i, new ColorTargetState(blending, formats.get(i), ColorTargetState.WRITE_ALL));
        }
        return builder.build();
    }

    /** A draw being put together: its targets, then its textures by sampler name. */
    public Draw into(GpuTextureView... targets) {
        return new Draw(this, targets);
    }

    /** One full-screen draw of a shader; {@link #run} draws it. */
    public static final class Draw {
        private final Shader shader;
        private final GpuTextureView[] targets;
        private final List<String> names = new ArrayList<>(4);
        private final List<GpuTextureView> views = new ArrayList<>(4);
        private final List<GpuSampler> samplers = new ArrayList<>(4);

        Draw(Shader shader, GpuTextureView[] targets) {
            this.shader = shader;
            this.targets = targets;
        }

        public Draw texture(String sampler, GpuTextureView view, GpuSampler how) {
            names.add(sampler);
            views.add(view);
            samplers.add(how);
            return this;
        }

        /** Draws; false when it could not (the shader will not compile, a target is gone). */
        public boolean run() {
            if (targets.length == 0) return false;
            GpuFormat[] formats = new GpuFormat[targets.length];
            for (int i = 0; i < targets.length; i++) {
                if (targets[i] == null || targets[i].isClosed()) return false;
                formats[i] = targets[i].texture().getFormat();
            }
            for (GpuTextureView view : views) if (view == null || view.isClosed()) return false;
            RenderPipeline pipeline = shader.ready(Arrays.asList(formats));
            if (pipeline == null) return false;
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            shader.uniforms.upload(encoder);
            RenderPassDescriptor descriptor = RenderPassDescriptor.create(new Label("mineux " + shader.name));
            for (GpuTextureView target : targets) descriptor.withColorAttachment(target);
            descriptor.withRenderArea(new RenderPass.RenderArea(0, 0, targets[0].getWidth(0), targets[0].getHeight(0)));
            try (RenderPass pass = encoder.createRenderPass(descriptor)) {
                pass.setPipeline(pipeline);
                shader.uniforms.bind(pass);
                for (int i = 0; i < names.size(); i++) pass.bindTexture(names.get(i), views.get(i), samplers.get(i));
                pass.draw(3, 1, 0, 0);
            }
            return true;
        }
    }

    /** Our programs' sources by id; anything else the game's own, as it would read them. */
    private static final class Sources implements ShaderSource {
        private final Map<Identifier, Glsl.Program> programs = new HashMap<>();

        void put(Identifier id, Glsl.Program program) {
            programs.put(id, program);
        }

        @Override
        public String get(Identifier id, ShaderType type) {
            Glsl.Program program = programs.get(id);
            if (program == null) return Minecraft.getInstance().getShaderManager().getShader(id, type);
            return type == ShaderType.VERTEX ? program.vertex : program.fragment;
        }
    }
}
