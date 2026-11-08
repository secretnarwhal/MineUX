package com.mineux.visuals.self.model;

import java.util.Arrays;

/**
 * Builds a rig part out of ellipsoids, cylinders and cones, each quad wound to face out. A shape
 * drawn {@link #outlined} is drawn twice: once grown by the outline's thickness, wound inward and
 * in the outline colour -- with back faces culled only its far side shows, round the shape's edge
 * -- and once as itself.
 */
final class MeshBuilder {

    private double hull;
    private int hullColor = 0xFF0A0A0A;
    private final double[] m = Mat.identity();
    private float[] pos = new float[12288];
    private int[] col = new int[4096];
    private int n;
    private final double[] qp = new double[12];
    private final double[] qn = new double[12];
    private final int[] qc = new int[4];

    void reset() {
        n = 0;
        hull = 0.0;
        Mat.identity(m);
    }

    void outlineColor(int c) {
        hullColor = c;
    }

    void resetTransform() {
        Mat.identity(m);
    }

    void translate(double x, double y, double z) {
        Mat.translate(m, x, y, z);
    }

    void rotZ(double a) {
        Mat.rotZ(m, a);
    }

    Mesh build() {
        Mesh mesh = n == 0 ? Mesh.EMPTY : new Mesh(Arrays.copyOf(pos, n * 3), Arrays.copyOf(col, n));
        n = 0;
        return mesh;
    }

    void outlined(double t, Runnable shape) {
        hull = t;
        try {
            shape.run();
        } finally {
            hull = 0.0;
        }
        shape.run();
    }

    private void put(int i, double x, double y, double z, double nx, double ny, double nz, int c) {
        int o = i * 3;
        qp[o] = x;
        qp[o + 1] = y;
        qp[o + 2] = z;
        qn[o] = nx;
        qn[o + 1] = ny;
        qn[o + 2] = nz;
        qc[i] = c;
    }

    /** The quad in {@link #qp}, wound out (or in, for a hull) by the normals it was given. */
    private void commit() {
        double ax = qp[6] - qp[0], ay = qp[7] - qp[1], az = qp[8] - qp[2];
        double bx = qp[9] - qp[3], by = qp[10] - qp[4], bz = qp[11] - qp[5];
        double fx = ay * bz - az * by;
        double fy = az * bx - ax * bz;
        double fz = ax * by - ay * bx;
        if (fx * fx + fy * fy + fz * fz < 1.0E-22) return;
        double sx = qn[0] + qn[3] + qn[6] + qn[9];
        double sy = qn[1] + qn[4] + qn[7] + qn[10];
        double sz = qn[2] + qn[5] + qn[8] + qn[11];
        boolean outward = fx * sx + fy * sy + fz * sz >= 0.0;
        if (outward == hull > 0.0) {
            emit(0);
            emit(1);
            emit(2);
            emit(3);
        } else {
            emit(0);
            emit(3);
            emit(2);
            emit(1);
        }
    }

    private void emit(int i) {
        if (n == col.length) {
            pos = Arrays.copyOf(pos, n * 6);
            col = Arrays.copyOf(col, n * 2);
        }
        int o = i * 3;
        double x = qp[o], y = qp[o + 1], z = qp[o + 2];
        pos[n * 3] = (float) Mat.px(m, x, y, z);
        pos[n * 3 + 1] = (float) Mat.py(m, x, y, z);
        pos[n * 3 + 2] = (float) Mat.pz(m, x, y, z);
        col[n] = hull > 0.0 ? hullColor : qc[i];
        n++;
    }

    /** A flat ribbon along {@code spine} (points of x, y, z), facing +z. */
    void strip(double[] spine, double w, int c) {
        for (int i = 0; i + 5 < spine.length; i += 3) {
            put(0, spine[i] - w / 2.0, spine[i + 1], spine[i + 2], 0.0, 0.0, 1.0, c);
            put(1, spine[i] + w / 2.0, spine[i + 1], spine[i + 2], 0.0, 0.0, 1.0, c);
            put(2, spine[i + 3] + w / 2.0, spine[i + 4], spine[i + 5], 0.0, 0.0, 1.0, c);
            put(3, spine[i + 3] - w / 2.0, spine[i + 4], spine[i + 5], 0.0, 0.0, 1.0, c);
            commit();
        }
    }

