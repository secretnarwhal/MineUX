package com.mineux.visuals.hand;

import java.util.Arrays;

/**
 * Swings driven by a spring: each frame works out where the hand should be for the mode and the
 * swing progress, and a damped spring carries it there, so a swing cut short or chained into the
 * next one moves on smoothly instead of jumping. Some modes count the swings in a row: a combo
 * of three, a dance of four, three charges and a slam, a spin that builds with each swing.
 * <p>
 * {@link #POSE}: x, y, z offsets, then yaw, roll, chop and flip in degrees.
 * Modes, by index: smooth, stab, slice, windmill, combo, dance, charge, momentum, whirlwind.
 */
final class SwingEngine {

    static final int N = 7;
    static final float[] POSE = new float[N];

    private static final int SMOOTH = 0, STAB = 1, SLICE = 2, WINDMILL = 3, COMBO = 4,
        DANCE = 5, CHARGE = 6, MOMENTUM = 7, WHIRLWIND = 8;

    /** How long after a swing the next still chains onto it, seconds. */
    private static final float CHAIN = 0.75f;

    private static final float[] VEL = new float[N];
    private static final float[] TARGET = new float[N];
    private static final float[] FROM = new float[N];
    private static final float[] TO = new float[N];

    private static int mode = 0 - 1;
    private static float lastP;
    private static int combo;
    private static float sinceEnd = 99f;
    private static boolean swinging;
    private static float momentum;
    private static float spin;
    private static float whirl;
    private static float time;

    private SwingEngine() {}

    private static void reset() {
        Arrays.fill(POSE, 0f);
        Arrays.fill(VEL, 0f);
        Arrays.fill(TARGET, 0f);
        Arrays.fill(FROM, 0f);
        Arrays.fill(TO, 0f);
        combo = 0;
        sinceEnd = 99f;
        swinging = false;
        lastP = 0f;
        momentum = 0f;
        spin = 0f;
        whirl = 0f;
    }

    static void step(int m, float p, float dt, float strength) {
        if (m != mode) {
            reset();
            mode = m;
        }
        dt = Math.max(0f, Math.min(dt, 0.05f));
        time += dt;
        boolean hit = p > 0.001f && lastP <= 0.001f || p < lastP - 0.25f && p > 0f;
        if (hit) {
            combo = swinging || sinceEnd < CHAIN ? combo + 1 : 0;
            swinging = true;
            System.arraycopy(POSE, 0, FROM, 0, N);
            if (mode == MOMENTUM) momentum = Math.min(6f, momentum + 1f);
            if (mode == WHIRLWIND) whirl += 90f;
        }
        if (swinging && p <= 0.001f && !hit) {
            swinging = false;
            sinceEnd = 0f;
        }
        if (!swinging) sinceEnd += dt;
        lastP = p;
        compute(p, dt, 0.75f + 0.5f * strength);
        spring(dt);
    }

    /** The spring, in steps of at most a 240th of a second; the two turning angles take the short way round. */
    private static void spring(float dt) {
        for (int i = 5; i <= 6; i++) {
            while (POSE[i] - TARGET[i] > 180f) POSE[i] -= 360f;
            while (POSE[i] - TARGET[i] < 0f - 180f) POSE[i] += 360f;
        }
        boolean soft = mode == SMOOTH;
        float omega = soft ? 20f : 34f;
        float zeta = soft ? 0.55f : 0.82f;
        int sub = Math.max(1, (int) Math.ceil(dt * 240f));
        float h = dt / sub;
        for (int s = 0; s < sub; s++) {
            for (int i = 0; i < N; i++) {
                float acc = omega * omega * (TARGET[i] - POSE[i]) - 2f * zeta * omega * VEL[i];
                VEL[i] += acc * h;
                POSE[i] += VEL[i] * h;
            }
        }
    }

