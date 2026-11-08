
package com.mineux.core.gpu;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlslTest {

    private static final String VERTEX = "#version 330 core\nuniform mat4 uInvViewProj;\nout vec2 uv;\nvoid main() { uv = vec2(0.0); gl_Position = uInvViewProj * vec4(0.0); }\n";
    private static final String FRAGMENT = String.join("\n",
        "#version 330 core",
        "in vec2 uv;",
        "out vec4 fragColor;",
        "uniform sampler2D uScene;   // the frame",
        "uniform float uA;",
        "uniform vec3  uB, uC;       // two colours",
        "uniform float uD;",
        "uniform mat3  uE;",
        "uniform vec2  uF;",
        "uniform int   uG;",
        "uniform mat4  uInvViewProj;",
        "void main() { fragColor = texture(uScene, uv) * uA + vec4(uB + uC + uE * vec3(uD), float(uG)) + uF.xyxy + uInvViewProj[0]; }",
        "");

    @Test
    void looseNumbersMoveIntoOneBlockAtTheTop() {
        Glsl.Program p = Glsl.of(VERTEX, FRAGMENT);
        assertEquals(Arrays.asList("uInvViewProj", "uA", "uB", "uC", "uD", "uE", "uF", "uG"), p.names);
        assertEquals(List.of("uScene"), p.samplers);
        for (String source : new String[] {p.vertex, p.fragment}) {
            assertTrue(source.startsWith("#version 330 core\nlayout(std140) uniform " + Glsl.BLOCK + " {\n"), source);
            assertFalse(source.contains("uniform float"), source);
            assertTrue(source.contains("uniform sampler2D uScene") || source == p.vertex, source);
        }
        // Line numbers in the bodies hold: one line per declaration taken out, blank.
        assertEquals(FRAGMENT.split("\n", -1).length + p.names.size() + 2, p.fragment.split("\n", -1).length);
    }

    @Test
    void std140Offsets() {
        Glsl.Program p = Glsl.of(VERTEX, FRAGMENT);
        assertEquals(0, p.offset("uInvViewProj"));
        assertEquals(64, p.offset("uA"));
        assertEquals(80, p.offset("uB"));   // vec3 to 16
        assertEquals(96, p.offset("uC"));
        assertEquals(108, p.offset("uD"));  // a float tucks in after a vec3
        assertEquals(112, p.offset("uE"));  // mat3: three padded columns
        assertEquals(160, p.offset("uF"));
        assertEquals(168, p.offset("uG"));
        assertEquals(176, p.size);
        assertEquals(-1, p.offset("uNope"));
    }

    @Test
    void writesGoWhereTheBlockReadsThem() {
        Glsl.Program p = Glsl.of(VERTEX, FRAGMENT);
        Uniforms u = new Uniforms(p, "test");
        u.set("uA", 2.5F);
        u.setInt("uG", 7);
        u.set("uG", 3.0F);
        u.setMat3("uE", new float[] {1, 2, 3, 4, 5, 6, 7, 8, 9});
        u.set("uNope", 1.0F);
        ByteBuffer b = u.bytes();
        assertEquals(2.5F, b.getFloat(64));
        assertEquals(3, b.getInt(168));
        assertEquals(1.0F, b.getFloat(112));
        assertEquals(3.0F, b.getFloat(120));
        assertEquals(4.0F, b.getFloat(128));
        assertEquals(9.0F, b.getFloat(152));
    }

    @Test
    void aNumberMustBeOneTypeInBothStages() {
        String fragment = "#version 330 core\nuniform vec4 uInvViewProj;\nout vec4 c;\nvoid main() { c = uInvViewProj; }\n";
        assertThrows(IllegalArgumentException.class, () -> Glsl.of(VERTEX, fragment));
        assertThrows(IllegalArgumentException.class, () -> Glsl.of(VERTEX, "#version 330 core\nuniform dmat4 uX;\n"));
    }

    @Test
    void aProgramWithNoNumbersHasNoBlock() {
        Glsl.Program p = Glsl.of("#version 330 core\nvoid main() {}\n", "#version 330 core\nuniform sampler2D s;\nvoid main() {}\n");
        assertEquals(0, p.size);
        assertFalse(p.fragment.contains(Glsl.BLOCK));
    }

    @Test
    void theRewriteCompilesForBothBackends() {
        ShaderCompile.check("glsl_test", Glsl.of(VERTEX, FRAGMENT));
    }

    /** The check itself catches what Vulkan would refuse: a 3D sampler, a loose uniform it cannot hold. */
    @Test
    void theCompileCheckCatchesWhatVulkanRefuses() {
        String vertex = "#version 330 core\nout vec2 uv;\nvoid main() { uv = vec2(0.0); gl_Position = vec4(0.0); }\n";
        String threeD = "#version 330 core\nin vec2 uv;\nout vec4 c;\nuniform sampler3D n;\nvoid main() { c = texture(n, vec3(uv, 0.0)); }\n";
        assertThrows(org.opentest4j.AssertionFailedError.class, () -> ShaderCompile.check("bad_3d", Glsl.of(vertex, threeD)));
        String loose = "#version 330 core\nin vec2 uv;\nout vec4 c;\nuniform float a[2];\nvoid main() { c = vec4(a[0]); }\n";
        assertThrows(org.opentest4j.AssertionFailedError.class, () -> ShaderCompile.check("bad_loose", Glsl.of(vertex, loose)));
    }

    @Test
    void backendNames() {
        assertTrue(Backend.isVulkan("Vulkan"));
        assertTrue(Backend.isVulkan(Backend.VULKAN));
        assertFalse(Backend.isVulkan(Backend.OPENGL));
        assertFalse(Backend.isVulkan(null));
        // No game, no device: never taken for Vulkan.
        assertFalse(Backend.vulkan());
        assertEquals("unknown", Backend.name());
    }
}
