package com.mineux.visuals.self;

import java.util.Arrays;
import java.util.List;

/**
 * The six parts of a humanoid's pose -- head, body, right arm, left arm, right leg, left leg -- as
 * the game's model left them, and the walk styles that repose them. Positions are in the model's
 * pixels with y down, angles in radians, as {@code ModelPart} keeps them.
 * <p>
 * Every style is the game's walk rewritten: legs and arms swung on the walk cycle, then leaned,
 * bobbed, tucked in the air and leaned into turns from {@link In}. The arms are left alone while
 * the player swings or uses an item, so a hit or a bow still looks like one.
 */
final class MovePose {

    static final int HEAD = 0, BODY = 1, RARM = 2, LARM = 3, RLEG = 4, LLEG = 5;

    static final List<String> STYLES = Arrays.asList("natural", "athletic", "relaxed", "confident", "ninja", "levitate", "zombie", "robot", "swagger");
    private static final int NATURAL = 0, ATHLETIC = 1, RELAXED = 2, CONFIDENT = 3, NINJA = 4, LEVITATE = 5, ZOMBIE = 6, ROBOT = 7, SWAGGER = 8;

    final float[] x = new float[6];
    final float[] y = new float[6];
    final float[] z = new float[6];
    final float[] xr = new float[6];
    final float[] yr = new float[6];
    final float[] zr = new float[6];

    /** The pose before a style, for blending back by strength and for the arms while they are busy. */
    private final float[] base = new float[36];

    /** What the style needs to know of the player's movement, smoothed by {@link Moves}. */
    static final class In {
        float pos;
        float spd;
        float age;
        boolean crouching;
        boolean armsFree = true;
        float air;
        float climb;
        float sprint;
        float fall;
        float yawRate;
        float lateral;
        float accel;
        float dip;
        boolean turnLean = true;
        boolean settle = true;
        boolean bounce = true;
    }

    static int styleIndex(String name) {
        return STYLES.indexOf(name);
    }

    void apply(int style, In in, float k) {
        if (k <= 0.0F) return;
        save();
        float sw = (float) Math.cos(in.pos * 0.6662F);
        float step = Math.abs((float) Math.sin(in.pos * 0.6662F));
        float spd = in.spd;
        if (style == ATHLETIC) athletic(in, sw, step, spd);
        else if (style == RELAXED) relaxed(in, sw, step, spd);
        else if (style == CONFIDENT) confident(in, sw, step, spd);
        else if (style == NINJA) ninja(in, sw, spd);
        else if (style == LEVITATE) levitate(in, spd);
        else if (style == ZOMBIE) zombie(in, sw, spd);
        else if (style == ROBOT) robot(in, spd);
        else if (style == SWAGGER) swagger(in, sw, spd);
        else natural(in, sw, step, spd);

        // The walks drawn from the game's own crouch the arms the way it does.
        if (style <= CONFIDENT && in.crouching) {
            xr[RARM] += 0.4F;
            xr[LARM] += 0.4F;
        }
        climb(in);
        if (!in.armsFree) {
            for (int i = RARM; i <= LARM; i++) {
                xr[i] = base[18 + i];
                yr[i] = base[24 + i];
                zr[i] = base[30 + i];
            }
        }
        if (in.settle && in.dip > 0.001F && !in.crouching) crouchShape(Math.min(0.4F, in.dip * 0.9F));
        if (in.turnLean && !in.crouching) lean(in, spd);
        if (k < 1.0F) blend(k);
    }

    private void lean(In in, float spd) {
        float move = Math.min(1.0F, spd * 1.4F);
        float roll = 0.0F - clamp(in.yawRate * 0.0011F * move, 0.0F - 0.13F, 0.13F) - clamp(in.lateral * 0.045F, 0.0F - 0.11F, 0.11F);
        roll *= 1.0F - in.climb;
        if (Math.abs(roll) > 1.0E-4F) roll(roll);
        float pitch = clamp(in.accel * 0.012F, 0.0F - 0.06F, 0.09F) * (1.0F - in.air) * (1.0F - in.climb);
        if (Math.abs(pitch) > 1.0E-4F) pitch(pitch);
    }

    private void save() {
        System.arraycopy(x, 0, base, 0, 6);
        System.arraycopy(y, 0, base, 6, 6);
        System.arraycopy(z, 0, base, 12, 6);
        System.arraycopy(xr, 0, base, 18, 6);
        System.arraycopy(yr, 0, base, 24, 6);
        System.arraycopy(zr, 0, base, 30, 6);
    }

