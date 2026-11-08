package com.mineux.visuals.self.model;

/**
 * The dress: a little figure in a lilac dress with a white collar, brown hair in bunches and a pink
 * bow, skipping as she walks -- outlined like a cartoon. 1.36 blocks tall.
 */
public final class Dress extends Rig {

    public static final Dress INSTANCE = new Dress();

    private static final int DRESS = 0xFF9B7BEA;
    private static final int DRESS_DARK = 0xFF7A5BCB;
    private static final int COLLAR = 0xFFFFFFFF;
    private static final int HAIR = 0xFF5A3825;
    private static final int SKIN = 0xFFF9D1A8;
    private static final int BOW = 0xFFFF6FA3;
    private static final int BOW_KNOT = 0xFFE24E86;
    private static final int BLUSH = 0xFFFF9EB0;
    private static final int SOCK = 0xFFF4F4F8;
    private static final int SHOE = 0xFF7A2E45;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF0A0A0A;
    private static final int MOUTH = 0xFFB0384F;
    private static final double T = 0.022;
    private static final double HIP = 0.27;
    private static final double LEG_X = 0.09;
    private static final double ARM_X = 0.19;
    private static final double SHOULDER = 0.31;
    private static final double NECK = 0.38;
    private static final double LEAN = 0.35;
    private static final double ROLL = 0.22;

    private static final double[] ANCHORS = {0.0, 0.42, 0.0, 0.0, 0.0, 0.0, 0.19, 0.47, 0.0, 0.0 - 0.19, 0.47, 0.0, 0.09, 0.13, 0.0, 0.0 - 0.09, 0.13, 0.0};

    private Mesh[] meshes;

    private Dress() {}

    @Override
    double[] anchors() {
        return ANCHORS;
    }

    @Override
    public double height() {
        return 1.36;
    }

    @Override
    double faceMinZ() {
        return 0.22;
    }

    @Override
    Mesh face() {
        return meshes()[2];
    }

    @Override
    Mesh mesh(int part) {
        return pick(meshes(), part);
    }

    private Mesh[] meshes() {
        if (meshes != null) return meshes;
        MeshBuilder b = new MeshBuilder();
        b.outlineColor(BLACK);
        Mesh[] ms = new Mesh[5];

        // The dress, its hem, the collar and a bow at the neck.
        b.reset();
        b.outlined(T, () -> b.frustum(0.0 - 0.1, 0.27, 0.34, 0.135, 20, DRESS));
        b.outlined(0.012, () -> b.ellipsoid(0.0, 0.0 - 0.075, 0.0, 0.275, 0.03, 0.275, 20, 6, DRESS_DARK));
        b.outlined(0.014, () -> b.ellipsoid(0.0, 0.335, 0.01, 0.16, 0.045, 0.15, 16, 8, COLLAR));
        b.outlined(0.008, () -> b.sphere(0.0, 0.3, 0.145, 0.032, 8, 6, BOW));
        ms[0] = b.build();

        // The hair, its bunches and the bow on top.
        b.reset();
        b.translate(0.0, 0.3, 0.0);
        b.outlined(T, () -> b.ellipsoid(0.0, 0.0, 0.0 - 0.02, 0.33, 0.32, 0.31, 24, 14, HAIR));
        for (int s = 0 - 1; s <= 1; s += 2) {
            double x = s * 0.25;
            b.outlined(T, () -> b.ellipsoid(x, 0.0 - 0.12, 0.06, 0.1, 0.19, 0.15, 12, 8, HAIR));
        }
        bow(b);
        ms[1] = b.build();

        // The face: skin, the fringe, eyes, cheeks and a mouth.
        b.reset();
        b.translate(0.0, 0.3, 0.0);
        b.outlined(0.014, () -> b.ellipsoid(0.0, 0.0 - 0.06, 0.13, 0.215, 0.21, 0.18, 18, 12, SKIN));
        b.outlined(0.012, () -> b.ellipsoid(0.0, 0.105, 0.16, 0.25, 0.085, 0.15, 18, 8, HAIR));
        for (int s = 0 - 1; s <= 1; s += 2) {
            double x = s * 0.09;
            b.ellipsoid(x, 0.0 - 0.075, 0.29, 0.034, 0.048, 0.03, 10, 6, BLACK);
            b.sphere(x - s * 0.01 + 0.004, 0.0 - 0.058, 0.318, 0.011, 6, 4, WHITE);
            b.ellipsoid(s * 0.135, 0.0 - 0.14, 0.255, 0.045, 0.022, 0.025, 10, 4, BLUSH);
        }
        b.ellipsoid(0.0, 0.0 - 0.18, 0.276, 0.028, 0.014, 0.02, 8, 4, MOUTH);
        ms[2] = b.build();

        // An arm: the sleeve's puff, the arm, the hand.
        b.reset();
        b.outlined(T, () -> b.ellipsoid(0.0, 0.0 - 0.15, 0.0, 0.052, 0.13, 0.052, 10, 8, SKIN));
        b.outlined(T, () -> b.sphere(0.0, 0.0 - 0.28, 0.0, 0.062, 10, 8, SKIN));
        b.outlined(T, () -> b.ellipsoid(0.0, 0.0 - 0.035, 0.0, 0.088, 0.08, 0.088, 12, 8, DRESS));
        ms[3] = b.build();

        // A leg: the sock and the shoe.
        b.reset();
        b.outlined(T, () -> b.cylinder(0.0 - 0.19, 0.02, 0.06, 12, SOCK));
        b.outlined(T, () -> b.ellipsoid(0.0, 0.0 - 0.215, 0.035, 0.082, 0.05, 0.13, 12, 8, SHOE));
        ms[4] = b.build();
        meshes = ms;
        return ms;
    }

