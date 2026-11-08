package com.mineux.visuals.self.model;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** A built rig part: quads as positions and one colour a corner, emitted through a matrix. */
public final class Mesh {

    static final Mesh EMPTY = new Mesh(new float[0], new int[0]);

    private final int count;
    private final float[] pos;
    private final int[] col;

    Mesh(float[] pos, int[] col) {
        this.count = col.length;
        this.pos = pos;
        this.col = col;
    }

    /** Every corner through {@code m}, its alpha scaled by {@code alpha}. */
    void emit(VertexConsumer vc, double[] m, float alpha) {
        if (count == 0) return;
        float m0 = (float) m[0], m1 = (float) m[1], m2 = (float) m[2], m3 = (float) m[3];
        float m4 = (float) m[4], m5 = (float) m[5], m6 = (float) m[6], m7 = (float) m[7];
        float m8 = (float) m[8], m9 = (float) m[9], m10 = (float) m[10], m11 = (float) m[11];
        boolean fade = alpha < 0.999F;
        for (int i = 0, k = 0; i < count; i++, k += 3) {
            float x = pos[k];
            float y = pos[k + 1];
            float z = pos[k + 2];
            int argb = col[i];
            if (fade) argb = (int) ((argb >>> 24) * alpha) << 24 | argb & 0xFFFFFF;
            vc.addVertex(m0 * x + m1 * y + m2 * z + m3, m4 * x + m5 * y + m6 * z + m7, m8 * x + m9 * y + m10 * z + m11).setColor(argb);
        }
    }
}
