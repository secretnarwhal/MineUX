package com.mineux.visuals.cosmetics.pets;

import com.mineux.visuals.cosmetics.CosmeticSink;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pets' outline: the game's gizmo quads are two-sided, so {@link Mesh#emit} drops the quads
 * that face away from the camera itself. Done right, a ball's body is kept on the side towards
 * the camera and its black hull only on the far side, where it shows as a rim; done backwards,
 * the hull would cover the body and every pet would be a black blob.
 */
class MeshCullTest {

    private static final int BODY = 0xFFFFFFFF;
    private static final int HULL = 0xFF101010;

    @Test
    void bodyFacesTheCameraAndTheHullOnlyShowsBehind() {
        for (double yawDeg = 0.0; yawDeg < 360.0; yawDeg += 45.0) {
            check(yawDeg);
        }
    }

    private static void check(double yawDeg) {
        MeshBuilder mb = new MeshBuilder();
        mb.reset();
        mb.outlineColor(HULL);
        mb.setTransform(Mat.identity());
        mb.outlined(0.02, () -> mb.ellipsoid(0.0, 0.0, 0.0, 0.2, 0.2, 0.2, 14, 9, BODY));
        Mesh mesh = mb.build();

        // As PetWalk sets it: the pet 3 blocks in front of the camera, turned to yawDeg.
        double s = 0.42;
        double yr = Math.toRadians(yawDeg);
        double hx = 0.0 - Math.sin(yr);
        double hz = Math.cos(yr);
        double[] w = {(0.0 - hz) * s, 0.0, hx * s, 0.0, 0.0, s, 0.0, 0.0, hx * s, 0.0, hz * s, 3.0};

        List<double[]> kept = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        CosmeticSink sink = new CosmeticSink() {
            @Override
            public void quad(double[] a, double[] b, double[] c, double[] d, int ca, int cb, int cc, int cd) {
                kept.add(new double[] {(a[2] + b[2] + c[2] + d[2]) / 4.0});
                colors.add(ca);
            }
        };
        mesh.emit(sink, w, new double[] {0.0, 0.0, 0.0}, 1.0F);

        // Seen from 3 blocks off, the line between facing and not sits a hair (r*r/3) in front of
        // the middle, so a quad at the edge may be kept a little either side of it.
        int body = 0;
        int hull = 0;
        for (int i = 0; i < kept.size(); i++) {
            double z = kept.get(i)[0];
            if (colors.get(i) == BODY) {
                body++;
                assertTrue(z < 3.0 + 0.01, "a body quad behind the ball's middle was kept at yaw " + yawDeg);
            } else {
                hull++;
                assertTrue(z > 3.0 - 0.01, "a hull quad in front of the ball's middle was kept at yaw " + yawDeg);
            }
        }
        assertTrue(body > 20, "too few body quads kept: " + body);
        assertTrue(hull > 20, "too few hull quads kept: " + hull);
    }
}
