package com.mineux.visuals.world;

import com.mineux.visuals.ui.Theme;

import java.util.Arrays;
import java.util.List;

/**
 * The sky shaders' presets: each one's GLSL file under {@code /mineux/shaders/sky/} (named
 * as the preset is), its own two colours, and the colour its horizon fades to -- which is also
 * the colour the world's fog takes on under it.
 */
final class SkyLook {

    private static final List<String> NAMES = Arrays.asList(
        "aurora", "galaxy", "blackhole", "thunderstorm", "nebula", "synthwave", "bloodmoon", "dreamscape", "glowlines",
        "fog", "sunset", "abyss", "glacier", "lagoon", "deepsea", "molten", "prismgrid", "flowlines", "cells", "halorings",
        "neonveil", "plasmastorm", "crystallattice", "quantumtunnel", "liquidlight", "mandala", "etherwaves");

    private static final int[] FIRST = {
        0x2BFF88, 0xFFC98A, 0xFFD9A0, 0xC9D6FF, 0xFF3D9A, 0xFF2A6D, 0xFF3B2F, 0x7EA7FF, 0x000000,
        0xC8D4FF, 0xFF8A3D, 0x7FE9FF, 0xBFF6FF, 0xFFF1C0, 0x3DFFD0, 0xFF8A20, 0x45F0FF, 0x7CFFB2, 0xFF5CA8, 0xFFC36B,
        0x2BFFD5, 0x6FA8FF, 0x7FF6FF, 0x8AFFF0, 0xFFB86B, 0xFFD36B, 0x5CFFB0};
    private static final int[] SECOND = {
        0x8A4DFF, 0x8FA2FF, 0xFF6A1F, 0x5A6380, 0x2EC5FF, 0x05D9E8, 0x5A0A0A, 0xFFB3C7, 0x000000,
        0x5C6672, 0x2C3E7A, 0x04345E, 0x0A2F4A, 0x0FA3B8, 0x020A1A, 0x3A0A04, 0xB45CFF, 0x3D7BFF, 0x5CC8FF, 0x8A5CFF,
        0xFF3DCB, 0xC03DFF, 0x6A5CFF, 0xFF5CA8, 0x5CE1FF, 0xFF4F9A, 0x5C7CFF};

    private SkyLook() {}

    static int index(String name) {
        return NAMES.indexOf(name);
    }

    static String file(int preset) {
        return NAMES.get(preset);
    }

    /** The preset's two colours, 0xRRGGBB each, as {first, second}. */
    static int[] natural(int preset) {
        if (preset == 8) return new int[] {Theme.GOLD & 0xFFFFFF, Theme.GREEN & 0xFFFFFF};
        return new int[] {FIRST[preset], SECOND[preset]};
    }

    /** The colour, 0 to 1 a channel, the horizon fades to under {@code preset} with these colours. */
    static float[] horizon(int preset, float[] c1, float[] c2) {
        if (preset == 1) return mix(rgb(0x0C0F1C), c2, 0.1F);
        if (preset == 2) return mix(rgb(0x0A0A10), c2, 0.06F);
        if (preset == 3) return scale(c2, 0.45F);
        if (preset == 4) return mix(rgb(0x0C0A16), mix(c1, c2, 0.5F), 0.08F);
        if (preset == 5) return scale(c1, 0.8F);
        if (preset == 6) return scale(c2, 0.8F);
        if (preset == 7) return mix(c2, rgb(0xFFE0C0), 0.25F);
        if (preset == 8) return mix(rgb(0x07080F), mix(c1, c2, 0.5F), 0.14F);
        if (preset == 9) return scale(c2, 0.78F);
        if (preset == 10) return scale(mix(c1, rgb(0xFFC9A0), 0.35F), 0.85F);
        if (preset == 11) return scale(mix(c2, c1, 0.12F), 0.65F);
        if (preset == 12) return scale(mix(c2, c1, 0.1F), 0.6F);
        if (preset == 13) return scale(c2, 0.8F);
        if (preset == 14) return scale(c2, 1.2F);
        if (preset == 15) return scale(mix(c2, c1, 0.15F), 0.7F);
        if (preset >= 16) return mix(rgb(0x05060B), mix(c1, c2, 0.5F), 0.08F);
        return mix(rgb(0x0B1622), c1, 0.08F);
    }

    static float[] rgb(int c) {
        return new float[] {World.red(c), World.green(c), World.blue(c)};
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private static float[] scale(float[] a, float s) {
        return new float[] {a[0] * s, a[1] * s, a[2] * s};
    }
}
