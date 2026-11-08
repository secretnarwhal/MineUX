package com.mineux.visuals.cosmetics.hats;

import java.util.function.DoubleBinaryOperator;
import java.util.Arrays;
import java.util.function.DoubleUnaryOperator;
import com.mineux.visuals.cosmetics.Builder;
import com.mineux.visuals.cosmetics.Prim;

final class EarKit {
   private static final double[] RIGHT = new double[]{1.0, 0.0, 0.0};

   private EarKit() {
   }

   static void shell(
      Builder b,
      double[][] spine,
      DoubleUnaryOperator hw,
      DoubleUnaryOperator hd,
      int segs,
      DoubleBinaryOperator outer,
      DoubleBinaryOperator inner,
      double innerW,
      double innerD,
      double innerTo
   ) {
      Prim.flatTube(b, spine, RIGHT, hw, hd, segs, outer);
      int k = Math.min(spine.length, Math.max(2, (int)Math.round(innerTo * (spine.length - 1))) + 1);
      double[][] sub = Arrays.copyOf(spine, k);
      double f = (double)(k - 1) / (spine.length - 1);
      DoubleUnaryOperator round = v -> Math.sqrt(Math.max(0.0, 1.0 - v * v * v));
      Prim.flatTube(
         b,
         sub,
         RIGHT,
         v -> hw.applyAsDouble(v * f) * innerW * round.applyAsDouble(v),
         v -> hd.applyAsDouble(v * f) * innerD * round.applyAsDouble(v),
         v -> hd.applyAsDouble(v * f) * (1.0 - innerD) + 0.007 * (1.0 - v),
         segs,
         (u, v) -> Prim.at(inner, u, v * f)
      );
   }
}
