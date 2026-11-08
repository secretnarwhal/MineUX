package com.mineux.visuals.self.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Blockbench's .bbmodel: cubes (with per-face or box UVs, rotated about their origin and through
 * every group above them) and free meshes, one {@link Piece} per texture. Textures come embedded
 * in the file, or from beside it. Hidden or unexported elements and groups are left out.
 */
final class BbLoad {

    private static final String[] FACES = {"north", "east", "south", "west", "up", "down"};
    private static final int NORTH = 0, EAST = 1, SOUTH = 2, WEST = 3, UP = 4;

    private final Imported m;
    private final int nTex;
    private final String[] texKeys;
    private final String[] texIds;
    private final float[] texW;
    private final float[] texH;
    private final Piece.Builder[] builders;
    private final Map<String, JsonObject> byId = new HashMap<>();
    private final Set<String> used = new HashSet<>();
    private byte kind;
    private int piece;
    private int pieces;

    private BbLoad(Imported m, int nTex) {
        this.m = m;
        this.nTex = nTex;
        texKeys = new String[nTex];
        texIds = new String[nTex];
        texW = new float[nTex];
        texH = new float[nTex];
        builders = new Piece.Builder[nTex + 1];
    }

    static void load(Imported m, Path file) throws IOException {
        JsonObject root;
        try {
            root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("not a .bbmodel file");
        }
        JsonArray textures = arr(root, "textures");
        BbLoad l = new BbLoad(m, textures.size());
        l.textures(root, textures, file.toAbsolutePath().getParent());

        List<JsonObject> all = new ArrayList<>();
        for (JsonElement e : arr(root, "elements")) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            all.add(o);
            String id = str(o, "uuid");
            if (id != null) l.byId.put(id, o);
        }
        l.walk(arr(root, "outliner"), new ArrayList<>(), new ArrayList<>(), false);
        for (JsonObject o : all) {
            String id = str(o, "uuid");
            if (id == null || !l.used.contains(id)) l.element(o, new ArrayList<>(), new ArrayList<>());
        }
        for (Piece.Builder b : l.builders) {
            if (b != null) b.finish(m);
        }
        if (m.triangles > Imported.MAX_TRIANGLES) throw new IOException("too detailed (over " + Imported.MAX_TRIANGLES + " triangles)");
    }

    private void textures(JsonObject root, JsonArray textures, Path dir) {
        float resW = 0.0F;
        float resH = 0.0F;
        JsonObject res = obj(root, "resolution");
        if (res != null) {
            resW = (float) num(res, "width", 0.0);
            resH = (float) num(res, "height", 0.0);
        }
        if (!(resW > 0.0F)) resW = (float) num(root, "texture_width", 0.0);
        if (!(resH > 0.0F)) resH = (float) num(root, "texture_height", 0.0);
        for (int i = 0; i < nTex; i++) {
            JsonObject t = textures.get(i).isJsonObject() ? textures.get(i).getAsJsonObject() : new JsonObject();
            texIds[i] = str(t, "id");
            float w = (float) num(t, "uv_width", 0.0);
            float h = (float) num(t, "uv_height", 0.0);
            if (!(w > 0.0F)) w = resW > 0.0F ? resW : (float) num(t, "width", 16.0);
            if (!(h > 0.0F)) h = resH > 0.0F ? resH : (float) num(t, "height", 16.0);
            texW[i] = Math.max(1.0F, w);
            texH[i] = Math.max(1.0F, h);
            byte[] data = image(t, dir);
            if (data != null && data.length > 0) {
                texKeys[i] = "bb:" + i;
                m.images.put(texKeys[i], data);
            }
        }
    }

    /** A texture's image: embedded as base64, or a file beside the model. */
    private static byte[] image(JsonObject t, Path dir) {
        String src = str(t, "source");
        int c = src == null ? 0 - 1 : src.indexOf("base64,");
        if (c >= 0) {
            try {
                return Base64.getMimeDecoder().decode(src.substring(c + 7));
            } catch (IllegalArgumentException ignored) {
                // Try the file instead.
            }
        }
        for (String key : new String[] {"relative_path", "path", "name"}) {
            String s = str(t, key);
            if (s == null || s.isBlank()) continue;
            try {
                s = s.replace('\\', '/');
                Path p = dir.resolve(s).normalize();
                if (!p.startsWith(dir) || !Files.isRegularFile(p)) p = dir.resolve(s.substring(s.lastIndexOf('/') + 1));
                if (Files.isRegularFile(p)) return Files.readAllBytes(p);
            } catch (Exception ignored) {
                // The next name, then none.
            }
        }
        return null;
    }

    private Piece.Builder builder(int ti) {
        int slot = ti >= 0 && ti < nTex ? ti : nTex;
        if (builders[slot] == null) {
            builders[slot] = new Piece.Builder(slot < nTex ? new Piece(texKeys[slot], 0xFFFFFFFF) : new Piece(null, 0xFFB4B4B4));
        }
        builders[slot].curKind = kind;
        builders[slot].curPiece = piece;
        return builders[slot];
    }

    /** The outliner's groups, depth first; {@code chain} their origins and rotations, six to a group. */
    private void walk(JsonArray nodes, List<double[]> chain, List<String> names, boolean skip) {
        for (JsonElement n : nodes) {
            if (n.isJsonPrimitive()) {
                String id = n.getAsString();
                JsonObject el = byId.get(id);
                if (el != null && used.add(id) && !skip) element(el, chain, names);
            } else if (n.isJsonObject()) {
                JsonObject g = n.getAsJsonObject();
                List<double[]> next = new ArrayList<>(chain);
                double[] o = vec3(g, "origin");
                double[] r = vec3(g, "rotation");
                next.add(new double[] {o[0], o[1], o[2], r[0], r[1], r[2]});
                List<String> nextNames = new ArrayList<>(names);
                nextNames.add(str(g, "name"));
                boolean hidden = !bool(g, "visibility", true) || !bool(g, "export", true);
                walk(arr(g, "children"), next, nextNames, skip || hidden);
            }
        }
    }

    private void element(JsonObject el, List<double[]> chain, List<String> names) {
        if (!bool(el, "visibility", true) || !bool(el, "export", true)) return;
        String[] ns = new String[names.size() + 1];
        ns[0] = str(el, "name");
        for (int i = 0; i < names.size(); i++) ns[i + 1] = names.get(names.size() - 1 - i);
        kind = Imported.kindOf(ns);
        piece = pieces++;
        String type = str(el, "type");
        if (type == null || Objects.equals("cube", type)) cube(el, chain);
        else if (Objects.equals("mesh", type)) mesh(el, chain);
    }

    private static double[] rotate(double[] p, double[] pivot, double rx, double ry, double rz) {
        if (rx == 0.0 && ry == 0.0 && rz == 0.0) return p;
        double x = p[0] - pivot[0];
        double y = p[1] - pivot[1];
        double z = p[2] - pivot[2];
        if (rx != 0.0) {
            double c = Math.cos(Math.toRadians(rx)), s = Math.sin(Math.toRadians(rx));
            double t = y * c - z * s;
            z = y * s + z * c;
            y = t;
        }
        if (ry != 0.0) {
            double c = Math.cos(Math.toRadians(ry)), s = Math.sin(Math.toRadians(ry));
            double t = x * c + z * s;
            z = z * c - x * s;
            x = t;
        }
        if (rz != 0.0) {
            double c = Math.cos(Math.toRadians(rz)), s = Math.sin(Math.toRadians(rz));
            double t = x * c - y * s;
            y = x * s + y * c;
            x = t;
        }
        return new double[] {x + pivot[0], y + pivot[1], z + pivot[2]};
    }

    /** A point through its element's rotation and every group's, into the model's frame (Blockbench faces -z). */
    private static float[] place(double[] pt, double[] org, double[] rot, List<double[]> chain) {
        double[] v = rotate(pt, org, rot[0], rot[1], rot[2]);
        for (int i = chain.size() - 1; i >= 0; i--) {
            double[] g = chain.get(i);
            v = rotate(v, g, g[3], g[4], g[5]);
        }
        return new float[] {(float) (0.0 - v[0]), (float) v[1], (float) (0.0 - v[2])};
    }

    /** A face's rectangle on a box-UV texture: u0, v0, u1, v1. */
    private static double[] boxUv(int face, double ox, double oy, double w, double h, double d) {
        if (face == EAST) return new double[] {ox, oy + d, ox + d, oy + d + h};
        if (face == NORTH) return new double[] {ox + d, oy + d, ox + d + w, oy + d + h};
        if (face == WEST) return new double[] {ox + d + w, oy + d, ox + 2.0 * d + w, oy + d + h};
        if (face == SOUTH) return new double[] {ox + 2.0 * d + w, oy + d, ox + 2.0 * d + 2.0 * w, oy + d + h};
        if (face == UP) return new double[] {ox + d + w, oy + d, ox + d, oy};
        return new double[] {ox + d + 2.0 * w, oy, ox + d + w, oy + d};
    }

    /** A cube face's four corners, x y z each, in the order its UV rectangle runs. */
    private static double[] corners(int face, double x0, double y0, double z0, double x1, double y1, double z1) {
        if (face == NORTH) return new double[] {x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0};
        if (face == SOUTH) return new double[] {x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1};
        if (face == EAST) return new double[] {x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1};
        if (face == WEST) return new double[] {x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0};
        if (face == UP) return new double[] {x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1};
        return new double[] {x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0};
    }

    private void cube(JsonObject el, List<double[]> chain) {
        JsonArray fa = el.has("from") && el.get("from").isJsonArray() ? el.getAsJsonArray("from") : null;
        JsonArray ta = el.has("to") && el.get("to").isJsonArray() ? el.getAsJsonArray("to") : null;
        if (fa == null || ta == null || fa.size() < 3 || ta.size() < 3) return;
        double inf = num(el, "inflate", 0.0);
        double x0 = fa.get(0).getAsDouble() - inf, y0 = fa.get(1).getAsDouble() - inf, z0 = fa.get(2).getAsDouble() - inf;
        double x1 = ta.get(0).getAsDouble() + inf, y1 = ta.get(1).getAsDouble() + inf, z1 = ta.get(2).getAsDouble() + inf;
        double[] org = vec3(el, "origin");
        double[] rot = vec3(el, "rotation");
        boolean box = bool(el, "box_uv", false);
        JsonArray off = el.has("uv_offset") && el.get("uv_offset").isJsonArray() ? el.getAsJsonArray("uv_offset") : null;
        JsonObject faces = obj(el, "faces");
        for (int f = 0; f < FACES.length; f++) {
            JsonObject fo = faces == null ? null : obj(faces, FACES[f]);
            if (fo == null) {
                if (!box) continue;
                fo = new JsonObject();
            }
            int ti = textureIndex(fo);
            if (ti == 0 - 2) continue;
            double[] uv = rect(fo, "uv");
            if (uv == null && box && off != null && off.size() >= 2) {
                uv = boxUv(f, off.get(0).getAsDouble(), off.get(1).getAsDouble(), x1 - x0, y1 - y0, z1 - z0);
            }
            if (uv == null) continue;
            double[] c = corners(f, x0, y0, z0, x1, y1, z1);
            double[] uvc = {uv[0], uv[1], uv[2], uv[1], uv[2], uv[3], uv[0], uv[3]};
            int k = ((int) Math.round(num(fo, "rotation", 0.0)) / 90 % 4 + 4) % 4;
            boolean textured = nTex > 0 && ti >= 0;
            float tw = textured ? texW[ti] : 1.0F;
            float th = textured ? texH[ti] : 1.0F;
            float[] p = new float[12];
            float[] t = new float[8];
            for (int i = 0; i < 4; i++) {
                float[] at = place(new double[] {c[i * 3], c[i * 3 + 1], c[i * 3 + 2]}, org, rot, chain);
                System.arraycopy(at, 0, p, i * 3, 3);
                int q = (i - k + 4) % 4;
                t[i * 2] = textured ? (float) (uvc[q * 2] / tw) : 0.0F;
                t[i * 2 + 1] = textured ? (float) (uvc[q * 2 + 1] / th) : 0.0F;
            }
            quad(ti, p, t);
        }
    }

    private int textureIndex(JsonObject face) {
        JsonElement te = face.get("texture");
        int none = nTex > 0 ? 0 : 0 - 1;
        if (te == null || te.isJsonNull()) return none;
        if (te.isJsonPrimitive() && te.getAsJsonPrimitive().isBoolean()) return te.getAsBoolean() ? none : 0 - 2;
        String s = te.getAsString();
        try {
            int i = Integer.parseInt(s);
            return i >= 0 && i < nTex ? i : none;
        } catch (NumberFormatException e) {
            for (int i = 0; i < nTex; i++) {
                if (s.equals(texIds[i])) return i;
            }
            return none;
        }
    }

    /** Two triangles of a quad: corners 0 3 2 and 0 2 1. */
    private void quad(int ti, float[] p, float[] t) {
        Piece.Builder b = builder(ti);
        int[] tris = {0, 3, 2, 0, 2, 1};
        for (int i = 0; i < 6; i += 3) {
            int start = b.n;
            for (int c = 0; c < 3; c++) {
                int j = tris[i + c];
                b.add(p[j * 3], p[j * 3 + 1], p[j * 3 + 2], 0.0F, 0.0F, 0.0F, t[j * 2], t[j * 2 + 1], 0, false);
            }
            b.flatNormals(start);
        }
    }

    private void mesh(JsonObject el, List<double[]> chain) {
        JsonObject verts = obj(el, "vertices");
        JsonObject faces = obj(el, "faces");
        if (verts == null || faces == null) return;
        double[] org = vec3(el, "origin");
        double[] rot = vec3(el, "rotation");
        for (Map.Entry<String, JsonElement> fe : faces.entrySet()) {
            if (!fe.getValue().isJsonObject()) continue;
            JsonObject fo = fe.getValue().getAsJsonObject();
            int ti = textureIndex(fo);
            if (ti == 0 - 2) continue;
            JsonArray ids = arr(fo, "vertices");
            JsonObject uvs = obj(fo, "uv");
            int n = ids.size();
            if (n < 3) continue;
            float[] p = new float[n * 3];
            float[] t = new float[n * 2];
            if (!corners(verts, uvs, ids, org, rot, chain, ti, p, t)) continue;
            Piece.Builder b = builder(ti);
            for (int i = 1; i + 1 < n; i++) {
                int start = b.n;
                int[] tri = {0, i, i + 1};
                for (int j : tri) b.add(p[j * 3], p[j * 3 + 1], p[j * 3 + 2], 0.0F, 0.0F, 0.0F, t[j * 2], t[j * 2 + 1], 0, false);
                b.flatNormals(start);
            }
        }
    }

    /** A mesh face's corners into {@code p} and {@code t}; false if one names a vertex that is not there. */
    private boolean corners(JsonObject verts, JsonObject uvs, JsonArray ids, double[] org, double[] rot, List<double[]> chain, int ti,
                            float[] p, float[] t) {
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i).getAsString();
            JsonElement ve = verts.get(id);
            if (ve == null || !ve.isJsonArray() || ve.getAsJsonArray().size() < 3) return false;
            JsonArray va = ve.getAsJsonArray();
            double[] local = {va.get(0).getAsDouble() + org[0], va.get(1).getAsDouble() + org[1], va.get(2).getAsDouble() + org[2]};
            System.arraycopy(place(local, org, rot, chain), 0, p, i * 3, 3);
            double u = 0.0;
            double v = 0.0;
            if (uvs != null && uvs.has(id) && uvs.get(id).isJsonArray() && uvs.getAsJsonArray(id).size() >= 2) {
                u = uvs.getAsJsonArray(id).get(0).getAsDouble();
                v = uvs.getAsJsonArray(id).get(1).getAsDouble();
            }
            boolean textured = nTex != 0 && ti >= 0;
            t[i * 2] = textured ? (float) (u / texW[ti]) : 0.0F;
            t[i * 2 + 1] = textured ? (float) (v / texH[ti]) : 0.0F;
        }
        return true;
    }

    // ---- json ----

    private static JsonObject obj(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static JsonArray arr(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static double num(JsonObject o, String k, double def) {
        JsonElement e = o.get(k);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsDouble() : def;
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private static boolean bool(JsonObject o, String k, boolean def) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : def;
    }

    private static double[] vec3(JsonObject o, String k) {
        double[] v = new double[3];
        JsonElement e = o.get(k);
        if (e == null || !e.isJsonArray()) return v;
        JsonArray a = e.getAsJsonArray();
        for (int i = 0; i < 3 && i < a.size(); i++) {
            try {
                v[i] = a.get(i).getAsDouble();
            } catch (RuntimeException ignored) {
                // Left at 0.
            }
        }
        return v;
    }

    private static double[] rect(JsonObject o, String k) {
        JsonElement e = o.get(k);
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() < 4) return null;
        JsonArray a = e.getAsJsonArray();
        double[] r = new double[4];
        for (int i = 0; i < 4; i++) {
            try {
                r[i] = a.get(i).getAsDouble();
            } catch (RuntimeException ex) {
                return null;
            }
        }
        return r;
    }
}
