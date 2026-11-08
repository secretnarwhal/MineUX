package com.mineux.visuals.self.model;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A model read from a file -- Blockbench (.bbmodel), Wavefront (.obj) or glTF (.gltf, .glb) --
 * scaled to stand one unit tall on the origin, facing +z, and rigged where it can be: every
 * triangle given to the head, an arm, a leg or the body, so the limbs can swing.
 * <p>
 * A part is a limb by its name (or a group's, or its node's) -- head, hat, arm, hand, leg, boot and
 * so on. A model that names none is cut up by shape instead: the top quarter is the head, the
 * bottom two fifths the legs, and whatever stands well out to the sides in between the arms. A
 * limb is a left one or a right one by which side of the middle its piece is on.
 */
public final class Imported {

    static final int MAX_TRIANGLES = 300000;

    /** Bones: 0 the body (never moves), 1 head, 2 and 3 the arms, 4 and 5 the legs. */
    static final byte BODY = 0, HEAD = 1, ARM = 2, LEG = 3;

    private static final Set<String> HEAD_WORDS = Set.of("head", "hair", "hat", "helmet", "face", "ear", "ears", "horn", "horns", "eye",
        "eyes", "nose", "mouth", "jaw", "beard", "snout", "skull", "antler", "antlers", "crown");
    private static final Set<String> ARM_WORDS = Set.of("arm", "arms", "hand", "hands", "shoulder", "forearm", "sleeve", "wrist", "elbow",
        "finger", "fingers");
    private static final Set<String> LEG_WORDS = Set.of("leg", "legs", "foot", "feet", "thigh", "shin", "calf", "knee", "boot", "boots",
        "shoe", "shoes", "sock", "toe", "toes");

    public final String name;
    final List<Piece> parts = new ArrayList<>();
    final Map<String, byte[]> images = new LinkedHashMap<>();
    public int triangles;
    public boolean rigged;
    /** Where each bone turns, three to a bone. */
    final float[] pivot = new float[18];
    private float widthRatio = 1.0F;

    Imported(String name) {
        this.name = name;
    }

