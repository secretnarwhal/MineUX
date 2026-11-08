package com.mineux.visuals.trail;

import net.minecraft.util.Mth;

/** ARGB packing shared by {@link TrailRenderer} and {@link FakeCursorRenderer}. */
final class ColorPack {
    private ColorPack() {}

    static int packArgb(float alpha, float r, float g, float b) {
        int a = Math.round(Mth.clamp(alpha, 0.0f, 1.0f) * 255.0f);
        int ri = Math.round(Mth.clamp(r, 0.0f, 1.0f) * 255.0f);
        int gi = Math.round(Mth.clamp(g, 0.0f, 1.0f) * 255.0f);
        int bi = Math.round(Mth.clamp(b, 0.0f, 1.0f) * 255.0f);
        return (a << 24) | (ri << 16) | (gi << 8) | bi;
    }
}
