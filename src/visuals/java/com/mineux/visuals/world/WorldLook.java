package com.mineux.visuals.world;

import com.mineux.visuals.ui.Theme;

import java.util.Arrays;
import java.util.List;

/**
 * The world shaders' presets: each a set of strengths -- sun, shadows, god rays, fog, bloom,
 * exposure, contrast, saturation, warmth, vignette -- and three colours: the sun's, the fog's
 * and the shade's. One flat row a preset, {@link #FIELDS} long.
 */
final class WorldLook {

    private static final List<String> NAMES = Arrays.asList(
        "realistic", "goldenhour", "misty", "cinematic", "dreamy", "stormy", "neon", "custom");

    static final int SUN = 0;
    static final int SHADOWS = 1;
    static final int RAYS = 2;
    static final int FOG = 3;
    static final int BLOOM = 4;
    static final int EXPOSURE = 5;
    static final int CONTRAST = 6;
    static final int SATURATION = 7;
    static final int WARMTH = 8;
    static final int VIGNETTE = 9;
    private static final int FIELDS = 10;

    private static final float[] LOOKS = {
        1.0F, 0.8F, 1.0F, 0.8F, 0.7F, 1.0F, 1.04F, 1.05F, 0.12F, 0.3F,
        1.25F, 0.9F, 1.35F, 0.55F, 0.9F, 1.05F, 1.06F, 1.12F, 0.55F, 0.35F,
        0.75F, 0.45F, 1.1F, 1.8F, 0.6F, 1.02F, 0.96F, 0.92F, 0.0F - 0.05F, 0.25F,
        1.05F, 1.0F, 0.9F, 0.7F, 0.75F, 0.98F, 1.14F, 0.95F, 0.12F, 0.55F,
        0.9F, 0.5F, 1.2F, 0.9F, 1.6F, 1.08F, 0.94F, 1.18F, 0.2F, 0.3F,
        0.55F, 0.35F, 0.4F, 1.4F, 0.4F, 0.9F, 1.08F, 0.75F, 0.0F - 0.35F, 0.5F,
        0.8F, 0.6F, 0.8F, 0.9F, 1.8F, 1.05F, 1.1F, 1.3F, 0.0F - 0.2F, 0.45F,
        1.0F, 0.8F, 1.0F, 1.0F, 0.8F, 1.0F, 1.04F, 1.05F, 0.1F, 0.3F};

    /** Sun, fog and shade colours, three a preset; the neon preset's first two are the client's own. */
    private static final int[] COLOURS = {
        0xFFE2B0, 0xB8C8DC, 0x5068A0,
        0xFFB061, 0xFFC890, 0x5A6CA8,
        0xFFE7C4, 0xC9D6E6, 0x6E7F99,
        0xFFD39A, 0x9AA7B8, 0x3B5878,
        0xFFD6F0, 0xE7C8FF, 0x7A6AC0,
        0xD8E2F0, 0x7D8794, 0x3F4A5C,
        0x000000, 0x000000, 0x2A1E5C,
        0xFFE2B0, 0xB8C8DC, 0x5068A0};

    private WorldLook() {}

    static int index(String name) {
        return NAMES.indexOf(name);
    }

    static float get(int preset, int field) {
        return LOOKS[preset * FIELDS + field];
    }

    /** {sun, fog, shade}, 0xRRGGBB each. */
    static int[] colours(int preset) {
        int[] out = {COLOURS[preset * 3], COLOURS[preset * 3 + 1], COLOURS[preset * 3 + 2]};
        if (preset == 6) {
            out[0] = Theme.GOLD & 0xFFFFFF;
            out[1] = Theme.GREEN & 0xFFFFFF;
        }
        return out;
    }
}
