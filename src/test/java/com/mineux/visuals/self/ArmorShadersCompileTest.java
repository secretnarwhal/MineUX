package com.mineux.visuals.self;

import com.mineux.core.gpu.ShaderCompile;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import net.minecraft.client.renderer.ShaderDefines;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every armour material's pipeline source -- armor.vsh/armor.fsh with the game's own includes
 * resolved as {@link ArmorGlsl} resolves them and MATERIAL defined as {@link ArmorShade} defines
 * it -- compiles for the Vulkan backend and for OpenGL. Headless: the includes come out of the
 * game jar on the test classpath.
 */
class ArmorShadersCompileTest {

    @Test
    void everyArmourMaterialCompiles() throws IOException {
        String vertex = ShaderCompile.resource("shaders/armor.vsh");
        String fragment = ShaderCompile.resource("shaders/armor.fsh");
        assertTrue(ArmorShade.MATERIALS.size() == 8, ArmorShade.MATERIALS.toString());
        for (int m = 0; m < ArmorShade.MATERIALS.size(); m++) {
            ShaderDefines defines = ShaderDefines.builder().define("MATERIAL", m).build();
            String v = GlslPreprocessor.injectDefines(String.join("", new Includes().process(vertex)), defines);
            String f = GlslPreprocessor.injectDefines(String.join("", new Includes().process(fragment)), defines);
            ShaderCompile.compiles("armor_" + ArmorShade.MATERIALS.get(m), v, f);
        }
    }

    /** The game's includes, each once, from its jar. */
    private static final class Includes extends GlslPreprocessor {
        private final Set<String> seen = new HashSet<>();

        @Override
        public String applyImport(boolean isRelative, String path) {
            if (isRelative) return "#error no relative imports";
            String file = path.contains(":") ? path.substring(path.indexOf(':') + 1) : path;
            if (!seen.add(file)) return null;
            try (InputStream in = ArmorShadersCompileTest.class.getResourceAsStream("/assets/minecraft/shaders/include/" + file)) {
                if (in == null) return "#error no include " + file;
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "#error " + e.getMessage();
            }
        }
    }
}
