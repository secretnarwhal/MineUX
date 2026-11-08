package com.mineux.visuals.self;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Move animations: your walk (and, if asked, everyone's) in one of {@link MovePose}'s styles --
 * leaning into speed and turns, tucked in the air, settling on landing, bobbing with the step.
 * <p>
 * Two halves, both on the render thread. When a player's state is extracted this reads how they
 * move -- their speed along and across their facing from frame to frame, how fast they turn, in
 * the air or climbing or sprinting -- smoothed so it eases in and out, and a spring for the dip on
 * landing. When the game has posed the model for that state, the style reposes it. Swimming,
 * flying, riding, sleeping, spinning with a riptide and dying are left as the game draws them.
 */
final class Moves {

    private static final Map<LivingEntity, Motion> MOTIONS = new WeakHashMap<>();
    private static final Map<Object, Motion> BY_STATE = new WeakHashMap<>();
    private static final MovePose POSE = new MovePose();

    private Moves() {}

    /** How one player has been moving, smoothed from frame to frame. */
    private static final class Motion {
        float air, climb, sprint, fall, yawRate, lateral, accel, dip, dipVel, fwdSpeed, minVy, prevYaw;
        double px, py, pz;
        long last;
        boolean wasAir, seen;
        final MovePose.In in = new MovePose.In();
    }

    static void onExtract(LivingEntity p, AvatarRenderState state, boolean self) {
        SelfConfig config = SelfConfig.get();
        if (!config.moves || !(self || config.moveOthers)) return;
        Motion m = MOTIONS.get(p);
        if (m == null) {
            m = new Motion();
            MOTIONS.put(p, m);
        }
        long now = System.nanoTime();
        float dt = m.last == 0L ? 0.0F : Math.min(0.1F, (now - m.last) / 1.0E9F);
        m.last = now;
        track(m, p, state, dt);
        BY_STATE.put(state, m);
    }

    private static void track(Motion m, LivingEntity p, AvatarRenderState state, float dt) {
        float k = 1.0F - (float) Math.exp(0.0F - dt * 9.0F);
        boolean climbing = p.onClimbable() && !p.onGround();
        boolean air = !p.onGround() && !climbing && !p.isInWater();
        boolean timed = m.seen && dt > 1.0E-4F;
        float vx = 0.0F, vy = 0.0F, vz = 0.0F;
        if (timed) {
            vx = (float) ((state.x - m.px) / dt);
            vy = (float) ((state.y - m.py) / dt);
            vz = (float) ((state.z - m.pz) / dt);
            // A teleport is not a step.
            if (vx * vx + vy * vy + vz * vz > 400.0F) {
                vx = 0.0F;
                vy = 0.0F;
                vz = 0.0F;
            }
        }
        m.px = state.x;
        m.py = state.y;
        m.pz = state.z;

        float yaw = state.bodyRot;
        float yawRate = timed ? wrap(yaw - m.prevYaw) / dt : 0.0F;
        m.prevYaw = yaw;
        double r = Math.toRadians(yaw);
        float fx = (float) (0.0 - Math.sin(r));
        float fz = (float) Math.cos(r);
        float fwd = vx * fx + vz * fz;
        // Right of the facing is (-fz, fx).
        float lat = vx * fz * (0.0F - 1.0F) + vz * fx;
        float accel = dt > 1.0E-4F ? (fwd - m.fwdSpeed) / dt : 0.0F;

        m.fwdSpeed += (fwd - m.fwdSpeed) * k;
        m.air += ((air ? 1.0F : 0.0F) - m.air) * k;
        m.climb += ((climbing ? 1.0F : 0.0F) - m.climb) * k;
        m.sprint += ((p.isSprinting() ? 1.0F : 0.0F) - m.sprint) * k;
        m.fall += ((air && vy < 0.0F - 1.6F ? 1.0F : 0.0F) - m.fall) * k;
        float kk = 1.0F - (float) Math.exp(0.0F - dt * 6.0F);
        m.yawRate += (MovePose.clamp(yawRate, 0.0F - 720.0F, 720.0F) - m.yawRate) * kk;
        m.lateral += (MovePose.clamp(lat, 0.0F - 8.0F, 8.0F) - m.lateral) * kk;
        m.accel += (MovePose.clamp(accel, 0.0F - 30.0F, 30.0F) - m.accel) * kk;

        // Landing: a kick to a damped spring, harder the faster the fall was.
        if (air) m.minVy = Math.min(m.minVy, vy);
        if (m.seen && m.wasAir && !air && !climbing) m.dipVel += 9.0F * MovePose.clamp((0.0F - m.minVy) / 14.0F, 0.12F, 1.0F);
        if (!air) m.minVy = 0.0F;
        float w = 17.0F;
        float zeta = 0.42F;
        m.dipVel += (0.0F - w * w * m.dip - 2.0F * zeta * w * m.dipVel) * dt;
        m.dip += m.dipVel * dt;
        if (Math.abs(m.dip) < 1.0E-4F && Math.abs(m.dipVel) < 0.001F) {
            m.dip = 0.0F;
            m.dipVel = 0.0F;
        }
        m.wasAir = air;
        m.seen = true;
    }

    private static float wrap(float d) {
        while (d > 180.0F) d -= 360.0F;
        while (d < 0.0F - 180.0F) d += 360.0F;
        return d;
    }

    /** After the game has posed {@code model} for {@code state}: the style's pose instead. */
    static void onPose(HumanoidModel<?> model, Object state) {
        SelfConfig config = SelfConfig.get();
        if (!config.moves || !AvatarRenderState.class.isInstance(state)) return;
        Motion m = BY_STATE.get(state);
        if (m == null) return;
        AvatarRenderState s = (AvatarRenderState) state;
        if (s.isFallFlying || s.isVisuallySwimming || s.isPassenger || s.isAutoSpinAttack || s.hasPose(Pose.SLEEPING)
            || s.swimAmount > 0.05F || s.deathTime > 0.0F) return;
        MovePose.In in = m.in;
        in.pos = s.walkAnimationPos;
        in.spd = Math.min(1.0F, s.walkAnimationSpeed);
        in.age = s.ageInTicks;
        in.crouching = s.isCrouching;
        in.armsFree = s.attackTime <= 0.0F && !s.isUsingItem;
        in.air = m.air;
        in.climb = m.climb;
        in.sprint = m.sprint;
        in.fall = m.fall;
        in.yawRate = m.yawRate;
        in.lateral = m.lateral;
        in.accel = m.accel;
        in.dip = Math.max(0.0F, m.dip);
        in.turnLean = config.moveLean;
        in.settle = config.moveSettle;
        in.bounce = config.moveBounce;
        read(model, POSE);
        POSE.apply(Math.max(0, MovePose.styleIndex(config.moveStyle)), in, config.moveStrength);
        write(model, POSE);
    }

    private static ModelPart part(HumanoidModel<?> m, int i) {
        if (i == MovePose.HEAD) return m.head;
        if (i == MovePose.BODY) return m.body;
        if (i == MovePose.RARM) return m.rightArm;
        if (i == MovePose.LARM) return m.leftArm;
        if (i == MovePose.RLEG) return m.rightLeg;
        return m.leftLeg;
    }

    private static void read(HumanoidModel<?> m, MovePose p) {
        for (int i = 0; i < 6; i++) {
            ModelPart q = part(m, i);
            p.x[i] = q.x;
            p.y[i] = q.y;
            p.z[i] = q.z;
            p.xr[i] = q.xRot;
            p.yr[i] = q.yRot;
            p.zr[i] = q.zRot;
        }
    }

    private static void write(HumanoidModel<?> m, MovePose p) {
        for (int i = 0; i < 6; i++) {
            ModelPart q = part(m, i);
            q.x = p.x[i];
            q.y = p.y[i];
            q.z = p.z[i];
            q.xRot = p.xr[i];
            q.yRot = p.yr[i];
            q.zRot = p.zr[i];
        }
    }
}
