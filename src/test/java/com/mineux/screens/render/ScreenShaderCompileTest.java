package com.mineux.screens.render;

import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.vulkan.glsl.GlslCompiler;
import com.mojang.blaze3d.vulkan.glsl.IntermediaryShaderModule;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.shaderc.Shaderc;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The screen shader from the jar, compiled the way each backend will: the game's own Vulkan
 * GLSL compiler (shaderc, auto bindings), and shaderc for OpenGL 4.5. No GPU, no window.
 */
class ScreenShaderCompileTest {

    private static String read(String path) throws Exception {
        try (InputStream in = ScreenShaderCompileTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is packed");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void compilesForVulkanAndOpenGl() throws Exception {
        String vertex = read("/mineux/screens/screen.vsh");
        String fragment = read("/mineux/screens/screen.fsh");
        try (GlslCompiler compiler = new GlslCompiler()) {
            IntermediaryShaderModule v = compiler.createIntermediary("screen.vsh", vertex, ShaderType.VERTEX);
            v.close();
            IntermediaryShaderModule f = compiler.createIntermediary("screen.fsh", fragment, ShaderType.FRAGMENT);
            f.close();
        }
        opengl(vertex, Shaderc.shaderc_glsl_vertex_shader, "screen.vsh");
        opengl(fragment, Shaderc.shaderc_glsl_fragment_shader, "screen.fsh");
    }

    private static void opengl(String source, int kind, String name) {
        long compiler = Shaderc.shaderc_compiler_initialize();
        long options = Shaderc.shaderc_compile_options_initialize();
        try {
            Shaderc.shaderc_compile_options_set_target_env(options, Shaderc.shaderc_target_env_opengl, Shaderc.shaderc_env_version_opengl_4_5);
            Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options, true);
            Shaderc.shaderc_compile_options_set_auto_map_locations(options, true);
            long result = Shaderc.shaderc_compile_into_spv(compiler, source, kind, name, "main", options);
            try {
                assertEquals(Shaderc.shaderc_compilation_status_success, Shaderc.shaderc_result_get_compilation_status(result),
                    name + " for OpenGL: " + Shaderc.shaderc_result_get_error_message(result));
            } finally {
                Shaderc.shaderc_result_release(result);
            }
        } finally {
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }
}
