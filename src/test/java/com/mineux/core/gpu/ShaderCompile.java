
package com.mineux.core.gpu;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.vulkan.glsl.GlslCompiler;
import com.mojang.blaze3d.vulkan.glsl.IntermediaryShaderModule;
import net.minecraft.client.renderer.ShaderDefines;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.util.spvc.SpvcReflectedResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Compiles a rewritten program the way the game's Vulkan backend does -- its own
 * {@link GlslCompiler} (shaderc, Vulkan 1.x, auto bindings and locations) -- and, for the
 * OpenGL side, through shaderc for OpenGL 4.5, then reads the compiled block back with
 * SPIRV-Cross and checks every member sits where {@link Glsl} put it and every sampler is 2D.
 * No GPU and no game window needed.
 */
public final class ShaderCompile {

    private ShaderCompile() {}

    /** A shader resource, {@code /mineux/<path>}, from the classpath or the source tree (core's, then the visuals'). */
    public static String resource(String path) throws IOException {
        try (InputStream in = ShaderCompile.class.getResourceAsStream("/mineux/" + path)) {
            if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Path core = Path.of("src/core/resources/mineux/" + path);
        if (Files.exists(core)) return Files.readString(core);
        return Files.readString(Path.of("src/visuals/resources/mineux/" + path));
    }

    /**
     * A program the game compiles itself as one of its own pipelines (loose samplers, the game's
     * include blocks), not a {@link Glsl} rewrite: both stages through the Vulkan backend's own
     * compiler and through shaderc for OpenGL 4.5; fails the test on any fault.
     */
    public static void compiles(String name, String vertex, String fragment) {
        try (GlslCompiler compiler = new GlslCompiler()) {
            for (ShaderType type : new ShaderType[] {ShaderType.VERTEX, ShaderType.FRAGMENT}) {
                String source = type == ShaderType.VERTEX ? vertex : fragment;
                try {
                    IntermediaryShaderModule module = compiler.createIntermediary(name + "." + type.name().toLowerCase(), source, type);
                    module.close();
                } catch (Exception e) {
                    fail(name + " " + type + " does not compile for the Vulkan backend: " + e.getMessage() + "\n" + source);
                }
                compile(source, type, true, name);
            }
        }
    }

    /** Compiles both stages for Vulkan and OpenGL and checks the block and the samplers; fails the test on any fault. */
    public static void check(String name, Glsl.Program program) {
        ShaderDefines vulkan = ShaderDefines.builder().define("gl_VertexID", "gl_VertexIndex").define("gl_InstanceID", "gl_InstanceIndex").build();
        try (GlslCompiler compiler = new GlslCompiler()) {
            for (ShaderType type : new ShaderType[] {ShaderType.VERTEX, ShaderType.FRAGMENT}) {
                String source = type == ShaderType.VERTEX ? program.vertex : program.fragment;
                try {
                    IntermediaryShaderModule module = compiler.createIntermediary(name + "." + type.name().toLowerCase(), source, type);
                    module.close();
                } catch (Exception e) {
                    fail(name + " " + type + " does not compile for the Vulkan backend: " + e.getMessage() + "\n" + source);
                }
                ByteBuffer spirv = compile(GlslPreprocessor.injectDefines(source, vulkan), type, false, name);
                reflect(name + " " + type + " (vulkan)", program, spirv);
                ByteBuffer gl = compile(source, type, true, name);
                reflect(name + " " + type + " (opengl)", program, gl);
            }
        }
    }

    private static ByteBuffer compile(String source, ShaderType type, boolean opengl, String name) {
        long compiler = Shaderc.shaderc_compiler_initialize();
        long options = Shaderc.shaderc_compile_options_initialize();
        try {
            if (opengl) {
                Shaderc.shaderc_compile_options_set_target_env(options, Shaderc.shaderc_target_env_opengl, Shaderc.shaderc_env_version_opengl_4_5);
            } else {
                Shaderc.shaderc_compile_options_set_target_env(options, 0, 4202496);
            }
            Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options, true);
            Shaderc.shaderc_compile_options_set_auto_map_locations(options, true);
            Shaderc.shaderc_compile_options_set_generate_debug_info(options);
            int kind = type == ShaderType.FRAGMENT ? Shaderc.shaderc_fragment_shader : Shaderc.shaderc_vertex_shader;
            long result = Shaderc.shaderc_compile_into_spv(compiler, source, kind, name, "main", options);
            try {
                if (Shaderc.shaderc_result_get_compilation_status(result) != Shaderc.shaderc_compilation_status_success) {
                    fail(name + " " + type + " does not compile for " + (opengl ? "OpenGL" : "Vulkan") + ": "
                        + Shaderc.shaderc_result_get_error_message(result) + "\n" + source);
                }
                ByteBuffer bytes = Shaderc.shaderc_result_get_bytes(result);
                ByteBuffer copy = ByteBuffer.allocateDirect(bytes.remaining()).order(java.nio.ByteOrder.nativeOrder());
                copy.put(bytes).flip();
                return copy;
            } finally {
                Shaderc.shaderc_result_release(result);
            }
        } finally {
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }

    /** The block's members by offset and the samplers' dimensions, as SPIRV-Cross reads them. */
    private static void reflect(String what, Glsl.Program program, ByteBuffer spirv) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pb = stack.mallocPointer(1);
            ok(Spvc.spvc_context_create(pb), what);
            long context = pb.get(0);
            try {
                IntBuffer words = spirv.asIntBuffer();
                ok(Spvc.spvc_context_parse_spirv(context, words, words.remaining(), pb), what);
                long ir = pb.get(0);
                ok(Spvc.spvc_context_create_compiler(context, Spvc.SPVC_BACKEND_NONE, ir, Spvc.SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, pb), what);
                long compiler = pb.get(0);
                ok(Spvc.spvc_compiler_create_shader_resources(compiler, pb), what);
                long resources = pb.get(0);
                PointerBuffer list = stack.mallocPointer(1);
                PointerBuffer count = stack.mallocPointer(1);

                ok(Spvc.spvc_resources_get_resource_list_for_type(resources, Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER, list, count), what);
                SpvcReflectedResource.Buffer blocks = SpvcReflectedResource.create(list.get(0), (int) count.get(0));
                Map<String, Integer> seen = new HashMap<>();
                for (SpvcReflectedResource block : blocks) {
                    String blockName = Spvc.spvc_compiler_get_name(compiler, block.base_type_id());
                    assertEquals(Glsl.BLOCK, blockName, what + ": a uniform block other than ours");
                    long type = Spvc.spvc_compiler_get_type_handle(compiler, block.base_type_id());
                    int members = Spvc.spvc_type_get_num_member_types(type);
                    IntBuffer offset = stack.mallocInt(1);
                    for (int i = 0; i < members; i++) {
                        String member = Spvc.spvc_compiler_get_member_name(compiler, block.base_type_id(), i);
                        ok(Spvc.spvc_compiler_type_struct_member_offset(compiler, type, i, offset), what);
                        seen.put(member, offset.get(0));
                    }
                    PointerBuffer size = stack.mallocPointer(1);
                    ok(Spvc.spvc_compiler_get_declared_struct_size(compiler, type, size), what);
                    assertTrue(size.get(0) <= program.size, what + ": block is " + size.get(0) + " bytes, laid out as " + program.size);
                }
                for (Map.Entry<String, Integer> e : seen.entrySet()) {
                    assertEquals(program.offset(e.getKey()), (int) e.getValue(), what + ": " + e.getKey() + " offset");
                }
                assertEquals(program.names.size(), seen.size(), what + ": block members " + seen.keySet());

                ok(Spvc.spvc_resources_get_resource_list_for_type(resources, Spvc.SPVC_RESOURCE_TYPE_SAMPLED_IMAGE, list, count), what);
                List<String> samplers = new ArrayList<>();
                for (SpvcReflectedResource sampler : SpvcReflectedResource.create(list.get(0), (int) count.get(0))) {
                    long type = Spvc.spvc_compiler_get_type_handle(compiler, sampler.type_id());
                    assertEquals(1, Spvc.spvc_type_get_image_dimension(type), what + ": " + sampler.nameString() + " is not a 2D sampler");
                    samplers.add(sampler.nameString());
                }
                assertTrue(program.samplers.containsAll(samplers), what + ": samplers " + samplers + " vs " + program.samplers);

                ok(Spvc.spvc_resources_get_resource_list_for_type(resources, Spvc.SPVC_RESOURCE_TYPE_GL_PLAIN_UNIFORM, list, count), what);
                assertEquals(0L, count.get(0), what + ": a loose uniform is left");
            } finally {
                Spvc.spvc_context_destroy(context);
            }
        }
    }

    private static void ok(int result, String what) {
        if (result != Spvc.SPVC_SUCCESS) fail(what + ": spvc error " + result);
    }
}