    private void blend(float k) {
        for (int i = 0; i < 6; i++) {
            x[i] = mix(base[i], x[i], k);
            y[i] = mix(base[6 + i], y[i], k);
            z[i] = mix(base[12 + i], z[i], k);
            xr[i] = mix(base[18 + i], xr[i], k);
            yr[i] = mix(base[24 + i], yr[i], k);
            zr[i] = mix(base[30 + i], zr[i], k);
        }
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static float mix(float a, float b, float k) {
        return a + (b - a) * k;
    }

    /** Rolls the whole pose about the feet. */
    private void roll(float a) {
        float c = (float) Math.cos(a);
        float s = (float) Math.sin(a);
        float fy = 24.0F;
        for (int i = 0; i < 6; i++) {
            float dx = x[i];
            float dy = y[i] - fy;
            x[i] = dx * c - dy * s;
            y[i] = fy + dx * s + dy * c;
            if (i != HEAD) zr[i] += a;
        }
    }

    /** Pitches the whole pose about the feet. */
    private void pitch(float a) {
        float c = (float) Math.cos(a);
        float s = (float) Math.sin(a);
        float fy = 24.0F;
        for (int i = 0; i < 6; i++) {
            float dy = y[i] - fy;
            float dz = z[i];
            y[i] = fy + dy * c - dz * s;
            z[i] = dy * s + dz * c;
            if (i != HEAD) xr[i] += a;
        }
    }

    /** Leans the upper body forward about the hips, the legs left standing. */
    private void leanRigid(float a) {
        if (a <= 1.0E-4F) return;
        float c = (float) Math.cos(a);
        float s = (float) Math.sin(a);
        float hy = 12.0F;
        for (int i = HEAD; i <= LARM; i++) {
            float dy = y[i] - hy;
            float dz = z[i];
            y[i] = hy + dy * c - dz * s;
            z[i] = dy * s + dz * c;
            if (i != HEAD) xr[i] += a;
        }
    }

    private void bob(float d) {
        for (int i = 0; i < 6; i++) y[i] += d;
    }

    /** The game's crouch, {@code l} of the way. */
    private void crouchShape(float l) {
        if (l <= 0.001F) return;
        xr[BODY] += l;
        xr[HEAD] -= l * 0.6F;
        y[BODY] += 6.4F * l;
        y[HEAD] += 8.4F * l;
        y[RARM] += 6.4F * l;
        y[LARM] += 6.4F * l;
        z[RLEG] += 8.0F * l;
        z[LLEG] += 8.0F * l;
        y[RLEG] += 0.4F * l;
        y[LLEG] += 0.4F * l;
    }

    private void airPose(In in, float tuck, float reach, float armUp) {
        if (in.air <= 0.001F) return;
        float a = in.air;
        float f = in.fall;
        xr[RLEG] = mix(xr[RLEG], mix(tuck, reach, f), a);
        xr[LLEG] = mix(xr[LLEG], mix(tuck * 0.35F, reach * 0.5F + 0.1F, f), a);
        xr[RARM] = mix(xr[RARM], mix(armUp, 0.15F, f), a);
        xr[LARM] = mix(xr[LARM], mix(armUp * 0.6F, 0.15F, f), a);
        zr[RARM] = mix(zr[RARM], 0.16F + 0.2F * f, a);
        zr[LARM] = mix(zr[LARM], 0.0F - 0.16F - 0.2F * f, a);
    }

    private void climb(In in) {
        if (in.climb <= 0.001F) return;
        float c = (float) Math.sin(in.age * 0.45F);
        float k = in.climb;
        xr[RARM] = mix(xr[RARM], 0.45F * c - 2.55F, k);
        xr[LARM] = mix(xr[LARM], 0.0F - 2.55F - 0.45F * c, k);
        zr[RARM] = mix(zr[RARM], 0.12F, k);
        zr[LARM] = mix(zr[LARM], 0.0F - 0.12F, k);
        xr[RLEG] = mix(xr[RLEG], 0.0F - 0.55F - 0.45F * c, k);
        xr[LLEG] = mix(xr[LLEG], 0.45F * c - 0.55F, k);
    }

    private static float idleX(In in, float side) {
        return side * (float) Math.sin(in.age * 0.067F) * 0.035F;
    }

    private static float idleZ(In in) {
        return (float) Math.cos(in.age * 0.09F) * 0.03F + 0.02F;
    }

    /** Legs swung opposite each other by {@code legA}. */
    private void legs(float sw, float legA) {
        xr[RLEG] = sw * legA;
        xr[LLEG] = 0.0F - sw * legA;
    }

    private void natural(In in, float sw, float step, float spd) {
        legs(sw, (0.88F + 0.14F * in.sprint) * spd);
        float swl = (float) Math.cos(in.pos * 0.6662F - 0.32F);
        float armA = (0.62F + 0.24F * in.sprint) * spd;
        xr[RARM] = idleX(in, 1.0F) + 0.06F * in.sprint * spd - swl * armA;
        xr[LARM] = swl * armA + idleX(in, 0.0F - 1.0F) + 0.06F * in.sprint * spd;
        zr[RARM] = 0.05F + 0.035F * spd + idleZ(in);
        zr[LARM] = 0.0F - 0.05F - 0.035F * spd - idleZ(in);
        yr[BODY] = 0.05F * sw * spd;
        if (!in.crouching) leanRigid(0.035F * spd + 0.09F * in.sprint * spd);
        if (in.bounce) bob(0.0F - 0.45F * step * spd);
        airPose(in, 0.0F - 0.45F, 0.0F - 0.1F, 0.0F - 0.25F);
    }

    private void athletic(In in, float sw, float step, float spd) {
        legs(sw, (0.95F + 0.2F * in.sprint) * spd);
        float swl = (float) Math.cos(in.pos * 0.6662F - 0.22F);
        float armA = (0.7F + 0.3F * in.sprint) * spd;
        xr[RARM] = 0.12F * spd + idleX(in, 1.0F) - swl * armA;
        xr[LARM] = swl * armA + 0.12F * spd + idleX(in, 0.0F - 1.0F);
        zr[RARM] = 0.025F + idleZ(in) * 0.5F;
        zr[LARM] = 0.0F - 0.025F - idleZ(in) * 0.5F;
        yr[BODY] = 0.09F * sw * spd;
        yr[HEAD] -= 0.04F * sw * spd;
        if (!in.crouching) leanRigid((0.06F + 0.16F * in.sprint) * spd);
        if (in.bounce) bob(0.0F - (0.4F + 0.45F * in.sprint) * step * spd);
        airPose(in, 0.0F - 0.75F, 0.0F - 0.15F, 0.0F - 0.55F);
    }

    private void relaxed(In in, float sw, float step, float spd) {
        legs(sw, 0.72F * spd);
        float swl = (float) Math.cos(in.pos * 0.6662F - 0.45F);
        float armA = 0.36F * spd;
        xr[RARM] = idleX(in, 1.0F) * 1.3F - swl * armA;
        xr[LARM] = swl * armA + idleX(in, 0.0F - 1.0F) * 1.3F;
        zr[RARM] = 0.09F + idleZ(in) * 1.3F;
        zr[LARM] = 0.0F - 0.09F - idleZ(in) * 1.3F;
        float shift = (float) Math.sin(in.pos * 0.6662F) * spd;
        roll(0.03F * shift);
        zr[HEAD] -= 0.03F * shift;
        yr[BODY] = 0.035F * sw * spd;
        if (in.bounce) bob(0.0F - 0.3F * step * spd);
        airPose(in, 0.0F - 0.35F, 0.0F - 0.05F, 0.0F - 0.15F);
    }

    private void confident(In in, float sw, float step, float spd) {
        legs(sw, (0.86F + 0.16F * in.sprint) * spd);
        float swl = (float) Math.cos(in.pos * 0.6662F - 0.3F);
        float armA = (0.72F + 0.22F * in.sprint) * spd;
        xr[RARM] = idleX(in, 1.0F) - swl * armA;
        xr[LARM] = swl * armA + idleX(in, 0.0F - 1.0F);
        zr[RARM] = 0.13F + 0.04F * spd + idleZ(in);
        zr[LARM] = 0.0F - 0.13F - 0.04F * spd - idleZ(in);
        yr[BODY] = 0.11F * sw * spd;
        yr[HEAD] -= 0.06F * sw * spd;
        if (!in.crouching) {
            xr[BODY] -= 0.05F * (1.0F - in.sprint);
            leanRigid(0.08F * in.sprint * spd);
        }
        if (in.bounce) bob(0.0F - 0.4F * step * spd);
        airPose(in, 0.0F - 0.5F, 0.0F - 0.1F, 0.0F - 0.3F);
    }

    private void ninja(In in, float sw, float spd) {
        float lean = in.crouching ? 0.0F : (0.1F + 0.32F * in.sprint) * spd * (1.0F - in.climb);
        float trail = 0.25F + 1.05F * in.sprint * spd;
        xr[RARM] = trail + sw * 0.15F * spd;
        xr[LARM] = trail - sw * 0.15F * spd;
        zr[RARM] = 0.1F + 0.12F * in.sprint;
        zr[LARM] = 0.0F - 0.1F - 0.12F * in.sprint;
        legs(sw, (1.2F + 0.5F * in.sprint) * spd);
        xr[RLEG] = mix(xr[RLEG], mix(0.0F - 1.15F, 0.0F - 0.35F, in.fall), in.air);
        xr[LLEG] = mix(xr[LLEG], mix(0.0F - 0.35F, 0.25F, in.fall), in.air);
        xr[RARM] = mix(xr[RARM], 1.35F, in.air);
        xr[LARM] = mix(xr[LARM], 1.35F, in.air);
        zr[RARM] = mix(zr[RARM], 0.3F, in.air);
        zr[LARM] = mix(zr[LARM], 0.0F - 0.3F, in.air);
        crouchShape(lean);
    }

    private void levitate(In in, float spd) {
        float bob = (float) Math.sin(in.age * 0.08F);
        if (!in.crouching) crouchShape(0.12F * spd);
        xr[RLEG] = 0.14F + 0.28F * spd + 0.05F * bob;
        xr[LLEG] = 0.2F + 0.28F * spd - 0.05F * bob;
        zr[RLEG] = 0.035F;
        zr[LLEG] = 0.0F - 0.035F;
        float open = 0.22F + 0.06F * (float) Math.sin(in.age * 0.1F) + 0.35F * in.air;
        zr[RARM] = open;
        zr[LARM] = 0.0F - open;
        xr[RARM] = 0.08F * spd + 0.05F * bob;
        xr[LARM] = 0.08F * spd - 0.05F * bob;
        bob(0.0F - 1.3F - bob * 1.1F);
    }

    private void zombie(In in, float sw, float spd) {
        float sway = (float) Math.sin(in.age * 0.067F) * 0.05F;
        xr[RARM] = mix(sway + sw * 0.05F * spd - 1.45F, 0.0F - 2.0F, in.air);
        xr[LARM] = mix(0.0F - 1.45F - sway - sw * 0.05F * spd, 0.0F - 2.0F, in.air);
        zr[RARM] = 0.0F - 0.06F;
        zr[LARM] = 0.06F;
        legs(sw, 0.7F * spd);
        zr[HEAD] = (float) Math.sin(in.age * 0.05F) * 0.12F;
    }

    private void robot(In in, float spd) {
        float q = Math.signum((float) Math.sin(in.pos * 0.6662F)) * Math.min(1.0F, spd * 3.0F);
        xr[RARM] = mix(0.0F - 0.55F * q, 0.0F - 3.05F, in.air);
        xr[LARM] = mix(0.55F * q, 0.0F - 3.05F, in.air);
        xr[RLEG] = mix(0.5F * q, 0.0F, in.air);
        xr[LLEG] = mix(0.0F - 0.5F * q, 0.0F, in.air);
        zr[RARM] = 0.0F;
        zr[LARM] = 0.0F;
        // The head turns in steps of a sixteenth of a turn.
        yr[HEAD] = Math.round(yr[HEAD] / 0.3927F) * 0.3927F;
    }

    private void swagger(In in, float sw, float spd) {
        float a = (1.0F + 0.3F * in.sprint) * spd;
        xr[RARM] = mix(sw * a, 0.0F - 0.6F, in.air);
        xr[LARM] = mix(0.0F - sw * a, 0.6F, in.air);
        zr[RARM] = 0.15F + 0.12F * Math.abs(sw) * spd;
        zr[LARM] = 0.0F - 0.15F - 0.12F * Math.abs(sw) * spd;
        legs(sw, 0.0F - 1.1F * spd);
        zr[BODY] = 0.07F * sw * spd;
        yr[BODY] = 0.14F * sw * spd;
        zr[HEAD] = 0.0F - 0.05F * sw * spd;
    }
}
