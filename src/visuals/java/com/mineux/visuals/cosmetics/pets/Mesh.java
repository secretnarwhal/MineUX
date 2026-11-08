package com.mineux.visuals.cosmetics.pets;

import com.mineux.visuals.cosmetics.CosmeticSink;
import com.mineux.visuals.cosmetics.Prim;

/**
 * A pet part's quads: positions three floats a corner, a colour a corner, four corners a quad.
 * The body's quads are wound one way and its outline hull's the other, so dropping the quads
 * that face away from the camera shows the body in front and the hull only as a rim behind it.
 * The game's gizmo quads are drawn two-sided, so that culling happens here.
 */
public final class Mesh {
   public static final Mesh EMPTY = new Mesh(new float[0], new int[0]);
   final int count;
   final float[] pos;
   final int[] col;

   Mesh(float[] pos, int[] col) {
      this.count = col.length;
      this.pos = pos;
      this.col = col;
   }

   /**
    * Hands the quads to {@code sink} through {@code m} (a 3x4 row-major matrix into
    * camera-relative space), each corner moved back by {@code cam} into the world.
    */
   public void emit(CosmeticSink sink, double[] m, double[] cam, float alpha) {
      if (this.count < 4) return;
      double det = m[0] * (m[5] * m[10] - m[6] * m[9]) - m[1] * (m[4] * m[10] - m[6] * m[8]) + m[2] * (m[4] * m[9] - m[5] * m[8]);
      float[] p = this.pos;
      int[] c = this.col;
      double[][] q = new double[4][];
      int[] qc = new int[4];
      for (int i = 0; i + 3 < this.count; i += 4) {
         for (int k = 0; k < 4; k++) {
            int o = (i + k) * 3;
            q[k] = corner(m, p[o], p[o + 1], p[o + 2]);
            qc[k] = fade(c[i + k], alpha);
         }
         if (!facing(q, det)) continue;
         sink.quad(shift(Prim.row(q, 0), cam), shift(Prim.row(q, 1), cam), shift(Prim.row(q, 2), cam), shift(Prim.row(q, 3), cam), qc[0], qc[1], qc[2], qc[3]);
      }
   }

   private static double[] corner(double[] m, double x, double y, double z) {
      return new double[] {m[0] * x + m[1] * y + m[2] * z + m[3], m[4] * x + m[5] * y + m[6] * z + m[7], m[8] * x + m[9] * y + m[10] * z + m[11]};
   }

   /** Whether the quad faces the camera (at the origin), by its winding and the matrix's handedness. */
   static boolean facing(double[][] q, double det) {
      double ax = Prim.row(q, 2)[0] - Prim.row(q, 0)[0];
      double ay = Prim.row(q, 2)[1] - Prim.row(q, 0)[1];
      double az = Prim.row(q, 2)[2] - Prim.row(q, 0)[2];
      double bx = Prim.row(q, 3)[0] - Prim.row(q, 1)[0];
      double by = Prim.row(q, 3)[1] - Prim.row(q, 1)[1];
      double bz = Prim.row(q, 3)[2] - Prim.row(q, 1)[2];
      double nx = ay * bz - az * by;
      double ny = az * bx - ax * bz;
      double nz = ax * by - ay * bx;
      double dot = nx * Prim.row(q, 0)[0] + ny * Prim.row(q, 0)[1] + nz * Prim.row(q, 0)[2];
      return dot * det > 0.0;
   }

   private static double[] shift(double[] p, double[] cam) {
      return new double[] {p[0] + cam[0], p[1] + cam[1], p[2] + cam[2]};
   }

   private static int fade(int argb, float alpha) {
      if (alpha >= 0.999F) return argb;
      return (int) ((argb >>> 24 & 0xFF) * alpha) << 24 | argb & 0xFFFFFF;
   }

   public int vertexCount() {
      return this.count;
   }
}
