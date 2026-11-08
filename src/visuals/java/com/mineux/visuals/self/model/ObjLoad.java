package com.mineux.visuals.self.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Wavefront .obj with its .mtl files: vertices (with colours when a line carries six numbers),
 * texture coordinates, normals, objects and groups (whose names rig the limbs), and faces of any
 * size, fanned into triangles. A material's diffuse colour, opacity and diffuse texture are read.
 */
final class ObjLoad {

    /** A material: its colour and its image's key. */
    private static final class Mtl {
        int color = 0xFFFFFFFF;
        String map;
    }

    /** The file's numbers so far, grown as they come. */
    private static final class Lists {
        float[] v = new float[3072];
        float[] vc;
        float[] vt = new float[2048];
        float[] vn = new float[3072];
        int nv, nvt, nvn;
    }

    private ObjLoad() {}

    static void load(Imported m, Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        Path dir = file.toAbsolutePath().getParent();
        Map<String, Mtl> mtls = new HashMap<>();
        for (String raw : lines) {
            String l = raw.trim();
            if (!l.startsWith("mtllib ")) continue;
            for (String lib : l.substring(7).trim().split("\\s+(?=\\S+\\.mtl)")) readMtl(dir, lib.trim(), mtls, m);
        }
        Lists d = new Lists();
        Map<String, Piece.Builder> builders = new LinkedHashMap<>();
        Map<String, Integer> pieces = new HashMap<>();
        String current = "";
        byte objKind = Imported.BODY;
        byte grpKind = Imported.BODY;
        String objName = "";
        String grpName = "";
        int made = 0;
        for (String raw : lines) {
            String l = raw.trim();
            if (l.isEmpty() || l.charAt(0) == '#') continue;
            String[] t = l.split("\\s+");
            String head = t[0];
            if (Objects.equals("v", head)) vertex(d, t);
            else if (Objects.equals("vt", head)) texcoord(d, t);
            else if (Objects.equals("vn", head)) normal(d, t);
            else if (Objects.equals("o", head)) {
                objName = l.length() > 1 ? l.substring(1).trim() : "";
                objKind = Imported.kindOf(objName);
            } else if (Objects.equals("g", head)) {
                grpName = l.length() > 1 ? l.substring(1).trim() : "";
                grpKind = Imported.kindOf(grpName);
            } else if (Objects.equals("usemtl", head)) {
                current = t.length > 1 ? l.substring(6).trim() : "";
            } else if (Objects.equals("f", head)) {
                if (m.triangles + made > Imported.MAX_TRIANGLES) throw new IOException("too detailed (over " + Imported.MAX_TRIANGLES + " triangles)");
                Piece.Builder b = builders.get(current);
                if (b == null) {
                    Mtl mt = mtls.get(current);
                    b = new Piece.Builder(new Piece(mt == null ? null : mt.map, mt == null ? 0xFFFFFFFF : mt.color));
                    builders.put(current, b);
                }
                b.curKind = grpKind != Imported.BODY ? grpKind : objKind;
                String pk = objName + "|" + grpName;
                Integer pid = pieces.get(pk);
                if (pid == null) {
                    pid = pieces.size();
                    pieces.put(pk, pid);
                }
                b.curPiece = pid;
                made += face(b, d, t);
            }
        }
        for (Piece.Builder b : builders.values()) b.finish(m);
    }

    private static void vertex(Lists d, String[] t) {
        if (d.nv * 3 + 3 > d.v.length) d.v = Arrays.copyOf(d.v, d.v.length * 2);
        d.v[d.nv * 3] = f(t, 1);
        d.v[d.nv * 3 + 1] = f(t, 2);
        d.v[d.nv * 3 + 2] = f(t, 3);
        if (t.length >= 7) {
            if (d.vc == null) d.vc = new float[d.v.length];
            if (d.vc.length < d.v.length) d.vc = Arrays.copyOf(d.vc, d.v.length);
            d.vc[d.nv * 3] = f(t, 4);
            d.vc[d.nv * 3 + 1] = f(t, 5);
            d.vc[d.nv * 3 + 2] = f(t, 6);
        }
        d.nv++;
    }

    private static void texcoord(Lists d, String[] t) {
        if (d.nvt * 2 + 2 > d.vt.length) d.vt = Arrays.copyOf(d.vt, d.vt.length * 2);
        d.vt[d.nvt * 2] = f(t, 1);
        d.vt[d.nvt * 2 + 1] = t.length > 2 ? f(t, 2) : 0.0F;
        d.nvt++;
    }

    private static void normal(Lists d, String[] t) {
        if (d.nvn * 3 + 3 > d.vn.length) d.vn = Arrays.copyOf(d.vn, d.vn.length * 2);
        d.vn[d.nvn * 3] = f(t, 1);
        d.vn[d.nvn * 3 + 1] = f(t, 2);
        d.vn[d.nvn * 3 + 2] = f(t, 3);
        d.nvn++;
    }

