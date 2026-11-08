package com.mineux.visuals.cosmetics;

import com.mineux.visuals.cosmetics.pets.Mat;
import com.mineux.visuals.cosmetics.pets.PetModel;
import com.mineux.visuals.cosmetics.pets.Pets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Objects;

/**
 * A pet at your side. It walks after you on the ground -- stepping up, dropping off edges,
 * jumping what is too tall to step -- turns its head to look at you, waves when you stand
 * still a while, and is put back beside you when it falls far behind.
 */
final class PetWalk {

    private static final double GRAVITY = 24.0;

    private static boolean placed = false;
    private static ClientLevel lastLevel;
    private static int lastPet = 0 - 1;
    private static double px;
    private static double py;
    private static double pz;
    private static double vy;
    private static double yaw;
    private static double walkPhase;
    private static double walkAmp;
    private static double idle;
    private static double waveT = 0.0 - 1.0;
    private static double nextWave = 3.5;
    private static long lastNanos;
    private static final double[][] RIG = new double[7][12];
    private static final double[] WORLD = new double[12];
    private static final BlockPos.MutableBlockPos PROBE = new BlockPos.MutableBlockPos();

    private PetWalk() {}

    static void draw(Minecraft mc, LocalPlayer p, float partialTick, CosmeticsConfig config) {
        int index = Pets.NAMES.indexOf(config.pet);
        PetModel model = Pets.get(index);
        if (model == null) {
            placed = false;
            return;
        }
        long now = System.nanoTime();
        double dt = lastNanos == 0L ? 0.016 : Math.min(0.1, (now - lastNanos) / 1.0E9);
        lastNanos = now;
        if (mc.level != lastLevel || index != lastPet) {
            placed = false;
            lastLevel = mc.level;
            lastPet = index;
        }
        double x = Mth.lerp(partialTick, p.xo, p.getX());
        double y = Mth.lerp(partialTick, p.yo, p.getY());
        double z = Mth.lerp(partialTick, p.zo, p.getZ());
        double by = Math.toRadians(Mth.rotLerp(partialTick, p.yBodyRotO, p.yBodyRot));
        double fx = 0.0 - Math.sin(by);
        double fz = Math.cos(by);
        double side = Objects.equals("left", config.petSide) ? 0.0 - 1.0 : Objects.equals("behind", config.petSide) ? 0.0 : 1.0;
        double dist = config.petDistance;
        double back = side == 0.0 ? dist : dist * 0.45;
        double tx = x - fz * side * dist * 0.85 - fx * back;
        double tz = z + fx * side * dist * 0.85 - fz * back;
        double s = model.petScale() * config.petScale;
        step(mc.level, p, model, dt, tx, tz, x, y, z, config.petSpeed, s);

        double time = Cosmetics.seconds();
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        QuadBatch batch = new QuadBatch();
        shadow(mc, batch, model, config);
        pose(model, p, y + p.getEyeHeight(), s, time);
        double yr = Math.toRadians(yaw);
        double hx = 0.0 - Math.sin(yr);
        double hz = Math.cos(yr);
        double[] w = WORLD;
        w[0] = (0.0 - hz) * s;
        w[1] = 0.0;
        w[2] = hx * s;
        w[3] = px - cam.x;
        w[4] = 0.0;
        w[5] = s;
        w[6] = 0.0;
        w[7] = py - cam.y;
        w[8] = hx * s;
        w[9] = 0.0;
        w[10] = hz * s;
        w[11] = pz - cam.z;
        model.draw(batch, w, new double[] {cam.x, cam.y, cam.z}, RIG, (float) config.opacity);
        batch.submit(false);
    }

    /** The rig for this frame: its walk, its head on you, and a wave now and then. */
    private static void pose(PetModel model, LocalPlayer p, double eyeY, double s, double time) {
        double hx = p.getX() - px;
        double hz = p.getZ() - pz;
        double dirYaw = Math.toDegrees(Math.atan2(0.0 - hx, hz));
        double headYaw = Math.toRadians(Mth.clamp(Mth.wrapDegrees(dirYaw - yaw), 0.0 - 70.0, 70.0));
        double headY = py + s * model.eyeHeight();
        double headPitch = Mth.clamp(0.0 - Math.atan2(eyeY - headY, Math.max(0.3, Math.hypot(hx, hz))), 0.0 - 0.75, 0.5);
        model.pose(walkPhase, walkAmp, p.getPose() == Pose.CROUCHING, headYaw, headPitch, time, idle, RIG);
        if (!model.canWave()) return;
        if (waveT < 0.0 && idle > nextWave) {
            waveT = 0.0;
            nextWave = idle + 5.0 + 4.0 * Math.random();
        }
        if (waveT < 0.0) return;
        double env = Math.min(1.0, Math.min(waveT / 0.25, (1.8 - waveT) / 0.25));
        if (env <= 0.0 && waveT > 1.0) {
            waveT = 0.0 - 1.0;
            return;
        }
        Mat.rotZ(Prim.row(RIG, 2), Math.max(0.0, env) * (model.waveAngle() + 0.32 * Math.sin(waveT * 14.0)));
    }

