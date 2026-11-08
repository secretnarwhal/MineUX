package com.mineux.core.gpu;

import org.junit.jupiter.api.Test;

/**
 * Core's port of the full-screen shader path works end to end: a pass written the way the
 * visuals write theirs (loose uniforms, the triangle from gl_VertexID, one 2D sampler) is
 * rewritten by {@link Glsl} and compiles for Vulkan and OpenGL ({@link ShaderCompile}).
 */
final class ShaderCompileSmokeTest {

    private static final String VERTEX = "#version 330 core\nout vec2 uv;\n"
        + "void main() { vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2); uv = p; gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0); }\n";
    private static final String FRAGMENT = "#version 330 core\nuniform sampler2D uScene;\nuniform float uAmount;\nuniform vec3 uTint;\n"
        + "in vec2 uv;\nout vec4 fragColor;\n"
        + "void main() { vec4 c = texture(uScene, uv); fragColor = vec4(mix(c.rgb, uTint, uAmount), c.a); }\n";

    @Test
    void aTintPassCompilesForBothBackends() {
        ShaderCompile.check("smoke_tint", Glsl.of(VERTEX, FRAGMENT));
    }
}
