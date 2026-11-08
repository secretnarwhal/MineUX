package com.mineux.visuals.self;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The armour shaders' source, for the pipelines {@link ArmorShade} builds.
 * <p>
 * The client puts no files in the game's resources, so the shaders cannot be where the game looks
 * for its own. They come out of the jar instead, and each pipeline is compiled with this as
 * its source -- the game's own includes ({@code #moj_import <minecraft:globals.glsl>} and the like)
 * read from the game's resources as the game reads them. A pipeline compiled this way stays
 * compiled until the next resource reload throws every compiled pipeline away; the armour asks for
 * it again before each use ({@link ArmorShade#ready}), which compiles it again then, and costs a
 * map lookup otherwise.
 */
final class ArmorGlsl implements ShaderSource {

    static final ArmorGlsl INSTANCE = new ArmorGlsl();

    static final Identifier SHADER = Identifier.fromNamespaceAndPath("mineux", "core/armor");

    private String vertex;
    private String fragment;
    private boolean failed;

    private ArmorGlsl() {}

    @Override
    public String get(Identifier id, ShaderType type) {
        if (id == null || !id.equals(SHADER)) return Minecraft.getInstance().getShaderManager().getShader(id, type);
        if (failed) return null;
        if (vertex == null) {
            String v = read("/mineux/shaders/armor.vsh");
            String f = read("/mineux/shaders/armor.fsh");
            if (v == null || f == null) {
                failed = true;
                return null;
            }
            vertex = String.join("", new Includes().process(v));
            fragment = String.join("", new Includes().process(f));
        }
        return type == ShaderType.VERTEX ? vertex : fragment;
    }

    private static String read(String resource) {
        try (InputStream in = open(resource)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static InputStream open(String resource) {
        return ArmorGlsl.class.getResourceAsStream(resource);
    }

    /** The game's own includes, each once, from the game's resources. */
    private static final class Includes extends GlslPreprocessor {
        private final Set<Identifier> seen = new HashSet<>();

        @Override
        public String applyImport(boolean isRelative, String path) {
            if (isRelative) return "#error no relative imports in the client's shaders";
            Identifier id = Identifier.parse(path).withPrefix("shaders/include/");
            if (!seen.add(id)) return null;
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isEmpty()) return "#error no " + id;
            try (Reader reader = resource.get().openAsReader()) {
                StringBuilder out = new StringBuilder();
                char[] buf = new char[4096];
                for (int n; (n = reader.read(buf)) > 0; ) out.append(buf, 0, n);
                return out.toString();
            } catch (Exception e) {
                return "#error " + e.getMessage();
            }
        }
    }
}
