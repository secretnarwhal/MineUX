package com.mineux.visuals.cosmetics;

import com.mineux.visuals.cosmetics.wings.WingBuilder;
import com.mineux.visuals.cosmetics.wings.Wings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Wings on your back. They beat slowly standing, faster walking, hard in the air and glide when
 * you do; built upright from your back and turned over with your body when you swim or fly.
 */
final class WornWings {

    private static final double REST_ROLL = 0.1;
    private static final double REST_FOLD = 0.18;

    private static long lastNanos = 0L;
    private static double flapPhase = 0.0;
    private static double flapAmp = 0.0;

    private WornWings() {}

    static void draw(Minecraft mc, LocalPlayer p, float partialTick, CosmeticsConfig config) {
        int index = Wings.NAMES.indexOf(config.wings);
        if (index <= 0) return;
        long now = System.nanoTime();
        double dt = lastNanos == 0L ? 0.016 : Math.min(0.1, (now - lastNanos) / 1.0E9);
        lastNanos = now;
        double roll = REST_ROLL;
        double fold = REST_FOLD;
        if (config.animate) {
            double[] flap = flap(dt, Math.hypot(p.getX() - p.xo, p.getZ() - p.zo), !p.onGround(), p.isFallFlying(), p.isSwimming(), config.flapSpeed);
            roll = flap[0];
            fold = flap[1];
        }
        Attach.Subject subject = Cosmetics.subject(p, partialTick, 1.0);
        Attach.Frame real = Attach.frame(Attach.BACK, subject);
        Attach.Frame up = upright(real);
        double[] tilt = tilt(real, up);
        double[] base = {up.ox, up.oy, up.oz};
        double unit = config.scale * Wings.unit(index);

        QuadBatch batch = new QuadBatch();
        if (tilt != null) batch.tilt(tilt, base[0], base[1], base[2]);
        WingBuilder b = new WingBuilder(batch, Cosmetics.color(config), Cosmetics.color2(config), config.opacity, Cosmetics.seconds(), config.animate);
        double rx = 0.0 - up.rx;
        double rz = 0.0 - up.rz;
        b.frame(base, rx, rz, up.fx, up.fz, unit, roll, fold, 0);
        Wings.build(index, b);
        b.frame(base, 0.0 - rx, 0.0 - rz, up.fx, up.fz, unit, roll, fold, 1);
        Wings.build(index, b);
        batch.submit(false);
    }

    /** This frame's {roll, fold}, the beat carried on from the last frame. */
    private static double[] flap(double dt, double moved, boolean airborne, boolean gliding, boolean swimming, double speed) {
        double targetAmp;
        double freq;
        if (gliding) {
            targetAmp = 0.12 + Math.min(moved, 0.5) * 0.15;
            freq = (0.4 + Math.min(moved, 0.5) * 1.2) * speed;
        } else if (swimming) {
            targetAmp = 0.2 + Math.min(moved, 0.3) * 0.25;
            freq = (1.0 + Math.min(moved, 0.3) * 2.0) * speed;
        } else {
            targetAmp = airborne ? 0.36 : 0.05 + Math.min(moved, 0.3) * 0.35;
            freq = (airborne ? 2.3 : 0.7 + Math.min(moved, 0.3) * 3.0) * speed;
        }
        double amp = flapAmp + (targetAmp - flapAmp) * Math.min(1.0, dt * 6.0);
        double phase = flapPhase + dt * freq * Math.PI * 2.0;
        if (phase > 1000000.0) phase -= 1000000.0;
        double roll = REST_ROLL + amp * Math.sin(phase);
        double fold = Math.max(0.0, REST_FOLD - amp * 0.35 * (0.5 + 0.5 * Math.sin(phase)));
        if (gliding) {
            fold = Math.max(fold, 0.05);
            roll *= 0.6;
        }
        flapAmp = amp;
        flapPhase = phase;
        return new double[] {roll, fold};
    }

    /** {@code f} stood up: the same origin, its right axis flattened, up straight up. */
    private static Attach.Frame upright(Attach.Frame f) {
        double rx = f.rx;
        double rz = f.rz;
        double l = Math.hypot(rx, rz);
        if (l < 1.0E-4) {
            rx = 0.0 - f.fz;
            rz = f.fx;
            l = Math.hypot(rx, rz);
        }
        if (l < 1.0E-6) {
            rx = 1.0;
            rz = 0.0;
            l = 1.0;
        }
        rx /= l;
        rz /= l;
        return new Attach.Frame(f.ox, f.oy, f.oz, rx, 0.0, rz, 0.0, 1.0, 0.0, rz, 0.0, 0.0 - rx);
    }

    /** The turn from {@code up} to {@code real}, row-major; null when they are the same. */
    private static double[] tilt(Attach.Frame real, Attach.Frame up) {
        double[] a = {real.rx, real.ux, real.fx, real.ry, real.uy, real.fy, real.rz, real.uz, real.fz};
        double[] b = {up.rx, up.ry, up.rz, up.ux, up.uy, up.uz, up.fx, up.fy, up.fz};
        double[] q = new double[9];
        double off = 0.0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double v = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
                q[i * 3 + j] = v;
                off += Math.abs(v - (i == j ? 1.0 : 0.0));
            }
        }
        return off < 0.001 ? null : q;
    }
}
