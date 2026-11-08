package com.mineux.visuals.self.model;

import java.util.Arrays;

/**
 * One texture's worth of an imported model: its triangles, three corners each -- position, normal,
 * texture coordinate, an optional colour -- and for each corner the bone it moves with and the
 * piece of the model it came from.
 */
public final class Piece {

    /** The image's key in {@link Imported#images}, or null for none. */
    final String image;
    /** The material's colour, multiplied into every corner's. */
    final int color;
    float[] pos = new float[0];
    float[] nrm = new float[0];
    float[] uv = new float[0];
    int[] vcol;
    int count;
    int[] piece;
    byte[] bone;

    Piece(String image, int color) {
        this.image = image;
        this.color = color;
    }

    /** Fills a {@link Piece} a corner at a time, growing as it goes. */
    static final class Builder {
        final Piece part;
        float[] pos = new float[288];
        float[] nrm = new float[288];
        float[] uv = new float[192];
        int[] col;
        byte[] kind = new byte[96];
        int[] pieces = new int[96];
        byte curKind;
        int curPiece = 0 - 1;
        int n;

        Builder(Piece part) {
            this.part = part;
        }

        void add(float x, float y, float z, float nx, float ny, float nz, float u, float v, int c, boolean hasCol) {
            if (n * 3 + 3 > pos.length) {
                int cap = pos.length / 3 * 2;
                pos = Arrays.copyOf(pos, cap * 3);
                nrm = Arrays.copyOf(nrm, cap * 3);
                uv = Arrays.copyOf(uv, cap * 2);
                if (col != null) col = Arrays.copyOf(col, cap);
                kind = Arrays.copyOf(kind, cap);
                pieces = Arrays.copyOf(pieces, cap);
            }
            if (hasCol && col == null) {
                col = new int[pos.length / 3];
                Arrays.fill(col, 0, n, 0xFFFFFFFF);
            }
            pos[n * 3] = x;
            pos[n * 3 + 1] = y;
            pos[n * 3 + 2] = z;
            nrm[n * 3] = nx;
            nrm[n * 3 + 1] = ny;
            nrm[n * 3 + 2] = nz;
            uv[n * 2] = u;
            uv[n * 2 + 1] = v;
            if (col != null) col[n] = hasCol ? c : 0xFFFFFFFF;
            kind[n] = curKind;
            pieces[n] = curPiece;
            n++;
        }

        /** The last triangle's normals, flat, from its corners; for a format that gave none. */
        void flatNormals(int start) {
            Imported.flatNormals(pos, nrm, start);
        }

        void finish(Imported m) {
            if (n == 0) return;
            part.pos = Arrays.copyOf(pos, n * 3);
            part.nrm = Arrays.copyOf(nrm, n * 3);
            part.uv = Arrays.copyOf(uv, n * 2);
            if (col != null) part.vcol = Arrays.copyOf(col, n);
            part.bone = Arrays.copyOf(kind, n);
            part.piece = Arrays.copyOf(pieces, n);
            part.count = n;
            m.parts.add(part);
            m.triangles += n / 3;
        }
    }
}
