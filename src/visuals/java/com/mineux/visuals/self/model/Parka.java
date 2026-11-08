package com.mineux.visuals.self.model;

/**
 * The parka: a little round figure in an orange parka, the hood drawn tight round its face, with
 * mittens and boots -- outlined like a cartoon. 1.32 blocks tall.
 */
public final class Parka extends Rig {

    public static final Parka INSTANCE = new Parka();

    private static final int ORANGE = 0xFFF98B1D;
    private static final int ORANGE_RING = 0xFFD86A0C;
    private static final int BROWN = 0xFF6E4324;
    private static final int SKIN = 0xFFF9D1A8;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF0A0A0A;
    private static final double T = 0.024;
    private static final double HIP = 0.27;
    private static final double LEG_X = 0.1;
    private static final double ARM_X = 0.27;
    private static final double SHOULDER = 0.3;
    private static final double NECK = 0.4;
    private static final double LEAN = 0.4;
    private static final double ROLL = 0.28;

    private static final double[] ANCHORS = {0.0, 0.47, 0.0, 0.0, 0.0, 0.0, 0.27, 0.47, 0.0, 0.0 - 0.27, 0.47, 0.0, 0.1, 0.13, 0.0, 0.0 - 0.1, 0.13, 0.0};

    private Mesh[] meshes;

    private Parka() {}

    @Override
    double[] anchors() {
        return ANCHORS;
    }

    @Override
    public double height() {
        return 1.32;
    }

    @Override
    double faceMinZ() {
        return 0.3;
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

        // The torso: the parka's body, and its zip.
        b.reset();
        double cy = 0.19, ry = 0.24, rz = 0.21;
        b.outlined(T, () -> b.ellipsoid(0.0, cy, 0.0, 0.27, ry, rz, 20, 12, ORANGE));
        int n = 8;
        double[] spine = new double[(n + 1) * 3];
        for (int i = 0; i <= n; i++) {
            double y = cy - 0.2 + 0.36 * i / n;
            double k = (y - cy) / ry;
            spine[i * 3] = 0.0;
            spine[i * 3 + 1] = y;
            spine[i * 3 + 2] = rz * Math.sqrt(Math.max(0.0, 1.0 - k * k)) + 0.006;
        }
        b.strip(spine, 0.014, BLACK);
        ms[0] = b.build();

        // The hood.
        b.reset();
        b.translate(0.0, 0.28, 0.0);
        b.outlined(T, () -> b.ellipsoid(0.0, 0.0, 0.0, 0.38, 0.36, 0.36, 24, 14, ORANGE));
        ms[1] = b.build();

        // The face in the hood's opening: the ring, the face, the eyes.
        b.reset();
        b.translate(0.0, 0.28, 0.0);
        double fy = 0.0 - 0.01;
        b.outlined(0.016, () -> b.ellipsoid(0.0, fy, 0.27, 0.255, 0.175, 0.095, 18, 10, ORANGE_RING));
        b.outlined(0.014, () -> b.ellipsoid(0.0, fy, 0.29, 0.185, 0.12, 0.09, 16, 10, SKIN));
        for (int s = 0 - 1; s <= 1; s += 2) {
            double x = s * 0.088;
            b.outlined(0.01, () -> b.ellipsoid(x, fy + 0.005, 0.31, 0.082, 0.088, 0.07, 12, 8, WHITE));
        }
        for (int s = 0 - 1; s <= 1; s += 2) b.sphere(s * 0.068, fy + 0.005, 0.376, 0.02, 8, 5, BLACK);
        ms[2] = b.build();

        // An arm and its mitten.
        b.reset();
        b.outlined(T, () -> b.ellipsoid(0.0, 0.0 - 0.13, 0.0, 0.075, 0.155, 0.075, 12, 8, ORANGE));
        b.outlined(T, () -> b.sphere(0.0, 0.0 - 0.3, 0.0, 0.085, 12, 8, BROWN));
        ms[3] = b.build();

        // A leg and its boot.
        b.reset();
        b.outlined(T, () -> b.cylinder(0.0 - 0.19, 0.02, 0.09, 12, ORANGE));
        b.outlined(T, () -> b.ellipsoid(0.0, 0.0 - 0.215, 0.04, 0.1, 0.055, 0.15, 12, 8, BROWN));
        ms[4] = b.build();
        meshes = ms;
        return ms;
    }

    @Override
    public void pose(double walkPhase, double walkAmp, double attack, boolean sneaking, double headYaw, double headPitch, Bones out) {
        double stride = Math.sin(walkPhase) * walkAmp * 0.7;
        double lean = sneaking ? LEAN : 0.0;
        double swing = Math.sin(attack * Math.PI);
        double[] torso = out.torso;
        Mat.setTranslation(torso, 0.0, HIP, 0.0);
        Mat.rotX(torso, lean);
        Mat.setTranslation(out.head, 0.0, HIP + NECK * Math.cos(lean), NECK * Math.sin(lean));
        Mat.rotY(out.head, headYaw);
        Mat.rotX(out.head, headPitch);
        arm(torso, out.armR, ARM_X, SHOULDER, ROLL, 0.0 - stride - swing * 1.6);
        arm(torso, out.armL, 0.0 - ARM_X, SHOULDER, 0.0 - ROLL, stride);
        Mat.setTranslation(out.legR, LEG_X, HIP, 0.0);
        Mat.rotX(out.legR, stride);
        Mat.setTranslation(out.legL, 0.0 - LEG_X, HIP, 0.0);
        Mat.rotX(out.legL, 0.0 - stride);
    }
}
