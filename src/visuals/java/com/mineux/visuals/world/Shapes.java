package com.mineux.visuals.world;

import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

/**
 * The few shapes the crystal and totem effects are built from, as gizmos: lines that glow, rings
 * in any plane, filled triangles and points. Whether they show through walls is set once for a
 * whole effect with {@link #walls}.
 */
final class Shapes {

    private static boolean walls;

    private Shapes() {}

    static void walls(boolean through) {
        walls = through;
    }

    private static void place(GizmoProperties drawn) {
        if (walls) drawn.setAlwaysOnTop();
    }

    static void line(Vec3 a, Vec3 b, int argb, float width) {
        if ((argb >>> 24) == 0) return;
        place(Gizmos.line(a, b, argb, width));
    }

    /** A line with a soft wider one under it, the wider one {@code glow} as strong (0 for none). */
    static void glowLine(Vec3 a, Vec3 b, int rgb, float alpha, float width, float glow) {
        if (glow > 0.01F) line(a, b, World.argb(rgb, alpha * 0.22F * glow), width * 3.5F);
        line(a, b, World.argb(rgb, alpha), width);
    }

    static void point(Vec3 at, int argb, float pixels) {
        if ((argb >>> 24) == 0) return;
        place(Gizmos.point(at, argb, pixels));
    }

    static void triangle(Vec3 a, Vec3 b, Vec3 c, int argb) {
        if ((argb >>> 24) == 0) return;
        place(Gizmos.rect(a, b, c, c, GizmoStyle.fill(argb)));
    }

    /**
     * A ring round {@code centre} of {@code radius} in the plane of {@code u} and {@code v} (two
     * unit vectors at right angles), started {@code phase} radians round, in {@code segments}.
     */
    static void ring(Vec3 centre, double radius, Vec3 u, Vec3 v, double phase, int segments, int rgb, float alpha,
                     float width, float glow) {
        Vec3 last = null;
        for (int i = 0; i <= segments; i++) {
            double a = phase + i * Math.PI * 2.0 / segments;
            Vec3 at = centre.add(u.scale(Math.cos(a) * radius)).add(v.scale(Math.sin(a) * radius));
            if (last != null) glowLine(last, at, rgb, alpha, width, glow);
            last = at;
        }
    }

    /** A flat ring on the ground, in the world's own plane. */
    static void groundRing(Vec3 centre, double radius, int rgb, float alpha, float width, float glow) {
        ring(centre, radius, new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0), 0.0, 40, rgb, alpha, width, glow);
    }

    /** 0 to 1 smoothly between {@code a} and {@code b}. */
    static float smooth(float a, float b, float x) {
        float t = Math.max(0.0F, Math.min(1.0F, (x - a) / (b - a)));
        return t * t * (3.0F - 2.0F * t);
    }

    static float clamp01(float x) {
        return Math.max(0.0F, Math.min(1.0F, x));
    }

    /** Overshoots past 1 and settles back, for things that pop into being. */
    static float outBack(float x) {
        float t = clamp01(x) - 1.0F;
        float c = 1.7F;
        return 1.0F + (c + 1.0F) * t * t * t + c * t * t;
    }
}
