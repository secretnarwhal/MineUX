package com.mineux.visuals.cosmetics;

/** Where a {@link Builder}'s quads go: world-space corners and a colour for each. */
public interface CosmeticSink {
    void quad(double[] a, double[] b, double[] c, double[] d, int ca, int cb, int cc, int cd);
}
