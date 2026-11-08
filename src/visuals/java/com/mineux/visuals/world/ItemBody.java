package com.mineux.visuals.world;

/**
 * A dropped item as a tumbling body: its turn as a quaternion {@link #q} (x, y, z, w) and its
 * spin {@link #w} in radians a second about the world's axes.
 * <p>
 * In the air it tumbles, the spin kept up to how fast it is moving and slowed in water or lava.
 * On the ground it rocks onto whichever face is nearest to facing down, as a damped spring, and
 * once it is still it is laid exactly flat and left there -- a flat item always on its face, a
 * block on any side.
 */
final class ItemBody {

    final float[] q = {0.0F, 0.0F, 0.0F, 1.0F};
    final float[] w = {0.0F, 0.0F, 0.0F};
    boolean started;
    boolean resting;
    long last;

    /** A number from 0 to 1 that only depends on {@code p}. */
    static float hash(float p) {
        double x = Math.sin(p * 127.1 + 311.7) * 43758.5453;
        return (float) (x - Math.floor(x));
    }

    void start(boolean onGround, boolean flat, float seed, float spin) {
        started = true;
        float yaw = (float) (hash(seed) * Math.PI * 2.0);
        q[0] = 0.0F;
        q[1] = (float) Math.sin(yaw / 2.0F);
        q[2] = 0.0F;
        q[3] = (float) Math.cos(yaw / 2.0F);
        if (onGround) {
            if (flat) turn(1.0F, 0.0F, 0.0F, (float) (0.0 - Math.PI / 2.0));
            resting = true;
            return;
        }
        turn(1.0F, 0.0F, 0.0F, (float) (hash(seed * 1.7F) * Math.PI * 2.0));
        double a = hash(seed * 3.1F) * Math.PI * 2.0;
        float speed = (5.0F + 5.0F * hash(seed * 5.3F)) * spin;
        w[0] = (float) Math.cos(a) * speed;
        w[1] = (hash(seed * 7.7F) - 0.5F) * speed * 0.4F;
        w[2] = (float) Math.sin(a) * speed;
    }

    /**
     * {@code dt} seconds on. {@code speed} is how fast it moves, blocks a second; {@code zeta}
     * how damped its rocking on the ground is, 1 for none at all past the first.
     */
    void step(float dt, boolean flat, boolean onGround, boolean inFluid, float speed, float spin, float zeta) {
        if (dt <= 0.0F) return;
        if (!onGround) air(dt, inFluid, speed, spin);
        else if (!resting) settle(dt, flat, zeta);
        float mag = length(w[0], w[1], w[2]);
        if (mag > 1.0E-6F) turn(w[0] / mag, w[1] / mag, w[2] / mag, mag * dt);
    }

    private void air(float dt, boolean inFluid, float speed, float spin) {
        resting = false;
        float damp = inFluid ? 2.2F : 0.25F;
        float k = (float) Math.exp((0.0F - damp) * dt);
        w[0] *= k;
        w[1] *= k;
        w[2] *= k;
        float mag = length(w[0], w[1], w[2]);
        float want = Math.min(9.0F, speed * 1.6F) * spin * (inFluid ? 0.25F : 1.0F);
        if (mag < want && mag > 1.0E-4F) {
            float s = want / mag;
            w[0] *= s;
            w[1] *= s;
            w[2] *= s;
        } else if (mag <= 1.0E-4F && want > 0.5F) {
            w[0] = want;
        }
    }

    private void settle(float dt, boolean flat, float zeta) {
        // The body axis nearest to straight up or down: it is the one to lie along.
        float bx = 0.0F;
        float by = 0.0F;
        float bz = 0.0F;
        float bestDot = 0.0F - 2.0F;
        int axes = flat ? 1 : 3;
        for (int i = 0; i < axes; i++) {
            int axis = flat ? 2 : i;
            float[] v = rotate(q, axis == 0 ? 1.0F : 0.0F, axis == 1 ? 1.0F : 0.0F, axis == 2 ? 1.0F : 0.0F);
            for (int sign = 0; sign < 2; sign++) {
                float s = sign == 0 ? 1.0F : 0.0F - 1.0F;
                float d = v[1] * s;
                if (d > bestDot) {
                    bestDot = d;
                    bx = v[0] * s;
                    by = v[1] * s;
                    bz = v[2] * s;
                }
            }
        }
        // The turn that brings it up: about (-bz, 0, bx), by its angle from up.
        float ex = 0.0F - bz;
        float ez = bx;
        float sinA = length(ex, 0.0F, ez);
        float angle = (float) Math.atan2(sinA, Math.max(0.0F - 1.0F, Math.min(1.0F, by)));
        float omega = 15.0F;
        float tx = 0.0F;
        float tz = 0.0F;
        if (sinA > 1.0E-5F) {
            tx = ex / sinA * angle;
            tz = ez / sinA * angle;
        }
        w[0] += (omega * omega * tx - 2.0F * zeta * omega * w[0]) * dt;
        w[2] += (omega * omega * tz - 2.0F * zeta * omega * w[2]) * dt;
        w[1] *= (float) Math.exp((0.0F - 6.0F) * dt);
        if (angle < 0.004F && Math.abs(w[0]) + Math.abs(w[1]) + Math.abs(w[2]) < 0.02F) {
            w[0] = 0.0F;
            w[1] = 0.0F;
            w[2] = 0.0F;
            if (sinA > 1.0E-6F) turn(ex / sinA, 0.0F, ez / sinA, angle);
            resting = true;
        }
    }

    /** How far the box of half sizes {@code hx}, {@code hy}, {@code hz} reaches below its centre, turned. */
    float support(float hx, float hy, float hz) {
        float[] ax = rotate(q, 1.0F, 0.0F, 0.0F);
        float[] ay = rotate(q, 0.0F, 1.0F, 0.0F);
        float[] az = rotate(q, 0.0F, 0.0F, 1.0F);
        return Math.abs(ax[1]) * hx + Math.abs(ay[1]) * hy + Math.abs(az[1]) * hz;
    }

    private static float length(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    static float[] rotate(float[] q, float vx, float vy, float vz) {
        float x = q[0];
        float y = q[1];
        float z = q[2];
        float qw = q[3];
        float tx = 2.0F * (y * vz - z * vy);
        float ty = 2.0F * (z * vx - x * vz);
        float tz = 2.0F * (x * vy - y * vx);
        return new float[] {vx + qw * tx + (y * tz - z * ty), vy + qw * ty + (z * tx - x * tz), vz + qw * tz + (x * ty - y * tx)};
    }

    /** {@link #q} turned by {@code angle} about the world axis (ax, ay, az), on the left. */
    private void turn(float ax, float ay, float az, float angle) {
        float s = (float) Math.sin(angle / 2.0F);
        float c = (float) Math.cos(angle / 2.0F);
        float bx = ax * s;
        float by = ay * s;
        float bz = az * s;
        float x = c * q[0] + bx * q[3] + by * q[2] - bz * q[1];
        float y = c * q[1] - bx * q[2] + by * q[3] + bz * q[0];
        float z = c * q[2] + bx * q[1] - by * q[0] + bz * q[3];
        float qw = c * q[3] - bx * q[0] - by * q[1] - bz * q[2];
        float l = (float) Math.sqrt(x * x + y * y + z * z + qw * qw);
        q[0] = x / l;
        q[1] = y / l;
        q[2] = z / l;
        q[3] = qw / l;
    }
}
