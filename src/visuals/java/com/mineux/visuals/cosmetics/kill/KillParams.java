package com.mineux.visuals.cosmetics.kill;

import com.mineux.visuals.cosmetics.Col;

/**
 * What a kill effect is drawn from: the body's size, how far into its life it is, how far it
 * reaches, and the ground under it.
 */
public record KillParams(double width, double height, double age, double life, double reach, int segs, int seed, KillParams.Ground ground) {

    public static final KillParams.Ground FLAT = new KillParams.Ground();

    public KillParams {
        if (!(width > 0.0)) width = 0.6;
        if (!(height > 0.0)) height = 1.8;
        if (!(life > 0.05)) life = 0.05;
        if (!(reach > 0.2)) reach = 0.2;
        if (Double.isNaN(age)) age = 0.0;
        segs = Math.max(12, Math.min(128, segs));
        if (ground == null) ground = FLAT;
    }

    public double t() {
        return Col.clamp01(this.age / this.life);
    }

    public static KillParams defaults() {
        return new KillParams(0.6, 1.8, 0.3, 0.625, 5.0, 64, 1, FLAT);
    }

    public KillParams withAge(double a) {
        return new KillParams(this.width, this.height, a, this.life, this.reach, this.segs, this.seed, this.ground);
    }

    public KillParams withReach(double r) {
        return new KillParams(this.width, this.height, this.age, this.life, r, this.segs, this.seed, this.ground);
    }

    public double bulk() {
        return Math.max(1.0, Math.min(3.0, Math.pow(this.height / 1.8, 0.6)));
    }

    /**
     * The ground's height at (u, w) round the body, relative to its feet. Flat here; a class and
     * not an interface, so a lambda never stands in for it (the obfuscation breaks those).
     */
    public static class Ground {
        public double at(double u, double w) {
            return 0.0;
        }
    }
}
