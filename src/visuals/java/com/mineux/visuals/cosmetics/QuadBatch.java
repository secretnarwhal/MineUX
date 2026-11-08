package com.mineux.visuals.cosmetics;

import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

/**
 * One frame's quads of one cosmetic, handed to the game as a single gizmo: the world-space gizmo
 * pass draws them translucent, two-sided, sorted back to front and depth-tested against the
 * world (or over it, {@link #submit(boolean)} with walls). A gizmo has one colour a quad, so a
 * quad gets the mean of its corners' colours -- the shapes are finely cut, which keeps their
 * gradients.
 * <p>
 * A {@link #tilt} turns everything about a pivot before it is kept: the wings follow your body
 * over when you swim or glide that way.
 */
public final class QuadBatch implements Gizmo, CosmeticSink {

    /** More than any cosmetic here makes; past it a frame's quads are dropped, never the game. */
    private static final int MAX_QUADS = 24000;

    private double[] xyz = new double[12 * 256];
    private int[] colors = new int[256];
    private int count;

    private double[] tilt;
    private double tx;
    private double ty;
    private double tz;

    /** Turns every quad from now on by the 3x3 row-major {@code m} about (x, y, z); null for none. */
    public void tilt(double[] m, double x, double y, double z) {
        this.tilt = m;
        this.tx = x;
        this.ty = y;
        this.tz = z;
    }

    public int size() {
        return count;
    }

    @Override
    public void quad(double[] a, double[] b, double[] c, double[] d, int ca, int cb, int cc, int cd) {
        if (count >= MAX_QUADS) return;
        int color = mean(ca, cb, cc, cd);
        if ((color >>> 24) < 2) return;
        if (count == colors.length) grow();
        int o = count * 12;
        put(o, a);
        put(o + 3, b);
        put(o + 6, c);
        put(o + 9, d);
        colors[count] = color;
        count++;
    }

    private void put(int o, double[] p) {
        double[] m = tilt;
        if (m == null) {
            xyz[o] = p[0];
            xyz[o + 1] = p[1];
            xyz[o + 2] = p[2];
            return;
        }
        double x = p[0] - tx;
        double y = p[1] - ty;
        double z = p[2] - tz;
        xyz[o] = tx + m[0] * x + m[1] * y + m[2] * z;
        xyz[o + 1] = ty + m[3] * x + m[4] * y + m[5] * z;
        xyz[o + 2] = tz + m[6] * x + m[7] * y + m[8] * z;
    }

    private void grow() {
        double[] moreXyz = new double[xyz.length * 2];
        System.arraycopy(xyz, 0, moreXyz, 0, xyz.length);
        int[] moreColors = new int[colors.length * 2];
        System.arraycopy(colors, 0, moreColors, 0, colors.length);
        xyz = moreXyz;
        colors = moreColors;
    }

    /** Hands the quads to this frame's gizmos; over the world when {@code walls}. */
    public void submit(boolean walls) {
        if (count == 0) return;
        if (walls) Gizmos.addGizmo(this).setAlwaysOnTop();
        else Gizmos.addGizmo(this);
    }

    @Override
    public void emit(GizmoPrimitives primitives, float alphaMultiplier) {
        double[] p = xyz;
        for (int i = 0; i < count; i++) {
            int o = i * 12;
            int color = colors[i];
            if (alphaMultiplier < 0.999F) color = scaleAlpha(color, alphaMultiplier);
            primitives.addQuad(new Vec3(p[o], p[o + 1], p[o + 2]), new Vec3(p[o + 3], p[o + 4], p[o + 5]),
                new Vec3(p[o + 6], p[o + 7], p[o + 8]), new Vec3(p[o + 9], p[o + 10], p[o + 11]), color);
        }
    }

    private static int scaleAlpha(int color, float k) {
        int a = (int) ((color >>> 24) * k);
        return a << 24 | color & 0xFFFFFF;
    }

    static int mean(int c0, int c1, int c2, int c3) {
        int a = ((c0 >>> 24) + (c1 >>> 24) + (c2 >>> 24) + (c3 >>> 24)) >> 2;
        int r = ((c0 >> 16 & 0xFF) + (c1 >> 16 & 0xFF) + (c2 >> 16 & 0xFF) + (c3 >> 16 & 0xFF)) >> 2;
        int g = ((c0 >> 8 & 0xFF) + (c1 >> 8 & 0xFF) + (c2 >> 8 & 0xFF) + (c3 >> 8 & 0xFF)) >> 2;
        int b = ((c0 & 0xFF) + (c1 & 0xFF) + (c2 & 0xFF) + (c3 & 0xFF)) >> 2;
        return a << 24 | r << 16 | g << 8 | b;
    }
}
