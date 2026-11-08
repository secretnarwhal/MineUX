package com.mineux.visuals.view;

import com.mineux.core.gpu.Glsl;
import com.mineux.core.gpu.ShaderCompile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Motion blur's shader, rewritten as drawn, compiles for the Vulkan and OpenGL backends. */
class MotionBlurPassTest {

    @Test
    void compilesForBothBackends() {
        Glsl.Program p = Glsl.of(MotionBlurPass.VERTEX, MotionBlurPass.FRAGMENT);
        assertEquals(java.util.List.of("uScene"), p.samplers);
        assertEquals(6, p.names.size());
        ShaderCompile.check("motion_blur", p);
    }
}
