package com.mineux.core.gpu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This client's GLSL made fit for both of the game's backends without touching what it draws.
 * <p>
 * The shaders were written for raw OpenGL, with each number a loose {@code uniform float uTime;}.
 * Vulkan has no loose numbers, only blocks, so the game's pipelines take theirs in
 * {@code layout(std140) uniform Name { ... }} blocks. This takes every loose non-sampler uniform
 * out of a program's two sources and puts them, in the order first met, into one block
 * ({@link #BLOCK}) at the top of both -- members of a block with no instance name are read by
 * their own names, so the shader bodies stay exactly as written. Samplers stay loose; both
 * backends bind those by name.
 * <p>
 * The block's std140 layout is worked out here as well ({@link Program#offset}), for
 * {@link Uniforms} to write each number where the GPU will read it.
 */
public final class Glsl {

    /** The one block every program's numbers go in. */
    public static final String BLOCK = "MuxParams";

    private static final Pattern UNIFORM = Pattern.compile("^\\s*uniform\\s+(\\w+)\\s+([\\w\\s,]+?)\\s*;\\s*$");

    private Glsl() {}

    /** A program's sources as the game compiles them, and its block's layout. */
    public static final class Program {
        public final String vertex;
        public final String fragment;
        /** The block's members in order, and each one's type. */
        public final List<String> names;
        private final Map<String, String> types;
        private final Map<String, Integer> offsets;
        /** The loose samplers, in order met. */
        public final List<String> samplers;
        /** The block's size in bytes, a whole number of 16; 0 when the program has no numbers. */
        public final int size;

        Program(String vertex, String fragment, List<String> names, Map<String, String> types,
                Map<String, Integer> offsets, List<String> samplers, int size) {
            this.vertex = vertex;
            this.fragment = fragment;
            this.names = Collections.unmodifiableList(names);
            this.types = types;
            this.offsets = offsets;
            this.samplers = Collections.unmodifiableList(samplers);
            this.size = size;
        }

        /** Where a member starts in the block, bytes; -1 when the program has no such number. */
        public int offset(String name) {
            Integer at = offsets.get(name);
            return at == null ? 0 - 1 : at.intValue();
        }

        /** A member's GLSL type ({@code float}, {@code vec3}, {@code mat4}...), or null. */
        public String type(String name) {
            return types.get(name);
        }
    }

    /** Rewrites a vertex and fragment source pair; throws on a uniform it cannot lay out. */
    public static Program of(String vertex, String fragment) {
        List<String> names = new ArrayList<>();
        Map<String, String> types = new HashMap<>();
        List<String> samplers = new ArrayList<>();
        String v = strip(vertex, names, types, samplers);
        String f = strip(fragment, names, types, samplers);
        Map<String, Integer> offsets = new HashMap<>();
        int end = 0;
        for (String name : names) {
            String type = types.get(name);
            int at = roundUp(end, align(type));
            offsets.put(name, at);
            end = at + size(type);
        }
        int total = roundUp(end, 16);
        if (!names.isEmpty()) {
            String block = block(names, types);
            v = insert(v, block);
            f = insert(f, block);
        }
        return new Program(v, f, names, types, offsets, samplers, total);
    }

    /** The source with its loose numbers taken out (each line left blank, so line numbers hold). */
    private static String strip(String source, List<String> names, Map<String, String> types, List<String> samplers) {
        String[] lines = source.split("\n", 0 - 1);
        StringBuilder out = new StringBuilder(source.length() + 64);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) out.append('\n');
            String line = lines[i];
            int comment = line.indexOf("//");
            String code = comment < 0 ? line : line.substring(0, comment);
            Matcher m = UNIFORM.matcher(code);
            if (!m.matches()) {
                out.append(line);
                continue;
            }
            String type = m.group(1);
            if (type.startsWith("sampler")) {
                for (String name : m.group(2).split(",")) add(samplers, name.strip());
                out.append(line);
                continue;
            }
            size(type);
            for (String raw : m.group(2).split(",")) {
                String name = raw.strip();
                String had = types.get(name);
                if (had == null) {
                    names.add(name);
                    types.put(name, type);
                } else if (!Objects.equals(had, type)) {
                    throw new IllegalArgumentException("uniform " + name + " is a " + had + " in one source and a " + type + " in the other");
                }
            }
        }
        return out.toString();
    }

    private static void add(List<String> list, String name) {
        if (!list.contains(name)) list.add(name);
    }

    private static String block(List<String> names, Map<String, String> types) {
        StringBuilder b = new StringBuilder("layout(std140) uniform ").append(BLOCK).append(" {\n");
        for (String name : names) b.append("    ").append(types.get(name)).append(' ').append(name).append(";\n");
        return b.append("};\n").toString();
    }

    /** The block straight after the {@code #version} line (or first, when there is none). */
    private static String insert(String source, String block) {
        int version = source.indexOf("#version");
        if (version < 0) return block + source;
        int eol = source.indexOf('\n', version);
        if (eol < 0) return source + "\n" + block;
        return source.substring(0, eol + 1) + block + source.substring(eol + 1);
    }

    // ---- std140 -----------------------------------------------------------------------------------

    /** A member's base alignment under std140, bytes. */
    static int align(String type) {
        if (Objects.equals(type, "vec2") || Objects.equals(type, "ivec2")) return 8;
        if (Objects.equals(type, "vec3") || Objects.equals(type, "vec4") || Objects.equals(type, "ivec3")
            || Objects.equals(type, "ivec4") || Objects.equals(type, "mat3") || Objects.equals(type, "mat4")) return 16;
        return 4;
    }

    /** A member's size under std140, bytes (a mat3 is three vec4 columns); throws on a type it does not know. */
    static int size(String type) {
        if (Objects.equals(type, "float") || Objects.equals(type, "int") || Objects.equals(type, "uint")
            || Objects.equals(type, "bool")) return 4;
        if (Objects.equals(type, "vec2") || Objects.equals(type, "ivec2")) return 8;
        if (Objects.equals(type, "vec3") || Objects.equals(type, "ivec3")) return 12;
        if (Objects.equals(type, "vec4") || Objects.equals(type, "ivec4")) return 16;
        if (Objects.equals(type, "mat3")) return 48;
        if (Objects.equals(type, "mat4")) return 64;
        throw new IllegalArgumentException("no std140 layout for a uniform " + type);
    }

    static int roundUp(int value, int to) {
        return (value + to - 1) / to * to;
    }
}
