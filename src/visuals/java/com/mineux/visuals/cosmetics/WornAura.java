package com.mineux.visuals.cosmetics;

import com.mineux.visuals.cosmetics.aura.AuraParams;
import com.mineux.visuals.cosmetics.aura.AuraReact;
import com.mineux.visuals.cosmetics.aura.Auras;
import com.mineux.visuals.cosmetics.aura.CircleParams;
import com.mineux.visuals.cosmetics.aura.ElementParams;
import com.mineux.visuals.cosmetics.aura.EnergySphere;
import com.mineux.visuals.cosmetics.aura.LightningArcs;
import com.mineux.visuals.cosmetics.aura.LightningParams;
import com.mineux.visuals.cosmetics.aura.Ripple;
import com.mineux.visuals.cosmetics.aura.RuneCircle;
import com.mineux.visuals.cosmetics.aura.SphereParams;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The aura round you. It turns at its own pace and, the faster you run, the faster it turns and
 * the wider it spreads; the energy sphere ripples where a hit lands on you.
 */
final class WornAura {

    private static long lastNanos = 0L;
    private static double turns = 0.0;
    private static double clock = 0.0;
    private static double energy = 0.0;
    private static double lastX = Double.NaN;
    private static double lastZ = Double.NaN;
    private static int lastHurt = 0;
    /** Recent hits on you, each {dx, dy, dz, when} in world axes. */
    private static final List<double[]> HITS = new ArrayList<>();

    private WornAura() {}

    static void draw(Minecraft mc, LocalPlayer p, float partialTick, CosmeticsConfig config) {
        int index = Auras.NAMES.indexOf(config.aura);
        if (index <= 0) return;
        long now = System.nanoTime();
        double dt = lastNanos == 0L ? 0.0 : Math.min(0.1, (now - lastNanos) / 1.0E9);
        lastNanos = now;
        double time = Cosmetics.seconds();
        Attach.Subject subject = Cosmetics.subject(p, partialTick, 1.0);
        energy = follow(energy, subject.x, subject.z, dt);
        hits(p, time);
        AuraReact.Reaction react = AuraReact.at(energy, config.auraReact);
        if (config.animate) {
            turns = wrap(turns + dt * config.auraSpeed * react.turn(), 10000.0, 1.0);
            clock = wrap(clock + dt * react.clock(), 100000.0, 3600.0);
        }

        int c1 = Cosmetics.color(config);
        int c2 = Cosmetics.color2(config);
        if (config.natural && Auras.natural(index, 0) != 0) {
            c1 = Auras.natural(index, 0);
            c2 = Auras.natural(index, 1);
        }
        int where = Auras.grounded(index) ? Attach.FEET : Attach.CENTER;
        Attach.Frame mount = Attach.frame(where, subject);
        QuadBatch batch = new QuadBatch();
        Builder b = Cosmetics.builder(mc, batch, c1, c2, config, clock);
        b.frame(mount, config.scale);
        Cosmetic aura = make(index, config, react, p.getBbHeight(), mount, time);
        if (aura == null) return;
        aura.build(b);
        batch.submit(false);
    }

    private static Cosmetic make(int index, CosmeticsConfig config, AuraReact.Reaction react, double height, Attach.Frame mount, double time) {
        double radius = config.auraRadius * react.radius();
        if (index == Auras.SPHERE) return new EnergySphere(new SphereParams(1.2 * config.auraRadius, 3, ripples(mount, time)));
        if (index == Auras.ARCS) return new LightningArcs(new LightningParams(config.auraCount + 1, 1.2 * react.reach(), 20.0, 600));
        if (index == Auras.RUNE_CIRCLE) return new RuneCircle(new CircleParams(1.5 * radius, turns));
        if (index >= Auras.FIRST_ELEMENT) return Auras.element(index, new ElementParams(radius, height));
        return Auras.orbit(index, new AuraParams(config.auraCount, radius, turns, config.auraSpeed));
    }

    private static double wrap(double v, double limit, double mod) {
        return v > limit || v < 0.0 - limit ? v % mod : v;
    }

    /** The run's energy, 0 to 1, from how far you moved since the last frame. */
    private static double follow(double current, double x, double z, double dt) {
        double next = current;
        if (dt > 0.002 && !Double.isNaN(lastX)) {
            double d = Math.hypot(x - lastX, z - lastZ);
            if (d <= 10.0) next = AuraReact.follow(current, AuraReact.energyFor(d / dt), dt);
        }
        if (dt > 0.002 || Double.isNaN(lastX)) {
            lastX = x;
            lastZ = z;
        }
        return next;
    }

    /** A new hurt on you is a ripple, towards the nearest one who could have done it. */
    private static void hits(LocalPlayer p, double time) {
        for (int i = HITS.size() - 1; i >= 0; i--) {
            if (time - HITS.get(i)[3] > 1.3) HITS.remove(i);
        }
        int hurt = p.hurtTime;
        boolean fresh = hurt > lastHurt && hurt > 0;
        lastHurt = hurt;
        if (!fresh) return;
        double yaw = Math.toRadians(p.yBodyRot);
        double dx = 0.0 - Math.sin(yaw);
        double dy = 0.25;
        double dz = Math.cos(yaw);
        net.minecraft.world.entity.player.Player near = p.level().getNearestPlayer(p, 8.0);
        if (near != null) {
            dx = near.getX() - p.getX();
            dy = near.getY() - p.getY();
            dz = near.getZ() - p.getZ();
        }
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1.0E-6) return;
        if (HITS.size() >= 4) HITS.remove(0);
        HITS.add(new double[] {dx / l, dy / l, dz / l, time});
    }

    private static List<Ripple> ripples(Attach.Frame f, double time) {
        List<Ripple> out = new ArrayList<>(HITS.size());
        for (double[] h : HITS) {
            out.add(new Ripple(h[0] * f.rx + h[1] * f.ry + h[2] * f.rz, h[0] * f.ux + h[1] * f.uy + h[2] * f.uz,
                h[0] * f.fx + h[1] * f.fy + h[2] * f.fz, time - h[3]));
        }
        return out;
    }
}
