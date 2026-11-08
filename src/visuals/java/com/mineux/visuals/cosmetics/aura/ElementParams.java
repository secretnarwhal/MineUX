package com.mineux.visuals.cosmetics.aura;

public record ElementParams(double radius, double bodyHeight) {
   public static ElementParams defaults() {
      return new ElementParams(1.0, 1.8);
   }
}