    public static boolean supported(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".obj") || n.endsWith(".glb") || n.endsWith(".gltf") || n.endsWith(".bbmodel");
    }

    public static Imported load(Path file, boolean zUp) throws IOException {
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        Imported m = new Imported(file.getFileName().toString());
        if (n.endsWith(".obj")) ObjLoad.load(m, file);
        else if (n.endsWith(".glb")) GltfLoad.loadGlb(m, file);
        else if (n.endsWith(".gltf")) GltfLoad.loadGltf(m, file);
        else if (n.endsWith(".bbmodel")) BbLoad.load(m, file);
        else throw new IOException("use a .bbmodel, .obj, .glb or .gltf");
        if (m.triangles == 0) throw new IOException("the model has no triangles");
        m.normalise(zUp);
        m.rig();
        return m;
    }

    /** The bone a part's names put it on, the first name that says one winning. */
    static byte kindOf(String... names) {
        for (String s : names) {
            if (s == null || s.isEmpty()) continue;
            String t = s.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
            for (String w : t.split("[^a-z]+")) {
                if (HEAD_WORDS.contains(w)) return HEAD;
                if (ARM_WORDS.contains(w)) return ARM;
                if (LEG_WORDS.contains(w)) return LEG;
            }
        }
        return BODY;
    }

    static int rgba(float r, float g, float b, float a) {
        return Math.round(clamp01(a) * 255.0F) << 24 | Math.round(clamp01(r) * 255.0F) << 16
            | Math.round(clamp01(g) * 255.0F) << 8 | Math.round(clamp01(b) * 255.0F);
    }

    static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    /** The triangle at corner {@code i}'s normal, flat, on all three corners. */
    static void flatNormals(float[] p, float[] nr, int i) {
        int o = i * 3;
        float ax = p[o + 3] - p[o], ay = p[o + 4] - p[o + 1], az = p[o + 5] - p[o + 2];
        float bx = p[o + 6] - p[o], by = p[o + 7] - p[o + 1], bz = p[o + 8] - p[o + 2];
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1.0E-12F) {
            nx = 0.0F;
            ny = 1.0F;
            nz = 0.0F;
        } else {
            nx /= l;
            ny /= l;
            nz /= l;
        }
        for (int k = 0; k < 3; k++) {
            nr[o + k * 3] = nx;
            nr[o + k * 3 + 1] = ny;
            nr[o + k * 3 + 2] = nz;
        }
    }

    /** One unit tall, standing on the origin, centred; a z-up model stood up first. */
    private void normalise(boolean zUp) {
        float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] hi = {0.0F - Float.MAX_VALUE, 0.0F - Float.MAX_VALUE, 0.0F - Float.MAX_VALUE};
        for (Piece p : parts) {
            if (zUp) standUp(p);
            for (int i = 0; i < p.count * 3; i++) {
                float v = p.pos[i];
                int a = i % 3;
                if (v < lo[a]) lo[a] = v;
                if (v > hi[a]) hi[a] = v;
            }
        }
        float h = hi[1] - lo[1];
        if (!(h > 1.0E-9F)) h = Math.max(hi[0] - lo[0], Math.max(hi[2] - lo[2], 1.0E-6F));
        float s = 1.0F / h;
        float cx = (lo[0] + hi[0]) / 2.0F;
        float cz = (lo[2] + hi[2]) / 2.0F;
        for (Piece p : parts) {
            for (int i = 0; i < p.count; i++) {
                p.pos[i * 3] = (p.pos[i * 3] - cx) * s;
                p.pos[i * 3 + 1] = (p.pos[i * 3 + 1] - lo[1]) * s;
                p.pos[i * 3 + 2] = (p.pos[i * 3 + 2] - cz) * s;
            }
        }
        widthRatio = Math.max(hi[0] - lo[0], hi[2] - lo[2]) * s;
    }

    private static void standUp(Piece p) {
        for (int i = 0; i < p.count; i++) {
            float y = p.pos[i * 3 + 1];
            p.pos[i * 3 + 1] = p.pos[i * 3 + 2];
            p.pos[i * 3 + 2] = 0.0F - y;
            float ny = p.nrm[i * 3 + 1];
            p.nrm[i * 3 + 1] = p.nrm[i * 3 + 2];
            p.nrm[i * 3 + 2] = 0.0F - ny;
        }
    }

    /** Every triangle onto a bone, and each bone's pivot from the triangles on it. */
    private void rig() {
        boolean anyLimb = false;
        boolean anyHead = false;
        for (Piece p : parts) {
            if (p.bone == null) p.bone = new byte[p.count];
            for (int i = 0; i < p.count; i++) {
                byte k = p.bone[i];
                if (k == ARM || k == LEG) anyLimb = true;
                else if (k == HEAD) anyHead = true;
            }
        }
        if (!anyLimb && widthRatio <= 1.1F) shapeClassify(!anyHead);
        Map<Integer, float[]> span = spans();
        float[] mn = new float[18];
        float[] mx = new float[18];
        Arrays.fill(mn, Float.MAX_VALUE);
        Arrays.fill(mx, 0.0F - Float.MAX_VALUE);
        int[] cnt = new int[6];
        for (Piece p : parts) {
            for (int t = 0; t + 2 < p.count; t += 3) {
                byte k = p.bone[t];
                if (k == BODY) continue;
                int b = side(p, t, k, span);
                for (int j = 0; j < 3; j++) {
                    p.bone[t + j] = (byte) b;
                    for (int a = 0; a < 3; a++) {
                        float v = p.pos[(t + j) * 3 + a];
                        if (v < mn[b * 3 + a]) mn[b * 3 + a] = v;
                        if (v > mx[b * 3 + a]) mx[b * 3 + a] = v;
                    }
                    cnt[b]++;
                }
            }
        }
        for (int b = 1; b < 6; b++) {
            if (cnt[b] == 0) continue;
            float h = mx[b * 3 + 1] - mn[b * 3 + 1];
            pivot[b * 3] = (mn[b * 3] + mx[b * 3]) / 2.0F;
            pivot[b * 3 + 2] = (mn[b * 3 + 2] + mx[b * 3 + 2]) / 2.0F;
            // The head turns at its neck, an arm at its shoulder, a leg at its hip.
            pivot[b * 3 + 1] = b == 1 ? mn[b * 3 + 1] + 0.1F * h : (b <= 3 ? mx[b * 3 + 1] - 0.1F * h : mx[b * 3 + 1] - 0.05F * h);
            rigged = true;
        }
    }

    /** Each limb piece's x extent and middle: {min, max, sum, count}. */
    private Map<Integer, float[]> spans() {
        Map<Integer, float[]> span = new HashMap<>();
        for (Piece p : parts) {
            for (int t = 0; t + 2 < p.count; t += 3) {
                byte k = p.bone[t];
                int pid = p.piece == null ? 0 - 1 : p.piece[t];
                if (k != ARM && k != LEG || pid < 0) continue;
                float[] sp = span.get(pid);
                if (sp == null) {
                    sp = new float[] {Float.MAX_VALUE, 0.0F - Float.MAX_VALUE, 0.0F, 0.0F};
                    span.put(pid, sp);
                }
                for (int j = 0; j < 3; j++) {
                    float x = p.pos[(t + j) * 3];
                    sp[0] = Math.min(sp[0], x);
                    sp[1] = Math.max(sp[1], x);
                    sp[2] += x;
                    sp[3]++;
                }
            }
        }
        return span;
    }

    /** The bone (1-5) for a triangle of kind {@code k}: a limb goes left or right by its piece's middle. */
    private static int side(Piece p, int t, byte k, Map<Integer, float[]> span) {
        if (k == HEAD) return 1;
        float cx = (p.pos[t * 3] + p.pos[t * 3 + 3] + p.pos[t * 3 + 6]) / 3.0F;
        float[] sp = p.piece != null && p.piece[t] >= 0 ? span.get(p.piece[t]) : null;
        // A piece that straddles the middle (a belt of legs) goes by the triangle's own side.
        boolean left = sp != null && (!(sp[0] < 0.0F - 0.02F) || !(sp[1] > 0.02F)) ? sp[2] / sp[3] > 0.0F : cx > 0.0F;
        if (k == ARM) return left ? 2 : 3;
        return left ? 4 : 5;
    }

    private void shapeClassify(boolean doHead) {
        float halfW = 0.0F;
        for (Piece p : parts) {
            for (int t = 0; t + 2 < p.count; t += 3) {
                float cy = (p.pos[t * 3 + 1] + p.pos[t * 3 + 4] + p.pos[t * 3 + 7]) / 3.0F;
                if (cy < 0.45F || cy > 0.7F) continue;
                for (int j = 0; j < 3; j++) halfW = Math.max(halfW, Math.abs(p.pos[(t + j) * 3]));
            }
        }
        for (Piece p : parts) {
            for (int t = 0; t + 2 < p.count; t += 3) {
                if (p.bone[t] != BODY) continue;
                float cx = (p.pos[t * 3] + p.pos[t * 3 + 3] + p.pos[t * 3 + 6]) / 3.0F;
                float cy = (p.pos[t * 3 + 1] + p.pos[t * 3 + 4] + p.pos[t * 3 + 7]) / 3.0F;
                byte k = BODY;
                if (doHead && cy > 0.77F) k = HEAD;
                else if (cy < 0.4F) k = LEG;
                else if (cy <= 0.77F && halfW > 0.0F && Math.abs(cx) > 0.55F * halfW) k = ARM;
                if (k != BODY) {
                    for (int j = 0; j < 3; j++) p.bone[t + j] = k;
                }
            }
        }
    }
}