    void sphere(double cx, double cy, double cz, double r, int lon, int lat, int c) {
        ellipsoid(cx, cy, cz, r, r, r, lon, lat, c);
    }

    void ellipsoid(double cx, double cy, double cz, double rx, double ry, double rz, int lon, int lat, int c) {
        rx += hull;
        ry += hull;
        rz += hull;
        for (int k = 0; k < lat; k++) {
            double f0 = Math.PI * k / lat - Math.PI / 2;
            double f1 = Math.PI * (k + 1) / lat - Math.PI / 2;
            double cf0 = Math.cos(f0), sf0 = Math.sin(f0), cf1 = Math.cos(f1), sf1 = Math.sin(f1);
            for (int i = 0; i < lon; i++) {
                double t0 = i * (Math.PI * 2) / lon;
                double t1 = (i + 1) * (Math.PI * 2) / lon;
                double c0 = Math.cos(t0), s0 = Math.sin(t0), c1 = Math.cos(t1), s1 = Math.sin(t1);
                point(0, cx, cy, cz, rx, ry, rz, cf0, sf0, c0, s0, c);
                point(1, cx, cy, cz, rx, ry, rz, cf0, sf0, c1, s1, c);
                point(2, cx, cy, cz, rx, ry, rz, cf1, sf1, c1, s1, c);
                point(3, cx, cy, cz, rx, ry, rz, cf1, sf1, c0, s0, c);
                commit();
            }
        }
    }

    private void point(int i, double cx, double cy, double cz, double rx, double ry, double rz, double cf, double sf, double ct, double st, int c) {
        double ux = cf * ct;
        double uz = cf * st;
        put(i, cx + rx * ux, cy + ry * sf, cz + rz * uz, ux / rx, sf / ry, uz / rz, c);
    }

    void cylinder(double y0, double y1, double r, int segs, int c) {
        frustum(y0, r, y1, r, segs, c);
    }

    /** A cone cut flat at both ends: radius {@code r0} at {@code y0}, {@code r1} at {@code y1}, with caps. */
    void frustum(double y0, double r0, double y1, double r1, int segs, int c) {
        y0 -= hull;
        y1 += hull;
        r0 += hull;
        r1 += hull;
        double ny = (r0 - r1) / Math.max(1.0E-6, y1 - y0);
        for (int i = 0; i < segs; i++) {
            double t0 = i * (Math.PI * 2) / segs;
            double t1 = (i + 1) * (Math.PI * 2) / segs;
            double c0 = Math.cos(t0), s0 = Math.sin(t0), c1 = Math.cos(t1), s1 = Math.sin(t1);
            put(0, r0 * c0, y0, r0 * s0, c0, ny, s0, c);
            put(1, r0 * c1, y0, r0 * s1, c1, ny, s1, c);
            put(2, r1 * c1, y1, r1 * s1, c1, ny, s1, c);
            put(3, r1 * c0, y1, r1 * s0, c0, ny, s0, c);
            commit();
            put(0, 0.0, y0, 0.0, 0.0, -1.0, 0.0, c);
            put(1, r0 * c1, y0, r0 * s1, 0.0, -1.0, 0.0, c);
            put(2, r0 * c0, y0, r0 * s0, 0.0, -1.0, 0.0, c);
            put(3, r0 * c0, y0, r0 * s0, 0.0, -1.0, 0.0, c);
            commit();
            put(0, 0.0, y1, 0.0, 0.0, 1.0, 0.0, c);
            put(1, r1 * c0, y1, r1 * s0, 0.0, 1.0, 0.0, c);
            put(2, r1 * c1, y1, r1 * s1, 0.0, 1.0, 0.0, c);
            put(3, r1 * c1, y1, r1 * s1, 0.0, 1.0, 0.0, c);
            commit();
        }
    }
}
