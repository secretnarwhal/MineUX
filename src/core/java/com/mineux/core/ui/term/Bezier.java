package com.mineux.core.ui.term;

/**
 * A cubic bezier timing curve, as {@code hyprland.conf}'s {@code bezier} lines give them: from
 * (0,0) to (1,1) through two control points. Time goes in along x; how far the animation has
 * got comes out along y.
 */
final class Bezier {

    /** Hyprland's own {@code default}. */
    static final float[] DEFAULT = {0f, 0.75f, 0.15f, 1f};
    static final float[] LINEAR = {0f, 0f, 1f, 1f};

    private Bezier() {}

    /** The curve {x0, y0, x1, y1} at time {@code t}, 0 to 1. */
    static float at(float[] p, float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float x0 = p[0], y0 = p[1], x1 = p[2], y1 = p[3];
        if (x0 == y0 && x1 == y1) return t;

        // Newton's method for the u where x(u) = t, falling back to bisection where it stalls.
        float u = t;
        for (int i = 0; i < 6; i++) {
            float x = coord(x0, x1, u) - t;
            if (Math.abs(x) < 1e-4f) return coord(y0, y1, u);
            float dx = slope(x0, x1, u);
            if (Math.abs(dx) < 1e-5f) break;
            u -= x / dx;
            if (u < 0f || u > 1f) break;
        }
        float lo = 0f, hi = 1f;
        u = t;
        for (int i = 0; i < 24; i++) {
            float x = coord(x0, x1, u);
            if (Math.abs(x - t) < 1e-4f) break;
            if (x < t) lo = u;
            else hi = u;
            u = (lo + hi) / 2f;
        }
        return coord(y0, y1, u);
    }

    /** One coordinate of the curve at {@code u}, the end points being 0 and 1. */
    private static float coord(float a, float b, float u) {
        float v = 1f - u;
        return 3f * v * v * u * a + 3f * v * u * u * b + u * u * u;
    }

    private static float slope(float a, float b, float u) {
        float v = 1f - u;
        return 3f * v * v * a + 6f * v * u * (b - a) + 3f * u * u * (1f - b);
    }
}
