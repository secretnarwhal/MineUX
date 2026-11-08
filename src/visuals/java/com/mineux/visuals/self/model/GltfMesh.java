package com.mineux.visuals.self.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Map;

/** One glTF mesh's primitives into the model, through their node's world matrix. */
final class GltfMesh {

    private static final int TRIANGLES = 4, STRIP = 5, FAN = 6;

    private GltfMesh() {}

    static void read(Imported m, GltfData c, JsonArray meshes, int meshIndex, float[] world, Map<Integer, Piece.Builder> builders,
                     GltfLoad.Materials materials, byte kind, int piece) throws IOException {
        if (meshIndex < 0 || meshIndex >= meshes.size()) return;
        float[] nmat = normalMatrix(world);
        boolean flipped = det3(world) < 0.0F;
        for (JsonElement pe : GltfLoad.arr(meshes.get(meshIndex).getAsJsonObject(), "primitives")) {
            JsonObject prim = pe.getAsJsonObject();
            int mode = prim.has("mode") ? prim.get("mode").getAsInt() : TRIANGLES;
            if (mode != TRIANGLES && mode != STRIP && mode != FAN) continue;
            JsonObject at = prim.getAsJsonObject("attributes");
            if (at == null || !at.has("POSITION")) continue;
            float[] p = c.floats(at.get("POSITION").getAsInt());
            float[] n = at.has("NORMAL") ? c.floats(at.get("NORMAL").getAsInt()) : null;
            float[] t = at.has("TEXCOORD_0") ? c.floats(at.get("TEXCOORD_0").getAsInt()) : null;
            float[] col = at.has("COLOR_0") ? c.floats(at.get("COLOR_0").getAsInt()) : null;
            int cComp = col != null ? c.components(at.get("COLOR_0").getAsInt()) : 0;
            int vcount = p.length / 3;
            int[] idx;
            if (prim.has("indices")) {
                idx = c.ints(prim.get("indices").getAsInt());
            } else {
                idx = new int[vcount];
                for (int i = 0; i < vcount; i++) idx[i] = i;
            }
            idx = triangles(idx, mode);
            if (m.triangles + idx.length / 3 > Imported.MAX_TRIANGLES) throw new IOException("too detailed (over " + Imported.MAX_TRIANGLES + " triangles)");
            int matIndex = prim.has("material") ? prim.get("material").getAsInt() : 0 - 1;
            Piece.Builder b = builders.get(matIndex);
            if (b == null) {
                b = materials.builder(matIndex);
                builders.put(matIndex, b);
            }
            b.curKind = kind;
            b.curPiece = piece;
            for (int tri = 0; tri + 2 < idx.length; tri += 3) {
                int start = b.n;
                for (int k = 0; k < 3; k++) {
                    int i = idx[tri + k];
                    corner(b, i < 0 || i >= vcount ? 0 : i, p, n, t, col, cComp, world, nmat);
                }
                if (n == null) b.flatNormals(start);
                if (flipped) swap(b, start + 1, start + 2);
            }
        }
    }

    private static void corner(Piece.Builder b, int i, float[] p, float[] n, float[] t, float[] col, int cComp, float[] w, float[] nm) {
        float x = p[i * 3], y = p[i * 3 + 1], z = p[i * 3 + 2];
        float wx = w[0] * x + w[4] * y + w[8] * z + w[12];
        float wy = w[1] * x + w[5] * y + w[9] * z + w[13];
        float wz = w[2] * x + w[6] * y + w[10] * z + w[14];
        float nx = 0.0F, ny = 0.0F, nz = 0.0F;
        if (n != null) {
            float ox = n[i * 3], oy = n[i * 3 + 1], oz = n[i * 3 + 2];
            nx = nm[0] * ox + nm[1] * oy + nm[2] * oz;
            ny = nm[3] * ox + nm[4] * oy + nm[5] * oz;
            nz = nm[6] * ox + nm[7] * oy + nm[8] * oz;
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l > 1.0E-12F) {
                nx /= l;
                ny /= l;
                nz /= l;
            }
        }
        float u = t != null ? t[i * 2] : 0.0F;
        float v = t != null ? t[i * 2 + 1] : 0.0F;
        int argb = 0xFFFFFFFF;
        if (col != null) {
            float a = cComp == 4 ? col[i * 4 + 3] : 1.0F;
            argb = Imported.rgba(col[i * cComp], col[i * cComp + 1], col[i * cComp + 2], a);
        }
        b.add(wx, wy, wz, nx, ny, nz, u, v, argb, col != null);
    }

    /** Strips and fans as plain triangle lists. */
    private static int[] triangles(int[] idx, int mode) {
        if (mode == TRIANGLES) return idx;
        if (idx.length < 3) return new int[0];
        int[] out = new int[(idx.length - 2) * 3];
        for (int i = 0; i + 2 < idx.length; i++) {
            if (mode == STRIP) {
                boolean even = (i & 1) == 0;
                out[i * 3] = idx[i];
                out[i * 3 + 1] = even ? idx[i + 1] : idx[i + 2];
                out[i * 3 + 2] = even ? idx[i + 2] : idx[i + 1];
            } else {
                out[i * 3] = idx[0];
                out[i * 3 + 1] = idx[i + 1];
                out[i * 3 + 2] = idx[i + 2];
            }
        }
        return out;
    }

    private static void swap(Piece.Builder b, int i, int j) {
        for (int k = 0; k < 3; k++) {
            float t = b.pos[i * 3 + k];
            b.pos[i * 3 + k] = b.pos[j * 3 + k];
            b.pos[j * 3 + k] = t;
            t = b.nrm[i * 3 + k];
            b.nrm[i * 3 + k] = b.nrm[j * 3 + k];
            b.nrm[j * 3 + k] = t;
        }
        for (int k = 0; k < 2; k++) {
            float t = b.uv[i * 2 + k];
            b.uv[i * 2 + k] = b.uv[j * 2 + k];
            b.uv[j * 2 + k] = t;
        }
        if (b.col != null) {
            int t = b.col[i];
            b.col[i] = b.col[j];
            b.col[j] = t;
        }
    }

    private static float det3(float[] m) {
        return m[0] * (m[5] * m[10] - m[9] * m[6]) - m[4] * (m[1] * m[10] - m[9] * m[2]) + m[8] * (m[1] * m[6] - m[5] * m[2]);
    }

    /** The inverse transpose of the matrix's upper 3x3, row major, for the normals. */
    private static float[] normalMatrix(float[] m) {
        float a = m[0], b = m[4], c = m[8], d = m[1], e = m[5], f = m[9], g = m[2], h = m[6], i = m[10];
        float ca = e * i - f * h;
        float cb = f * g - d * i;
        float cc = d * h - e * g;
        float det = a * ca + b * cb + c * cc;
        if (Math.abs(det) < 1.0E-20F) return new float[] {1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F};
        float inv = 1.0F / det;
        return new float[] {
            ca * inv, cb * inv, cc * inv,
            (c * h - b * i) * inv, (a * i - c * g) * inv, (b * g - a * h) * inv,
            (b * f - c * e) * inv, (c * d - a * f) * inv, (a * e - b * d) * inv};
    }
}
