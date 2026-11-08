package com.mineux.visuals.cosmetics.kill;

import java.util.ArrayList;
import java.util.List;

/** The kill effects playing now, oldest first, at most {@code max} of them. */
public final class KillEffects {

    private final ArrayList<KillEffects.Active> list = new ArrayList<>();
    private final int max;

    public KillEffects(int max) {
        this.max = Math.max(1, max);
    }

    public void clear() {
        this.list.clear();
    }

    public boolean isEmpty() {
        return this.list.isEmpty();
    }

    public List<KillEffects.Active> active() {
        return this.list;
    }

    public void spawn(KillEffects.Active a) {
        this.list.add(a);
        while (this.list.size() > this.max) this.list.remove(0);
    }

    public void prune(double now) {
        for (int i = this.list.size() - 1; i >= 0; i--) {
            if (this.list.get(i).expired(now)) this.list.remove(i);
        }
    }

    public static int seedFor(int entityId, long stamp) {
        long x = entityId * -7046029254386353131L + stamp * -4658895280553007687L;
        x ^= x >>> 31;
        x *= -7723592293110705685L;
        x ^= x >>> 29;
        return (int) x;
    }

    /** One effect: its style ({@link KillStyles}), where, the body's size, when it began and how long it lasts. */
    public record Active(int style, double x, double y, double z, double width, double height, double start, double life,
                         double reach, int segs, int seed) {

        public KillParams params(double now, KillParams.Ground ground) {
            return new KillParams(this.width, this.height, Math.max(0.0, now - this.start), this.life, this.reach, this.segs, this.seed, ground);
        }

        public boolean expired(double now) {
            return now - this.start >= this.life;
        }
    }
}
