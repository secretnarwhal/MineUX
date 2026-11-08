package com.mineux.visuals.cosmetics;

/**
 * Where on a body a cosmetic hangs, as a frame: an origin and right/up/forward axes in world
 * space. Ported from Clarity, where this was an enum; here the places are int constants, since
 * the obfuscation a host may apply renames enum constants.
 */
public final class Attach {

    public static final int HEAD = 0;
    public static final int BACK = 1;
    public static final int FEET = 2;
    public static final int CENTER = 3;
    public static final int HAND = 4;

    public static final double HEAD_HALF = 0.234;
    public static final double NECK_TO_EYE = 0.214;
    public static final double HEAD_TOP = 0.234;
    public static final double EYE_STAND = 1.62;
    public static final double EYE_SNEAK = 1.27;
    public static final double EYE_PRONE = 0.4;
    public static final double BACK_OFFSET = 0.14;
    public static final double BACK_HEIGHT = 0.8;

    private Attach() {}

    public static Frame frame(int where, Subject s) {
        if (where == HEAD) return head(s);
        if (where == BACK) return back(s);
        if (where == CENTER) return upright(s, s.x, s.y + s.height * 0.5, s.z);
        if (where == HAND) return hand(s);
        return upright(s, s.x, s.y, s.z);
    }

    private static Frame head(Subject s) {
        double yaw = Math.toRadians(s.headYawDeg);
        double pitch = Math.toRadians(Math.max(0.0 - 89.0, Math.min(89.0, s.headPitchDeg))) * s.followTilt;
        double cp = Math.cos(pitch);
        double sp = Math.sin(pitch);
        double fx = 0.0 - Math.sin(yaw) * cp;
        double fy = 0.0 - sp;
        double fz = Math.cos(yaw) * cp;
        double rx = 0.0 - Math.cos(yaw);
        double ry = 0.0;
        double rz = 0.0 - Math.sin(yaw);
        double ux = ry * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - ry * fx;
        double neckY = s.y + s.eyeHeight() - NECK_TO_EYE;
        return new Frame(s.x + ux * HEAD_HALF, neckY + uy * HEAD_HALF, s.z + uz * HEAD_HALF, rx, ry, rz, ux, uy, uz, fx, fy, fz);
    }

    private static Frame back(Subject s) {
        double yaw = Math.toRadians(s.bodyYawDeg);
        double sinY = 0.0 - Math.sin(yaw);
        double cosY = Math.cos(yaw);
        if (!s.swimming && !s.fallFlying) {
            double heightMul = s.sneaking ? 0.72 : BACK_HEIGHT;
            return new Frame(s.x - sinY * BACK_OFFSET, s.y + s.height * heightMul, s.z - cosY * BACK_OFFSET,
                0.0 - cosY, 0.0, sinY, 0.0, 1.0, 0.0, sinY, 0.0, cosY);
        }
        double pitch = Math.toRadians(Math.max(0.0 - 89.0, Math.min(89.0, s.headPitchDeg)) * s.followTilt);
        double cp = Math.cos(pitch);
        double sp = Math.sin(pitch);
        double ffx = sinY * cp;
        double ffy = 0.0 - sp;
        double ffz = cosY * cp;
        double rrx = 0.0 - cosY;
        double rry = 0.0;
        double rrz = 0.0 - sinY;
        double uux = rry * ffz - rrz * ffy;
        double uuy = rrz * ffx - rrx * ffz;
        double uuz = rrx * ffy - rry * ffx;
        double ox = s.x - ffx * BACK_OFFSET;
        double oy = s.y + EYE_PRONE + uuy * 0.15;
        double oz = s.z - ffz * BACK_OFFSET;
        return new Frame(ox, oy, oz, rrx, rry, rrz, uux, uuy, uuz, ffx, ffy, ffz);
    }

    private static Frame upright(Subject s, double x, double y, double z) {
        double yaw = Math.toRadians(s.bodyYawDeg);
        double fx = 0.0 - Math.sin(yaw);
        double fz = Math.cos(yaw);
        return new Frame(x, y, z, 0.0 - fz, 0.0, fx, 0.0, 1.0, 0.0, fx, 0.0, fz);
    }

    private static Frame hand(Subject s) {
        Frame f = upright(s, s.x, s.y, s.z);
        double side = 0.37;
        double up = s.height * 0.42;
        return new Frame(f.ox + f.rx * side, f.oy + up, f.oz + f.rz * side, f.rx, f.ry, f.rz, f.ux, f.uy, f.uz, f.fx, f.fy, f.fz);
    }

    /** An origin and three axes; {@link #map} takes (right, up, forward) to world space. */
    public static final class Frame {
        public final double ox;
        public final double oy;
        public final double oz;
        public final double rx;
        public final double ry;
        public final double rz;
        public final double ux;
        public final double uy;
        public final double uz;
        public final double fx;
        public final double fy;
        public final double fz;

        public Frame(double ox, double oy, double oz, double rx, double ry, double rz,
                     double ux, double uy, double uz, double fx, double fy, double fz) {
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
            this.ux = ux;
            this.uy = uy;
            this.uz = uz;
            this.fx = fx;
            this.fy = fy;
            this.fz = fz;
        }

        public double[] map(double[] p) {
            return map(p[0], p[1], p[2]);
        }

        public double[] map(double u, double v, double w) {
            return new double[] {
                ox + rx * u + ux * v + fx * w,
                oy + ry * u + uy * v + fy * w,
                oz + rz * u + uz * v + fz * w
            };
        }

        public static Frame identity() {
            return new Frame(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0);
        }

        public Frame shifted(double du, double dv, double dw) {
            double[] o = map(du, dv, dw);
            return new Frame(o[0], o[1], o[2], rx, ry, rz, ux, uy, uz, fx, fy, fz);
        }
    }

    /** Whom a frame is taken from: where they stand, which way body and head face, their pose. */
    public static final class Subject {
        public final double x;
        public final double y;
        public final double z;
        public final double bodyYawDeg;
        public final double headYawDeg;
        public final double headPitchDeg;
        public final double height;
        public final boolean sneaking;
        public final boolean swimming;
        public final boolean fallFlying;
        public final double followTilt;

        public Subject(double x, double y, double z, double bodyYawDeg, double headYawDeg, double headPitchDeg, double height,
                       boolean sneaking, boolean swimming, boolean fallFlying, double followTilt) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.bodyYawDeg = bodyYawDeg;
            this.headYawDeg = headYawDeg;
            this.headPitchDeg = headPitchDeg;
            this.height = height;
            this.sneaking = sneaking;
            this.swimming = swimming;
            this.fallFlying = fallFlying;
            this.followTilt = followTilt;
        }

        public static Subject standing(double x, double y, double z, double bodyYawDeg, double height) {
            return new Subject(x, y, z, bodyYawDeg, bodyYawDeg, 0.0, height, false, false, false, 1.0);
        }

        public double eyeHeight() {
            if (swimming || fallFlying) return EYE_PRONE;
            return sneaking ? EYE_SNEAK : EYE_STAND;
        }
    }
}