    private static void bow(MeshBuilder b) {
        for (int s = 0 - 1; s <= 1; s += 2) {
            b.resetTransform();
            b.translate(s * 0.115, 0.605, 0.0 - 0.04);
            b.rotZ(s * (0.0 - 0.35));
            b.outlined(0.014, () -> b.ellipsoid(0.0, 0.0, 0.0, 0.11, 0.07, 0.05, 12, 8, BOW));
        }
        b.resetTransform();
        b.outlined(0.012, () -> b.sphere(0.0, 0.61, 0.0 - 0.03, 0.045, 10, 6, BOW_KNOT));
    }

    @Override
    public void pose(double walkPhase, double walkAmp, double attack, boolean sneaking, double headYaw, double headPitch, Bones out) {
        double stride = Math.sin(walkPhase) * walkAmp * 0.6;
        double skip = Math.abs(Math.sin(walkPhase)) * walkAmp * 0.025;
        double lean = sneaking ? LEAN : 0.0;
        double swing = Math.sin(attack * Math.PI);
        double[] torso = out.torso;
        Mat.setTranslation(torso, 0.0, HIP + skip, 0.0);
        Mat.rotX(torso, lean);
        Mat.rotZ(torso, Math.sin(walkPhase) * walkAmp * 0.05);
        Mat.setTranslation(out.head, 0.0, HIP + skip + NECK * Math.cos(lean), NECK * Math.sin(lean));
        Mat.rotY(out.head, headYaw);
        Mat.rotX(out.head, headPitch);
        Mat.rotZ(out.head, Math.sin(walkPhase * 0.5) * walkAmp * 0.06);
        arm(torso, out.armR, ARM_X, SHOULDER, ROLL, 0.0 - stride * 0.8 - swing * 1.6);
        arm(torso, out.armL, 0.0 - ARM_X, SHOULDER, 0.0 - ROLL, stride * 0.8);
        Mat.setTranslation(out.legR, LEG_X, HIP, 0.0);
        Mat.rotX(out.legR, stride);
        Mat.setTranslation(out.legL, 0.0 - LEG_X, HIP, 0.0);
        Mat.rotX(out.legL, 0.0 - stride);
    }
}
