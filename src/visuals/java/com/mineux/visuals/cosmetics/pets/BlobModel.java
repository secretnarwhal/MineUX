package com.mineux.visuals.cosmetics.pets;

import com.mineux.visuals.cosmetics.CosmeticSink;
import com.mineux.visuals.cosmetics.Prim;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class BlobModel implements PetModel {
   protected static final int BLACK = -16119286;
   protected static final int WHITE = -1;
   protected static final double T = 0.022;
   protected static final double T_SMALL = 0.012;
   private BlobModel.Blob[] blobs;
   private static final double[][] partWorld = new double[7][12];
   private static double[] keys = new double[64];
   private static int[] order = new int[64];

   protected abstract void build(BlobModel.Kit var1);

   private BlobModel.Blob[] blobs() {
      BlobModel.Blob[] b = this.blobs;
      if (b != null) {
         return b;
      }

      BlobModel.Kit k = new BlobModel.Kit();
      this.build(k);
      return this.blobs = k.out.toArray(new BlobModel.Blob[0]);
   }

   @Override
   public void draw(CosmeticSink sink, double[] world, double[] cam, double[][] rig, float alpha) {
      BlobModel.Blob[] bs = this.blobs();
      int n = bs.length;
      if (keys.length < n) {
         keys = new double[n * 2];
         order = new int[n * 2];
      }

      for (int p = 0; p < 7; p++) {
         Mat.mul(world, Prim.row(rig, p), Prim.row(partWorld, p));
      }

      double s = Math.sqrt(world[0] * world[0] + world[4] * world[4] + world[8] * world[8]);
      int i = 0;

      while (i < n) {
         BlobModel.Blob b = bs[i];
         double[] m = Prim.row(partWorld, b.part);
         double x = Mat.px(m, b.cx, b.cy, b.cz);
         double y = Mat.py(m, b.cx, b.cy, b.cz);
         double z = Mat.pz(m, b.cx, b.cy, b.cz);
         double key = Math.sqrt(x * x + y * y + z * z) - b.bias * s;

         int j;
         for (j = i - 1; j >= 0 && keys[j] < key; j--) {
            keys[j + 1] = keys[j];
            order[j + 1] = order[j];
         }

         keys[j + 1] = key;
         order[j + 1] = i++;
      }

      for (int ix = 0; ix < n; ix++) {
         BlobModel.Blob b = bs[order[ix]];
         b.mesh.emit(sink, Prim.row(partWorld, b.part), cam, alpha);
      }
   }

   public abstract static class Biped extends BlobModel {
      protected double hip = 0.27;
      protected double legX = 0.1;
      protected double armX = 0.24;
      protected double shoulder = 0.3;
      protected double neck = 0.4;
      protected double roll = 0.25;
      protected double lean = 0.4;
      protected double strideAmt = 0.6;
      protected double armSwing = 1.0;
      protected double waddle = 0.0;
      protected double skip = 0.0;

      private void arm(double[] torso, double[] a, double u, double r, double angle) {
         Mat.copy(torso, a);
         Mat.translate(a, u, this.shoulder, 0.0);
         Mat.rotZ(a, r);
         Mat.rotX(a, angle);
      }

      @Override
      public void pose(double walkPhase, double walkAmp, boolean sneaking, double headYaw, double headPitch, double time, double idle, double[][] out) {
         double sw = Math.sin(walkPhase);
         double stride = sw * walkAmp * this.strideAmt;
         double up = Math.abs(sw) * walkAmp * this.skip + Math.sin(time * 2.2) * 0.006;
         double ln = sneaking ? this.lean : 0.0;
         double[] torso = Prim.row(out, 0);
         Mat.setTranslation(torso, 0.0, this.hip + up, 0.0);
         Mat.rotX(torso, ln);
         Mat.rotZ(torso, sw * walkAmp * this.waddle);
         double[] head = Prim.row(out, 1);
         Mat.setTranslation(head, 0.0, this.hip + up + this.neck * Math.cos(ln), this.neck * Math.sin(ln));
         Mat.rotY(head, headYaw);
         Mat.rotX(head, headPitch);
         Mat.rotZ(head, -sw * walkAmp * this.waddle * 0.6);
         this.arm(torso, Prim.row(out, 2), this.armX, this.roll, -stride * this.armSwing);
         this.arm(torso, Prim.row(out, 3), -this.armX, -this.roll, stride * this.armSwing);
         Mat.setTranslation(Prim.row(out, 4), this.legX, this.hip, 0.0);
         Mat.rotX(Prim.row(out, 4), stride);
         Mat.setTranslation(Prim.row(out, 5), -this.legX, this.hip, 0.0);
         Mat.rotX(Prim.row(out, 5), -stride);
         Mat.identity(Prim.row(out, 6));
      }
   }

   static final class Blob {
      final int part;
      final Mesh mesh;
      final double cx;
      final double cy;
      final double cz;
      final double bias;

      Blob(int part, Mesh mesh, double cx, double cy, double cz, double bias) {
         this.part = part;
         this.mesh = mesh;
         this.cx = cx;
         this.cy = cy;
         this.cz = cz;
         this.bias = bias;
      }
   }

   protected static final class Kit {
      private final MeshBuilder b = new MeshBuilder();
      private final List<BlobModel.Blob> out = new ArrayList<>();
      private final double[] tf = Mat.identity();
      private double bias = 0.0;

      public BlobModel.Kit ell(int part, double outline, double x, double y, double z, double rx, double ry, double rz, int c) {
         int lon = rx + rz > 0.3 ? 20 : (rx + rz > 0.12 ? 14 : 10);
         int lat = Math.max(6, lon * 2 / 3);
         return this.blob(outline, mb -> mb.ellipsoid(x, y, z, rx, ry, rz, lon, lat, c), part);
      }

      Kit() {
         this.b.outlineColor(-16119286);
      }

      public BlobModel.Kit t(double x, double y, double z) {
         Mat.translate(this.tf, x, y, z);
         return this;
      }

      public BlobModel.Kit at(double x, double y, double z) {
         Mat.setTranslation(this.tf, x, y, z);
         this.bias = 0.0;
         return this;
      }

      public BlobModel.Kit rx(double a) {
         Mat.rotX(this.tf, a);
         return this;
      }

      public BlobModel.Kit ell2(int pa, int pb, double outline, double x, double y, double z, double rx, double ry, double rz, int c) {
         int lon = rx + rz > 0.3 ? 20 : (rx + rz > 0.12 ? 14 : 10);
         int lat = Math.max(6, lon * 2 / 3);
         return this.blob(outline, mb -> mb.ellipsoid(x, y, z, rx, ry, rz, lon, lat, c), pa, pb);
      }

      public BlobModel.Kit blob(double outline, Consumer<MeshBuilder> shape, int... parts) {
         this.b.reset();
         this.b.setTransform(this.tf);
         if (outline > 0.0) {
            this.b.outlined(outline, () -> shape.accept(this.b));
         } else {
            shape.accept(this.b);
         }

         Mesh m = this.b.build();
         if (m.count == 0) {
            return this;
         }

         double x0 = 1.0E9;
         double y0 = 1.0E9;
         double z0 = 1.0E9;
         double x1 = -1.0E9;
         double y1 = -1.0E9;
         double z1 = -1.0E9;

         for (int i = 0; i < m.count; i++) {
            double x = m.pos[i * 3];
            double y = m.pos[i * 3 + 1];
            double z = m.pos[i * 3 + 2];
            x0 = Math.min(x0, x);
            y0 = Math.min(y0, y);
            z0 = Math.min(z0, z);
            x1 = Math.max(x1, x);
            y1 = Math.max(y1, y);
            z1 = Math.max(z1, z);
         }

         for (int p : parts) {
            this.out.add(new BlobModel.Blob(p, m, (x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0, this.bias));
         }

         return this;
      }

      public BlobModel.Kit ball(int part, double outline, double x, double y, double z, double r, int c) {
         return this.ell(part, outline, x, y, z, r, r, r, c);
      }

      public BlobModel.Kit cyl(int part, double outline, double y0, double y1, double r, int c) {
         return this.blob(outline, mb -> mb.cylinder(y0, y1, r, 12, c), part);
      }

      public BlobModel.Kit rz(double a) {
         Mat.rotZ(this.tf, a);
         return this;
      }

      public BlobModel.Kit ry(double a) {
         Mat.rotY(this.tf, a);
         return this;
      }

      public BlobModel.Kit ring(int part, double outline, double y, double rx, double rz, double halfHeight, double thick, int n, int c) {
         double[] base = (double[])this.tf.clone();
         double seg = Math.PI * (rx + rz) / n * 0.75;

         for (int i = 0; i < n; i++) {
            double a = (Math.PI * 2) * i / n;
            Mat.copy(base, this.tf);
            Mat.translate(this.tf, rx * Math.cos(a), y, rz * Math.sin(a));
            Mat.rotY(this.tf, -a);
            this.blob(outline, mb -> mb.ellipsoid(0.0, 0.0, 0.0, thick, halfHeight, seg, 10, 6, c), part);
         }

         Mat.copy(base, this.tf);
         return this;
      }

      public BlobModel.Kit cone(int part, double outline, double y0, double r0, double y1, double r1, int c) {
         return this.blob(outline, mb -> mb.frustum(y0, r0, y1, r1, 16, c), part);
      }

      public BlobModel.Kit bias(double v) {
         this.bias = v;
         return this;
      }
   }

   public abstract static class Quad extends BlobModel {
      protected double legY = 0.36;
      protected double frontZ = 0.22;
      protected double backZ = -0.22;
      protected double legX = 0.12;
      protected double backLegY = Double.NaN;
      protected double backLegX = Double.NaN;
      protected double neckY = 0.58;
      protected double neckZ = 0.28;
      protected double tailY = 0.56;
      protected double tailZ = -0.34;
      protected double tailLift = 0.0;
      protected double wagIdle = 14.0;
      protected double wagWalk = 9.0;
      protected double wagAmp = 0.45;
      protected boolean hop = false;

      private static void leg(double[] m, double x, double y, double z, double bob, double angle) {
         Mat.setTranslation(m, x, y + bob, z);
         Mat.rotX(m, angle);
      }

      @Override
      public boolean canWave() {
         return false;
      }

      @Override
      public void pose(double walkPhase, double walkAmp, boolean sneaking, double headYaw, double headPitch, double time, double idle, double[][] out) {
         double bly = Double.isNaN(this.backLegY) ? this.legY : this.backLegY;
         double blx = Double.isNaN(this.backLegX) ? this.legX : this.backLegX;
         double low = sneaking ? -0.05 : 0.0;
         double bob;
         double pitch;
         double fr;
         double fl;
         double br;
         double bl;
         if (this.hop) {
            double s = Math.sin(walkPhase);
            bob = Math.max(0.0, s) * 0.16 * walkAmp;
            pitch = -Math.cos(walkPhase) * 0.18 * walkAmp;
            br = bl = -s * 0.75 * walkAmp;
            fr = fl = s * 0.55 * walkAmp;
         } else {
            double s = Math.sin(walkPhase);
            bob = Math.abs(Math.cos(walkPhase)) * 0.02 * walkAmp;
            pitch = 0.0;
            fr = bl = s * 0.55 * walkAmp;
            fl = br = -s * 0.55 * walkAmp;
         }

         double breathe = Math.sin(time * 2.6) * 0.005;
         double[] torso = Prim.row(out, 0);
         Mat.setTranslation(torso, 0.0, bob + low + breathe, 0.0);
         Mat.translate(torso, 0.0, this.legY, 0.0);
         Mat.rotX(torso, pitch);
         Mat.translate(torso, 0.0, -this.legY, 0.0);
         double[] head = Prim.row(out, 1);
         Mat.copy(torso, head);
         Mat.translate(head, 0.0, this.neckY + low * 0.5, this.neckZ);
         Mat.rotX(head, -pitch);
         Mat.rotY(head, headYaw);
         Mat.rotX(head, headPitch);
         leg(Prim.row(out, 2), this.legX, this.legY, this.frontZ, bob, fr);
         leg(Prim.row(out, 3), -this.legX, this.legY, this.frontZ, bob, fl);
         leg(Prim.row(out, 4), blx, bly, this.backZ, bob, br);
         leg(Prim.row(out, 5), -blx, bly, this.backZ, bob, bl);
         double[] tail = Prim.row(out, 6);
         Mat.copy(torso, tail);
         Mat.translate(tail, 0.0, this.tailY, this.tailZ);
         boolean still = walkAmp < 0.2;
         double wag = Math.sin(time * (still ? this.wagIdle : this.wagWalk)) * this.wagAmp * (still ? 1.0 : 0.6);
         Mat.rotY(tail, wag);
         Mat.rotX(tail, this.tailLift);
      }

      @Override
      public double shadowRadius() {
         return 0.34;
      }
   }
}