    /** A face fanned into triangles; how many it made. */
    private static int face(Piece.Builder b, Lists d, String[] t) {
        int corners = t.length - 1;
        int[] idx = new int[corners * 3];
        for (int i = 0; i < corners; i++) corner(t[i + 1], d, idx, i * 3);
        int made = 0;
        for (int i = 1; i + 1 < corners; i++) {
            int start = b.n;
            boolean missingN = false;
            int[] tri = {0, i, i + 1};
            for (int c : tri) {
                int pi = idx[c * 3], ti = idx[c * 3 + 1], ni = idx[c * 3 + 2];
                float u = 0.0F, vv = 0.0F, nx = 0.0F, ny = 0.0F, nz = 0.0F;
                if (ti >= 0 && ti < d.nvt) {
                    u = d.vt[ti * 2];
                    vv = 1.0F - d.vt[ti * 2 + 1];
                }
                if (ni >= 0 && ni < d.nvn) {
                    nx = d.vn[ni * 3];
                    ny = d.vn[ni * 3 + 1];
                    nz = d.vn[ni * 3 + 2];
                } else {
                    missingN = true;
                }
                boolean hc = d.vc != null && pi * 3 + 2 < d.vc.length;
                int col = hc ? Imported.rgba(d.vc[pi * 3], d.vc[pi * 3 + 1], d.vc[pi * 3 + 2], 1.0F) : 0xFFFFFFFF;
                b.add(d.v[pi * 3], d.v[pi * 3 + 1], d.v[pi * 3 + 2], nx, ny, nz, u, vv, col, hc);
            }
            if (missingN) b.flatNormals(start);
            made++;
        }
        return made;
    }

    private static float f(String[] t, int i) {
        return i < t.length ? Float.parseFloat(t[i]) : 0.0F;
    }

    private static int index(String s, int count) {
        int i = Integer.parseInt(s);
        return i < 0 ? count + i : i - 1;
    }

    /** A face corner's vertex, texture and normal indices into {@code out}, -1 for one not given. */
    private static void corner(String s, Lists d, int[] out, int at) {
        String[] p = s.split("/", 0 - 1);
        int vi = index(p[0], d.nv);
        if (vi < 0 || vi >= d.nv) throw new IllegalArgumentException("bad face index " + s);
        out[at] = vi;
        out[at + 1] = p.length > 1 && !p[1].isEmpty() ? index(p[1], d.nvt) : 0 - 1;
        out[at + 2] = p.length > 2 && !p[2].isEmpty() ? index(p[2], d.nvn) : 0 - 1;
    }

    private static void readMtl(Path dir, String name, Map<String, Mtl> out, Imported m) {
        Path p = dir.resolve(name).normalize();
        if (!p.startsWith(dir) || !Files.exists(p)) return;
        try {
            Mtl cur = null;
            float r = 1.0F, g = 1.0F, b = 1.0F, a = 1.0F;
            for (String raw : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                String l = raw.trim();
                if (l.isEmpty() || l.charAt(0) == '#') continue;
                String[] t = l.split("\\s+");
                String head = t[0];
                if (Objects.equals("newmtl", head)) {
                    if (cur != null) cur.color = Imported.rgba(r, g, b, a);
                    cur = new Mtl();
                    r = 1.0F;
                    g = 1.0F;
                    b = 1.0F;
                    a = 1.0F;
                    out.put(l.substring(6).trim(), cur);
                } else if (Objects.equals("Kd", head) && t.length >= 4) {
                    r = Float.parseFloat(t[1]);
                    g = Float.parseFloat(t[2]);
                    b = Float.parseFloat(t[3]);
                } else if (Objects.equals("d", head) && t.length >= 2) {
                    a = Float.parseFloat(t[1]);
                } else if (Objects.equals("Tr", head) && t.length >= 2) {
                    a = 1.0F - Float.parseFloat(t[1]);
                } else if (Objects.equals("map_Kd", head) && cur != null) {
                    cur.map = texture(dir, t[t.length - 1], m);
                }
            }
            if (cur != null) cur.color = Imported.rgba(r, g, b, a);
        } catch (Exception ignored) {
            // The materials read so far stand; the rest draw white.
        }
    }

    private static String texture(Path dir, String name, Imported m) throws IOException {
        Path tp = dir.resolve(name.replace('\\', '/')).normalize();
        if (!tp.startsWith(dir) || !Files.exists(tp)) tp = dir.resolve(tp.getFileName().toString());
        if (!Files.exists(tp)) return null;
        String key = "obj:" + tp.getFileName();
        if (!m.images.containsKey(key)) m.images.put(key, Files.readAllBytes(tp));
        return key;
    }
}
