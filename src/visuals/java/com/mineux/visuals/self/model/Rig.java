package com.mineux.visuals.self.model;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * A built-in player model made of six parts on a skeleton -- torso, head, two arms, two legs --
 * each a {@link Mesh} posed by a matrix, plus a face drawn over the head while it faces the camera.
 * Model units are blocks with y up and the model facing +z; {@link #height} is how tall it stands.
 * <p>
 * The parts are drawn back to front, the legs first, as the outline hulls need with no depth
 * written between them.
 */
public abstract class Rig {

    public static final int TORSO = 0, HEAD = 1, ARM_R = 2, ARM_L = 3, LEG_R = 4, LEG_L = 5;

    /** Six part matrices, one {@link Mat} each. */
    public static final class Bones {
        final double[] torso = new double[12], head = new double[12], armR = new double[12];
        final double[] armL = new double[12], legR = new double[12], legL = new double[12];

        double[] part(int p) {
            if (p == TORSO) return torso;
            if (p == HEAD) return head;
            if (p == ARM_R) return armR;
            if (p == ARM_L) return armL;
            if (p == LEG_R) return legR;
            return legL;
        }
    }

    private final double[] full = new double[12];
    private final int[] order = new int[3];
    private final double[] dist = new double[3];

    /** Where each part's middle is, model units, three to a part (the head's is unused). */
    abstract double[] anchors();

    public abstract double height();

    /** How far forward of the head's middle the camera must be for the face to show. */
    abstract double faceMinZ();

    abstract Mesh face();

    abstract Mesh mesh(int part);

    /** The skeleton for a walk phase and stride, a swing (0-1), a crouch, and the head's turn and tilt, radians. */
    public abstract void pose(double walkPhase, double walkAmp, double attack, boolean sneaking, double headYaw, double headPitch, Bones out);

    private double d2(double[] w, int p) {
        double[] a = anchors();
        int o = p * 3;
        double x = Mat.px(w, a[o], a[o + 1], a[o + 2]);
        double y = Mat.py(w, a[o], a[o + 1], a[o + 2]);
        double z = Mat.pz(w, a[o], a[o + 1], a[o + 2]);
        return x * x + y * y + z * z;
    }

    private void part(VertexConsumer vc, double[] view, Bones bones, int p, float alpha) {
        Mat.mul(view, bones.part(p), full);
        mesh(p).emit(vc, full, alpha);
    }

    /**
     * The model through {@code view} -- model units to the camera's, with the camera at the origin --
     * farthest part first.
     */
    public void draw(VertexConsumer vc, double[] view, Bones bones, float alpha) {
        int far = d2(view, LEG_R) >= d2(view, LEG_L) ? LEG_R : LEG_L;
        part(vc, view, bones, far, alpha);
        part(vc, view, bones, far == LEG_R ? LEG_L : LEG_R, alpha);
        int[] o = order;
        double[] d = dist;
        o[0] = TORSO;
        o[1] = ARM_R;
        o[2] = ARM_L;
        for (int i = 0; i < 3; i++) d[i] = d2(view, o[i]);
        for (int i = 1; i < 3; i++) {
            int oi = o[i];
            double di = d[i];
            int j = i - 1;
            while (j >= 0 && d[j] < di) {
                o[j + 1] = o[j];
                d[j + 1] = d[j];
                j--;
            }
            o[j + 1] = oi;
            d[j + 1] = di;
        }
        for (int i = 0; i < 3; i++) part(vc, view, bones, o[i], alpha);
        part(vc, view, bones, HEAD, alpha);
        if (faceVisible(view, bones.head)) {
            Mat.mul(view, bones.head, full);
            face().emit(vc, full, alpha);
        }
    }

    /** Whether the camera is far enough in front of the head to see the face. */
    private boolean faceVisible(double[] view, double[] head) {
        Mat.mul(view, head, full);
        double s2 = full[0] * full[0] + full[4] * full[4] + full[8] * full[8];
        if (s2 < 1.0E-12) return true;
        double z = (0.0 - (full[2] * full[3] + full[6] * full[7] + full[10] * full[11])) / s2;
        return z >= faceMinZ();
    }

    /** An arm matrix: the torso's, moved out to the shoulder, rolled out and swung. */
    static void arm(double[] torso, double[] a, double u, double shoulder, double roll, double angle) {
        Mat.copy(torso, a);
        Mat.translate(a, u, shoulder, 0.0);
        Mat.rotZ(a, roll);
        Mat.rotX(a, angle);
    }

    /** The parts' meshes for this rig: torso, head, face, arm, leg. */
    static Mesh pick(Mesh[] ms, int part) {
        if (part == TORSO) return ms[0];
        if (part == HEAD) return ms[1];
        if (part == ARM_R || part == ARM_L) return ms[3];
        return ms[4];
    }
}
