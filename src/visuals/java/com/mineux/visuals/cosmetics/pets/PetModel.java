package com.mineux.visuals.cosmetics.pets;

import com.mineux.visuals.cosmetics.CosmeticSink;

/**
 * A pet: how it poses its parts for a step of its walk, and how it draws them. The rig is seven
 * 3x4 matrices, one a part.
 */
public interface PetModel {
   int TORSO = 0;
   int HEAD = 1;
   int ARM_R = 2;
   int ARM_L = 3;
   int LEG_R = 4;
   int LEG_L = 5;
   int TAIL = 6;
   int PARTS = 7;

   default double stride() {
      return 1.0;
   }

   default double petScale() {
      return 0.42;
   }

   default double waveAngle() {
      return 1.95;
   }

   default boolean canWave() {
      return true;
   }

   void pose(double walkPhase, double walkAmp, boolean sneaking, double headYaw, double headPitch, double time, double idle, double[][] out);

   default double eyeHeight() {
      return 0.95;
   }

   /** Draws through {@code world} (camera-relative), each corner put back at {@code cam}. */
   void draw(CosmeticSink sink, double[] world, double[] cam, double[][] rig, float alpha);

   default double shadowRadius() {
      return 0.3;
   }
}
