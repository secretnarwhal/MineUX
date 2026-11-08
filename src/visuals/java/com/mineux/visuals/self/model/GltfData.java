package com.mineux.visuals.self.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.ByteBuffer;
import java.util.Objects;

/** A glTF file's accessors read out of its buffers: as ints, as floats, or a buffer view's bytes. */
final class GltfData {

    private static final int BYTE = 5120, UNSIGNED_BYTE = 5121, SHORT = 5122, UNSIGNED_SHORT = 5123, UNSIGNED_INT = 5125, FLOAT = 5126;

    private final JsonArray accessors;
    private final JsonArray views;
    private final ByteBuffer[] buffers;

    GltfData(JsonObject g, ByteBuffer[] buffers) {
        this.accessors = GltfLoad.arr(g, "accessors");
        this.views = GltfLoad.arr(g, "bufferViews");
        this.buffers = buffers;
    }

    byte[] view(int vi) {
        JsonObject v = views.get(vi).getAsJsonObject();
        ByteBuffer b = buffers[v.get("buffer").getAsInt()];
        int off = v.has("byteOffset") ? v.get("byteOffset").getAsInt() : 0;
        byte[] out = new byte[v.get("byteLength").getAsInt()];
        ByteBuffer d = b.duplicate();
        d.position(off);
        d.get(out);
        return out;
    }

    int components(int ai) {
        return comps(accessors.get(ai).getAsJsonObject().get("type").getAsString());
    }

    private static int comps(String t) {
        if (Objects.equals("VEC2", t)) return 2;
        if (Objects.equals("VEC3", t)) return 3;
        if (Objects.equals("VEC4", t) || Objects.equals("MAT2", t)) return 4;
        if (Objects.equals("MAT3", t)) return 9;
        if (Objects.equals("MAT4", t)) return 16;
        return 1;
    }

    private static int size(int ct) {
        if (ct == BYTE || ct == UNSIGNED_BYTE) return 1;
        if (ct == SHORT || ct == UNSIGNED_SHORT) return 2;
        return 4;
    }

    private int base(JsonObject a, JsonObject v) {
        return (v.has("byteOffset") ? v.get("byteOffset").getAsInt() : 0) + (a.has("byteOffset") ? a.get("byteOffset").getAsInt() : 0);
    }

    int[] ints(int ai) {
        JsonObject a = accessors.get(ai).getAsJsonObject();
        int count = a.get("count").getAsInt();
        int ct = a.get("componentType").getAsInt();
        JsonObject v = views.get(a.get("bufferView").getAsInt()).getAsJsonObject();
        ByteBuffer b = buffers[v.get("buffer").getAsInt()];
        int base = base(a, v);
        int stride = v.has("byteStride") ? v.get("byteStride").getAsInt() : size(ct);
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            int p = base + i * stride;
            if (ct == UNSIGNED_BYTE) out[i] = b.get(p) & 0xFF;
            else if (ct == UNSIGNED_SHORT) out[i] = b.getShort(p) & 0xFFFF;
            else out[i] = b.getInt(p);
        }
        return out;
    }

    float[] floats(int ai) {
        JsonObject a = accessors.get(ai).getAsJsonObject();
        int count = a.get("count").getAsInt();
        int comps = comps(a.get("type").getAsString());
        int ct = a.get("componentType").getAsInt();
        boolean norm = a.has("normalized") && a.get("normalized").getAsBoolean();
        float[] out = new float[count * comps];
        if (!a.has("bufferView")) return out;
        JsonObject v = views.get(a.get("bufferView").getAsInt()).getAsJsonObject();
        ByteBuffer b = buffers[v.get("buffer").getAsInt()];
        int base = base(a, v);
        int size = size(ct);
        int stride = v.has("byteStride") ? v.get("byteStride").getAsInt() : comps * size;
        for (int i = 0; i < count; i++) {
            for (int k = 0; k < comps; k++) out[i * comps + k] = read(b, base + i * stride + k * size, ct, norm);
        }
        return out;
    }

    private static float read(ByteBuffer b, int p, int ct, boolean norm) {
        if (ct == FLOAT) return b.getFloat(p);
        if (ct == BYTE) return norm ? Math.max(b.get(p) / 127.0F, 0.0F - 1.0F) : b.get(p);
        if (ct == UNSIGNED_BYTE) return norm ? (b.get(p) & 0xFF) / 255.0F : b.get(p) & 0xFF;
        if (ct == SHORT) return norm ? Math.max(b.getShort(p) / 32767.0F, 0.0F - 1.0F) : b.getShort(p);
        if (ct == UNSIGNED_SHORT) return norm ? (b.getShort(p) & 0xFFFF) / 65535.0F : b.getShort(p) & 0xFFFF;
        if (ct == UNSIGNED_INT) return (float) (b.getInt(p) & 0xFFFFFFFFL);
        return 0.0F;
    }
}
