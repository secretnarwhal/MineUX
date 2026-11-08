package com.mineux.visuals.self.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The custom player model's file readers and rig: a model is read, stood one unit tall on the
 * origin, and its named limbs land on the right bones with their pivots where they turn.
 */
class ImportedTest {

    /** A box of six quads as obj lines, under the group {@code name}, its vertices numbered from {@code base}. */
    private static String box(String name, int base, float x0, float y0, float z0, float x1, float y1, float z1) {
        StringBuilder s = new StringBuilder("g " + name + "\n");
        float[][] c = {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
        for (float[] v : c) s.append("v ").append(v[0]).append(' ').append(v[1]).append(' ').append(v[2]).append('\n');
        int[][] faces = {{1, 2, 3, 4}, {5, 8, 7, 6}, {1, 5, 6, 2}, {2, 6, 7, 3}, {3, 7, 8, 4}, {4, 8, 5, 1}};
        for (int[] f : faces) {
            s.append('f');
            for (int i : f) s.append(' ').append(base + i);
            s.append('\n');
        }
        return s.toString();
    }

    private static Imported figure(Path dir) throws IOException {
        String obj = box("body", 0, -4, 12, -2, 4, 24, 2)
            + box("head", 8, -4, 24, -4, 4, 32, 4)
            + box("left_arm", 16, 4, 12, -2, 8, 24, 2)
            + box("right_arm", 24, -8, 12, -2, -4, 24, 2)
            + box("left_leg", 32, 0, 0, -2, 4, 12, 2)
            + box("right_leg", 40, -4, 0, -2, 0, 12, 2);
        Path file = dir.resolve("figure.obj");
        Files.writeString(file, obj);
        return Imported.load(file, false);
    }

    @Test
    void objIsReadStoodUpAndRigged(@TempDir Path dir) throws IOException {
        Imported m = figure(dir);
        assertEquals(6 * 6 * 2, m.triangles);
        assertTrue(m.rigged);
        float lo = Float.MAX_VALUE, hi = 0.0F - Float.MAX_VALUE;
        for (Piece p : m.parts) {
            for (int i = 0; i < p.count; i++) {
                lo = Math.min(lo, p.pos[i * 3 + 1]);
                hi = Math.max(hi, p.pos[i * 3 + 1]);
            }
        }
        assertEquals(0.0F, lo, 1.0E-5F);
        assertEquals(1.0F, hi, 1.0E-5F);
        // The head turns at its neck, the arms at the shoulders, the legs at the hips.
        assertTrue(m.pivot[1 * 3 + 1] > m.pivot[2 * 3 + 1] - 0.2F);
        assertTrue(m.pivot[2 * 3 + 1] > m.pivot[4 * 3 + 1]);
        // The arm on +x is bone 2 and the one on -x bone 3.
        assertTrue(m.pivot[2 * 3] > 0.0F);
        assertTrue(m.pivot[3 * 3] < 0.0F);
    }

    @Test
    void namedLimbsLandOnTheirBones() {
        assertEquals(Imported.HEAD, Imported.kindOf("Hair_Top"));
        assertEquals(Imported.ARM, Imported.kindOf("leftForearm"));
        assertEquals(Imported.LEG, Imported.kindOf(null, "", "boots"));
        assertEquals(Imported.BODY, Imported.kindOf("torso"));
    }

    @Test
    void blockbenchCubesAreRead(@TempDir Path dir) throws IOException {
        String json = "{\"resolution\":{\"width\":16,\"height\":16},\"textures\":[],"
            + "\"elements\":[{\"name\":\"head\",\"type\":\"cube\",\"uuid\":\"a\",\"from\":[-4,0,-4],\"to\":[4,8,4],"
            + "\"faces\":{\"north\":{\"uv\":[0,0,8,8]},\"east\":{\"uv\":[0,0,8,8]},\"south\":{\"uv\":[0,0,8,8]},"
            + "\"west\":{\"uv\":[0,0,8,8]},\"up\":{\"uv\":[0,0,8,8]},\"down\":{\"uv\":[0,0,8,8]}}}],"
            + "\"outliner\":[\"a\"]}";
        Path file = dir.resolve("cube.bbmodel");
        Files.writeString(file, json);
        Imported m = Imported.load(file, false);
        assertEquals(12, m.triangles);
    }

    @Test
    void emptyModelsAreRefused(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("empty.obj");
        Files.writeString(file, "# nothing\n");
        assertThrows(IOException.class, () -> Imported.load(file, false));
    }
}