    private static void step(ClientLevel level, LocalPlayer p, PetModel model, double dt, double tx, double tz,
                             double x, double y, double z, double speedMul, double s) {
        if (!placed) {
            place(level, tx, tz, y);
            yaw = p.getYRot();
            walkAmp = 0.0;
            idle = 0.0;
            waveT = 0.0 - 1.0;
            placed = true;
        }
        double dx = tx - px;
        double dz = tz - pz;
        double d = Math.hypot(dx, dz);
        double dyP = y - py;
        if (d > 14.0 || dyP < 0.0 - 6.0 || dyP > 6.0 && p.onGround()) {
            place(level, tx, tz, y);
            d = 0.0;
            dx = 0.0;
            dz = 0.0;
        }
        double v = Math.min(9.0, Math.max(0.0, (d - 0.25) * 3.2)) * speedMul;
        double step = Math.min(v * dt, Math.max(0.0, d - 0.2));
        if (d > 1.0E-4 && step > 0.0) {
            px += dx / d * step;
            pz += dz / d * step;
        }
        double moveSpeed = step / Math.max(dt, 1.0E-4);
        double targetYaw = moveSpeed > 0.3 ? Math.toDegrees(Math.atan2(0.0 - dx, dz)) : Math.toDegrees(Math.atan2(0.0 - (x - px), z - pz));
        yaw = yaw + Mth.clamp(Mth.wrapDegrees(targetYaw - yaw), 0.0 - 540.0 * dt, 540.0 * dt);
        walkAmp = walkAmp + (Math.min(1.0, moveSpeed / 2.2) - walkAmp) * Math.min(1.0, dt * 10.0);
        walkPhase = walkPhase + dt * moveSpeed * 3.6 / Math.max(0.2, s * model.stride());
        if (walkPhase > 1000000.0) walkPhase -= 1000000.0;
        fall(level, dt, y);
        if (!(moveSpeed > 0.3) && vy == 0.0) {
            idle += dt;
        } else {
            idle = 0.0;
            waveT = 0.0 - 1.0;
            nextWave = 3.5;
        }
        if (waveT >= 0.0) waveT += dt;
    }

    private static void place(ClientLevel level, double tx, double tz, double y) {
        px = tx;
        pz = tz;
        py = groundOr(level, tx, tz, y + 0.5, y);
        vy = 0.0;
    }

    /** Gravity, landing, a step up, a jump onto what is taller than a step. */
    private static void fall(ClientLevel level, double dt, double playerY) {
        double ground = groundOr(level, px, pz, py + 1.1, Math.min(py, playerY));
        if (vy != 0.0 || py > ground + 0.001) {
            vy -= GRAVITY * dt;
            py = py + vy * dt;
            if (vy <= 0.0 && py <= ground) {
                py = ground;
                vy = 0.0;
            }
        } else if (ground > py) {
            double h = ground - py;
            if (h > 0.3) vy = Math.sqrt(2.0 * GRAVITY * (h + 0.2));
            else py = Math.min(ground, py + dt * 4.0);
        }
    }

    private static double groundOr(ClientLevel level, double x, double z, double fromY, double fallback) {
        double g = groundY(level, x, z, fromY, fromY - 6.0);
        return Double.isNaN(g) ? fallback : g;
    }

    private static double groundY(ClientLevel level, double x, double z, double fromY, double minY) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int top = Mth.floor(fromY);
        int bottom = Math.max(Mth.floor(minY), level.getMinY());
        for (int byy = top; byy >= bottom; byy--) {
            PROBE.set(bx, byy, bz);
            BlockState st = level.getBlockState(PROBE);
            if (st.isAir()) continue;
            VoxelShape shape = st.getCollisionShape(level, PROBE);
            if (shape.isEmpty()) continue;
            double h = byy + shape.bounds().maxY;
            if (h <= fromY + 0.001) return h;
        }
        return Double.NaN;
    }

    /** A soft dark disc on the ground under it, fainter the higher it is. */
    private static void shadow(Minecraft mc, QuadBatch batch, PetModel model, CosmeticsConfig config) {
        double g = groundOr(mc.level, px, pz, py, py);
        double k = Mth.clamp(1.0 - (py - g) * 1.5, 0.0, 1.0) * config.opacity;
        if (k <= 0.02) return;
        double r = model.shadowRadius() * config.petScale;
        int rings = 3;
        int segs = 18;
        for (int ring = 0; ring < rings; ring++) {
            double r0 = r * ring / rings;
            double r1 = r * (ring + 1) / rings;
            int alpha = (int) (110.0 * k * (1.0 - (ring + 0.5) / rings));
            int color = alpha << 24;
            for (int i = 0; i < segs; i++) {
                double a0 = Math.PI * 2.0 * i / segs;
                double a1 = Math.PI * 2.0 * (i + 1) / segs;
                double y = g + 0.012;
                batch.quad(
                    new double[] {px + Math.cos(a0) * r0, y, pz + Math.sin(a0) * r0},
                    new double[] {px + Math.cos(a0) * r1, y, pz + Math.sin(a0) * r1},
                    new double[] {px + Math.cos(a1) * r1, y, pz + Math.sin(a1) * r1},
                    new double[] {px + Math.cos(a1) * r0, y, pz + Math.sin(a1) * r0},
                    color, color, color, color);
            }
        }
    }
}
