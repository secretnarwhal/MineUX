package com.mineux.visuals.hand;

import com.mineux.core.gpu.Glsl;
import com.mineux.core.gpu.Shader;
import com.mineux.core.gpu.ShaderCompile;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every hand shader, rewritten as drawn, compiles for the Vulkan and OpenGL backends. */
class HandShadersCompileTest {

    @Test
    void everyHandShaderCompiles() throws IOException {
        String vertex = Shader.read("hand/hand.vsh");
        for (String name : new String[] {"chroma", "invert", "outline", "pixelate", "prism"}) {
            assertTrue(HandShaders.index(name) >= 0, name);
            Glsl.Program p = Glsl.of(vertex, Shader.read("hand/" + name + ".fsh"));
            assertTrue(p.samplers.contains("ColorTexture"), name + " " + p.samplers);
            ShaderCompile.check("hand_" + name, p);
        }
    }
}