    private static void compute(float p, float dt, float amp) {
        float sq = (float) Math.sqrt(p);
        float sp = swinging ? p : 1f;
        float b = bump(p);
        if (mode == SMOOTH) {
            set(TARGET, (0f - 0.32f) * bump(sq) * amp, 0.2f * (float) Math.sin(sq * 2f * Math.PI), (0f - 0.2f) * b,
                (0f - 20f) * (float) Math.sin(p * p * Math.PI), (0f - 20f) * bump(sq), (0f - 80f) * bump(sq) * amp, 0f);
        } else if (mode == STAB) {
            float t = p < 0.35f ? outCubic(p / 0.35f) : 1f - smooth((p - 0.35f) / 0.65f);
            set(TARGET, (0f - 0.08f) * t, 0.06f * t, (0f - 0.42f) * t * amp, (0f - 12f) * t, 70f * t, 12f * t, 0f);
        } else if (mode == SLICE) {
            float e = inOut(p);
            set(TARGET, (0.15f - 0.55f * e) * b * amp, 0.05f * b, (0f - 0.18f) * b,
                (55f - 120f * e) * b * amp, (0f - 45f) * b, (0f - 35f) * b, 0f);
        } else if (mode == WINDMILL) {
            set(TARGET, (0f - 0.12f) * b, 0.1f * b, (0f - 0.15f) * b, 0f, (0f - 10f) * b, (0f - 360f) * inOut(p), 0f);
        } else if (mode == COMBO) {
            if (!settle()) comboSlash(p, sp, b, amp);
        } else if (mode == DANCE) {
            if (!settle()) bladeDance(sp, b, amp);
        } else if (mode == CHARGE) {
            if (!settle()) heavyCharge(sp, b, amp);
        } else if (mode == MOMENTUM) {
            momentum(dt, b, amp);
        } else if (mode == WHIRLWIND) {
            whirlwind(b, amp);
        } else {
            Arrays.fill(TARGET, 0f);
        }
    }

    private static void comboSlash(float p, float sp, float b, float amp) {
        int st = combo % 3;
        if (st == 0) set(TO, (0f - 0.26f) * amp, 0f - 0.04f, 0f - 0.08f, (0f - 38f) * amp, 32f, (0f - 68f) * amp, 0f);
        else if (st == 1) set(TO, 0.12f * amp, 0.1f, 0f - 0.06f, 42f * amp, 0f - 38f, 0f - 18f, 0f);
        else set(TO, 0f - 0.05f, 0f - 0.06f, 0f - 0.18f, 0f, 0f, (0f - 100f) * amp, 0f - 360f);
        blend(st == 2 ? inOut(sp) : outBack(sp));
        TARGET[2] -= 0.1f * b;
        if (st == 2) TARGET[1] += 0.12f * bump(Math.min(1f, p * 1.6f));
    }

    private static void bladeDance(float sp, float b, float amp) {
        int st = combo % 4;
        if (st == 0) {
            set(TO, 0.02f, 0.04f, 0f - 0.04f, 15f, 10f, 0f - 25f, 0f - 360f);
            blend(inOut(sp));
            TARGET[0] -= 0.12f * b;
        } else if (st == 1) {
            set(TO, 0f - 0.05f, 0.04f, 0f - 0.1f, 0f - 8f, 80f, 10f, 0f - 360f);
            blend(outBack(sp));
            TARGET[2] -= 0.3f * b * amp;
        } else if (st == 2) {
            set(TO, (0f - 0.3f) * amp, 0f, 0f - 0.08f, (0f - 50f) * amp, 20f, 0f - 55f, 0f - 360f);
            blend(outBack(sp));
        } else {
            set(TO, 0f, 0f, 0f, 0f, 0f, (0f - 90f) * amp, 0f - 1080f);
            blend(inOut(sp));
            TARGET[1] += 0.1f * b;
            TARGET[2] -= 0.15f * b;
        }
    }

