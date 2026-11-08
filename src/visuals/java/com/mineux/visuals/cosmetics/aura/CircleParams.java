package com.mineux.visuals.cosmetics.aura;

public record CircleParams(double radius, double phase) {
   public static CircleParams defaults() {
      return new CircleParams(1.5, 0.0);
   }
}
