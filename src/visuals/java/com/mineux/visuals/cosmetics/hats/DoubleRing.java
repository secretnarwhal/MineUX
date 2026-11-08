package com.mineux.visuals.cosmetics.hats;

import java.util.function.DoubleBinaryOperator;
import com.mineux.visuals.cosmetics.Builder;
import com.mineux.visuals.cosmetics.Col;
import com.mineux.visuals.cosmetics.Cosmetic;
import com.mineux.visuals.cosmetics.Prim;

public final class DoubleRing implements Cosmetic {
   static final double LOW_Y = 0.2;
   static final double LOW_R = 0.33;
   static final double LOW_TUBE = 0.024;
   static final double UP_Y = 0.37;
   static final double UP_R = 0.24;
   static final double UP_TUBE = 0.016;
   static final int STRUTS = 4;
   private static final double TAU = Math.PI * 2;
   private final HatParams p;

   public DoubleRing() {
      this(HatParams.defaults());
   }

   public DoubleRing(HatParams p) {
      this.p = p;
   }

   @Override
   public void build(Builder b) {
      double t = b.animate ? b.time : 0.0;
      double bob = this.p.bob() && b.animate ? Math.sin(t * 1.9) * 0.016 : 0.0;
      double breathe = b.animate ? 1.0 + 0.02 * Math.sin(t * 1.5) : 1.0;
      double drift = b.animate ? t * 0.1 : 0.0;
      double turn = b.animate ? t * this.p.spin() * (Math.PI * 2) : 0.0;
      int c1 = b.c1;
      int c2 = b.c2;
      b.push();
      b.translate(0.0, bob, 0.0);
      b.scaleLocal(breathe);
      DoubleBinaryOperator low = ringShade(c1, c2, drift, 1.0, t);
      DoubleBinaryOperator up = ringShade(c2, c1, -drift, -1.0, t);
      Prim.torus(b, 0.2, 0.33, 0.024, 36, 5, low);
      Prim.torus(b, 0.37, 0.24, 0.016, 32, 4, up);
      DoubleBinaryOperator gLow = (u, v) -> Col.withA(Col.tint(Prim.at(low, u, 0.25), 0.45), 0.5 * (1.0 - v) * (1.0 - v));
      DoubleBinaryOperator gLowIn = (u, v) -> Col.withA(Col.tint(Prim.at(low, u, 0.25), 0.45), 0.4 * v * v);
      DoubleBinaryOperator gUp = (u, v) -> Col.withA(Col.tint(Prim.at(up, u, 0.25), 0.45), 0.45 * (1.0 - v) * (1.0 - v));
      Prim.ring(b, 0.2, 0.33, 0.43000000000000005, 36, gLow);
      Prim.ring(b, 0.2, 0.25, 0.33, 36, gLowIn);
      Prim.ring(b, 0.37, 0.24, 0.32, 32, gUp);

      for (int i = 0; i < 4; i++) {
         double a = turn + i * (Math.PI * 2) / 4.0;
         double ca = Math.cos(a);
         double sa = Math.sin(a);
         double[] lo = Prim.P(0.33 * ca, 0.2, 0.33 * sa);
         double[] hi = Prim.P(0.24 * ca, 0.37, 0.24 * sa);
         int strutLo = Col.shade(c1, 0.9);
         int strutHi = Col.tint(c2, 0.25);
         Prim.taperedTube(b, lo, hi, 0.0065, 0.0055, 4, Prim.byV(strutLo, strutHi), false);
         double s = b.animate ? 0.5 + 0.5 * Math.sin(t * 1.6 + i * 1.57) : 0.5;
         double[] at = Prim.lerp(lo, hi, 0.08 + 0.84 * s);
         int bead = Col.tint(i % 2 == 0 ? c1 : c2, 0.4);
         Prim.sphere(b, at[0], at[1], at[2], 0.021, 6, 4, (u, v) -> Col.mix(Col.dark(bead, 0.35), Col.tint(bead, 0.55), v));
      }

      b.pop();
   }

   private static DoubleBinaryOperator ringShade(int ca, int cb, double drift, double dir, double t) {
      return (u, v) -> {
         double x = frac(u + drift);
         int base = Col.mix(ca, cb, 1.0 - Math.abs(2.0 * x - 1.0));
         double band = Math.pow(Math.max(0.0, Math.cos((u - dir * t * 0.35) * (Math.PI * 2))), 6.0);
         double top = Math.max(0.0, Math.sin(v * (Math.PI * 2)));
         double under = Math.max(0.0, -Math.sin(v * (Math.PI * 2)));
         return Col.shade(Col.tint(base, 0.3 * top + 0.55 * band), 1.0 - 0.22 * under);
      };
   }

   private static double frac(double x) {
      return x - Math.floor(x);
   }
}