    private static void heavyCharge(float sp, float b, float amp) {
        int st = combo % 4;
        if (st < 3) {
            float c = st + 1;
            set(TO, (0f - 0.04f) * c, 0.05f * c, 0.01f * c, (0f - 6f) * c, 12f * c * amp, 0f, 0f);
            blend(outBack(sp));
            TARGET[5] -= 14f * b;
            float shake = 0.0045f * c * c;
            TARGET[0] += shake * (float) Math.sin(time * 61f);
            TARGET[1] += shake * (float) Math.sin(time * 47f + 1f);
            return;
        }
        set(TO, 0f - 0.04f, 0f - 0.16f, 0f - 0.25f, 0f - 6f, 0f - 12f, (0f - 115f) * amp, 0f - 90f);
        blend(sp < 0.45f ? outCubic(sp / 0.45f) : 1f);
        float rec = smooth((sp - 0.55f) / 0.45f);
        for (int i = 0; i < N; i++) TARGET[i] *= 1f - 0.75f * rec;
    }

    private static void momentum(float dt, float b, float amp) {
        momentum = Math.max(0f, momentum - dt * (swinging ? 0.4f : 1.3f));
        spin -= (float) Math.pow(momentum, 1.35) * 120f * dt;
        if (momentum < 0.05f) spin = Math.round(spin / 360f) * 360f;
        if (spin < 0f - 36000f) spin += 36000f;
        float m = momentum / 6f;
        set(TARGET, (0f - 0.06f) * m - 0.1f * b, 0.05f * m + 0.04f * b, (0f - 0.1f) * m - 0.12f * b,
            (0f - 10f) * m, 20f * m, (0f - 20f) * m * amp - 30f * b, spin);
    }

    private static void whirlwind(float b, float amp) {
        if (!swinging && sinceEnd > 0.95f) whirl = Math.round(whirl / 360f) * 360f;
        if (whirl > 36000f) whirl -= 36000f;
        float s = sinD(whirl);
        set(TARGET, 0.18f * s * amp - 0.08f * b, 0.06f * b, (0f - 0.06f) * (1f - cosD(whirl)) - 0.1f * b,
            30f * s, (0f - 15f) * b + 25f * s, (0f - 50f) * b * amp, 0f);
    }

    /** After a chain has ended: back to rest, and the count starts over. True while settling. */
    private static boolean settle() {
        if (swinging || sinceEnd < CHAIN) return false;
        float k = smooth((sinceEnd - CHAIN) / 0.35f);
        for (int i = 0; i < N; i++) TARGET[i] = TO[i] * (1f - k);
        if (k >= 1f) {
            combo = 0;
            Arrays.fill(TO, 0f);
        }
        return true;
    }

    private static void blend(float e) {
        for (int i = 0; i < N; i++) TARGET[i] = FROM[i] + (TO[i] - FROM[i]) * e;
    }

    private static void set(float[] o, float x, float y, float z, float yaw, float roll, float chop, float flip) {
        o[0] = x;
        o[1] = y;
        o[2] = z;
        o[3] = yaw;
        o[4] = roll;
        o[5] = chop;
        o[6] = flip;
    }

    private static float clamp01(float t) {
        return t < 0f ? 0f : Math.min(t, 1f);
    }

    private static float inOut(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(2f - 2f * t, 3.0) / 2f;
    }

    private static float smooth(float t) {
        t = clamp01(t);
        return t * t * (3f - 2f * t);
    }

    private static float outBack(float t) {
        t = clamp01(t) - 1f;
        float c = 1.9f;
        return 1f + (c + 1f) * t * t * t + c * t * t;
    }

    private static float outCubic(float t) {
        t = 1f - clamp01(t);
        return 1f - t * t * t;
    }

    private static float bump(float t) {
        return (float) Math.sin(Math.PI * clamp01(t));
    }

    private static float sinD(float deg) {
        return (float) Math.sin(Math.toRadians(deg));
    }

    private static float cosD(float deg) {
        return (float) Math.cos(Math.toRadians(deg));
    }
}
