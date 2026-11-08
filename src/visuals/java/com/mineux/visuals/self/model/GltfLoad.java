package com.mineux.visuals.self.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * glTF 2.0, as .gltf (with its buffers and images in files beside it or inline) or .glb: the
 * default scene's node tree with each node's transform, triangle lists, strips and fans, normals,
 * the first texture coordinates, vertex colours, and each material's base colour and texture. One
 * {@link Piece} per material. Draco- or meshopt-compressed files are refused; skins and animations
 * are not read -- the limbs swing by {@link Imported}'s own rig.
 */
final class GltfLoad {

    private static final int GLB_MAGIC = 0x46546C67;
    private static final int CHUNK_JSON = 0x4E4F534A;
    private static final int CHUNK_BIN = 0x004E4942;

    /** A node waiting to be read: its index, its parent's world matrix, and the bone its parent was on. */
    private record Pending(int node, float[] world, byte kind) {}

    private GltfLoad() {}

    static void loadGlb(Imported m, Path file) throws IOException {
        byte[] all = Files.readAllBytes(file);
        ByteBuffer bb = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
        if (bb.remaining() < 20 || bb.getInt(0) != GLB_MAGIC) throw new IOException("not a .glb file");
        int pos = 12;
        JsonObject json = null;
        ByteBuffer bin = null;
        while (pos + 8 <= bb.limit()) {
            int len = bb.getInt(pos);
            int type = bb.getInt(pos + 4);
            if (len < 0 || pos + 8 + len > bb.limit()) break;
            if (type == CHUNK_JSON) json = JsonParser.parseString(new String(all, pos + 8, len, StandardCharsets.UTF_8)).getAsJsonObject();
            else if (type == CHUNK_BIN && bin == null) bin = ByteBuffer.wrap(all, pos + 8, len).slice().order(ByteOrder.LITTLE_ENDIAN);
            pos += 8 + len;
        }
        if (json == null) throw new IOException("broken .glb (no json)");
        load(m, json, bin, file.toAbsolutePath().getParent());
    }

    static void loadGltf(Imported m, Path file) throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        load(m, json, null, file.toAbsolutePath().getParent());
    }

    private static void load(Imported m, JsonObject g, ByteBuffer glbBin, Path dir) throws IOException {
        for (JsonElement e : arr(g, "extensionsRequired")) {
            String ext = e.getAsString();
            if (ext.contains("draco") || ext.contains("meshopt")) {
                throw new IOException("compressed glTF (" + ext + ") is not read; export it without compression");
            }
        }
        JsonArray bufs = arr(g, "buffers");
        ByteBuffer[] buffers = new ByteBuffer[bufs.size()];
        for (int i = 0; i < bufs.size(); i++) {
            JsonObject b = bufs.get(i).getAsJsonObject();
            buffers[i] = b.has("uri") ? ByteBuffer.wrap(readUri(b.get("uri").getAsString(), dir)).order(ByteOrder.LITTLE_ENDIAN) : glbBin;
        }
        GltfData c = new GltfData(g, buffers);
        String[] imageKey = images(m, c, g, dir);

        int scene = g.has("scene") ? g.get("scene").getAsInt() : 0;
        JsonArray scenes = arr(g, "scenes");
        JsonArray nodes = arr(g, "nodes");
        Deque<Pending> stack = new ArrayDeque<>();
        if (scenes.size() > scene) {
            for (JsonElement e : arr(scenes.get(scene).getAsJsonObject(), "nodes")) stack.push(new Pending(e.getAsInt(), identity(), Imported.BODY));
        } else {
            for (int i = 0; i < nodes.size(); i++) stack.push(new Pending(i, identity(), Imported.BODY));
        }
        Map<Integer, Piece.Builder> builders = new LinkedHashMap<>();
        Materials materials = new Materials(arr(g, "materials"), arr(g, "textures"), imageKey);
        JsonArray meshes = arr(g, "meshes");
        int guard = 0;
        while (!stack.isEmpty() && guard++ < 100000) {
            Pending it = stack.pop();
            if (it.node() < 0 || it.node() >= nodes.size()) continue;
            JsonObject node = nodes.get(it.node()).getAsJsonObject();
            float[] world = mul(it.world(), local(node));
            byte kind = Imported.kindOf(node.has("name") ? node.get("name").getAsString() : null);
            if (kind == Imported.BODY && node.has("mesh")) {
                int mi = node.get("mesh").getAsInt();
                if (mi >= 0 && mi < meshes.size() && meshes.get(mi).getAsJsonObject().has("name")) {
                    kind = Imported.kindOf(meshes.get(mi).getAsJsonObject().get("name").getAsString());
                }
            }
            if (kind == Imported.BODY) kind = it.kind();
            if (node.has("mesh")) GltfMesh.read(m, c, meshes, node.get("mesh").getAsInt(), world, builders, materials, kind, it.node());
            for (JsonElement ch : arr(node, "children")) stack.push(new Pending(ch.getAsInt(), world, kind));
        }
        for (Piece.Builder b : builders.values()) b.finish(m);
    }

    /** Every image's bytes into the model, keyed by its index. */
    private static String[] images(Imported m, GltfData c, JsonObject g, Path dir) {
        JsonArray images = arr(g, "images");
        String[] imageKey = new String[images.size()];
        for (int i = 0; i < images.size(); i++) {
            JsonObject im = images.get(i).getAsJsonObject();
            try {
                byte[] bytes;
                if (im.has("bufferView")) bytes = c.view(im.get("bufferView").getAsInt());
                else if (im.has("uri")) bytes = readUri(im.get("uri").getAsString(), dir);
                else continue;
                imageKey[i] = "gltf:" + i;
                m.images.put(imageKey[i], bytes);
            } catch (Exception ignored) {
                // That material draws untextured.
            }
        }
        return imageKey;
    }

    /** The materials, as {@link Piece}s with their colour and texture. */
    static final class Materials {
        private final JsonArray materials;
        private final JsonArray textures;
        private final String[] imageKey;

        Materials(JsonArray materials, JsonArray textures, String[] imageKey) {
            this.materials = materials;
            this.textures = textures;
            this.imageKey = imageKey;
        }

        Piece.Builder builder(int k) {
            int color = 0xFFFFFFFF;
            String img = null;
            if (k >= 0 && k < materials.size()) {
                JsonObject mat = materials.get(k).getAsJsonObject();
                JsonObject pbr = mat.getAsJsonObject("pbrMetallicRoughness");
                if (pbr != null) {
                    if (pbr.has("baseColorFactor")) {
                        JsonArray f = pbr.getAsJsonArray("baseColorFactor");
                        color = Imported.rgba(f.get(0).getAsFloat(), f.get(1).getAsFloat(), f.get(2).getAsFloat(), f.size() > 3 ? f.get(3).getAsFloat() : 1.0F);
                    }
                    if (pbr.has("baseColorTexture")) img = image(pbr.getAsJsonObject("baseColorTexture"));
                }
                JsonObject ext = mat.getAsJsonObject("extensions");
                if (img == null && ext != null && ext.has("KHR_materials_pbrSpecularGlossiness")) {
                    JsonObject sg = ext.getAsJsonObject("KHR_materials_pbrSpecularGlossiness");
                    if (sg.has("diffuseTexture")) img = image(sg.getAsJsonObject("diffuseTexture"));
                }
            }
            return new Piece.Builder(new Piece(img, color));
        }

        private String image(JsonObject texInfo) {
            int ti = texInfo.has("index") ? texInfo.get("index").getAsInt() : 0 - 1;
            if (ti < 0 || ti >= textures.size()) return null;
            JsonObject tex = textures.get(ti).getAsJsonObject();
            int src = tex.has("source") ? tex.get("source").getAsInt() : 0 - 1;
            // Basis and webp textures have no source the game can read.
            return src >= 0 && src < imageKey.length ? imageKey[src] : null;
        }
    }

    private static byte[] readUri(String uri, Path dir) throws IOException {
        if (uri.startsWith("data:")) return Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1));
        Path p = dir.resolve(URLDecoder.decode(uri, StandardCharsets.UTF_8)).normalize();
        if (!p.startsWith(dir.normalize())) throw new IOException("a file reference outside the model's folder: " + uri);
        return Files.readAllBytes(p);
    }

    static float[] identity() {
        return new float[] {1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F};
    }

    /** A node's matrix, column major, from its own or its translation, rotation and scale. */
    private static float[] local(JsonObject n) {
        if (n.has("matrix")) {
            JsonArray a = n.getAsJsonArray("matrix");
            float[] m = new float[16];
            for (int i = 0; i < 16; i++) m[i] = a.get(i).getAsFloat();
            return m;
        }
        float[] t = floats(n, "translation", 0.0F, 3);
        float[] q = n.has("rotation") ? floats(n, "rotation", 0.0F, 4) : new float[] {0.0F, 0.0F, 0.0F, 1.0F};
        float[] s = floats(n, "scale", 1.0F, 3);
        float xx = q[0] * q[0], yy = q[1] * q[1], zz = q[2] * q[2];
        float xy = q[0] * q[1], xz = q[0] * q[2], yz = q[1] * q[2];
        float wx = q[3] * q[0], wy = q[3] * q[1], wz = q[3] * q[2];
        return new float[] {
            (1.0F - 2.0F * (yy + zz)) * s[0], 2.0F * (xy + wz) * s[0], 2.0F * (xz - wy) * s[0], 0.0F,
            2.0F * (xy - wz) * s[1], (1.0F - 2.0F * (xx + zz)) * s[1], 2.0F * (yz + wx) * s[1], 0.0F,
            2.0F * (xz + wy) * s[2], 2.0F * (yz - wx) * s[2], (1.0F - 2.0F * (xx + yy)) * s[2], 0.0F,
            t[0], t[1], t[2], 1.0F};
    }

    private static float[] floats(JsonObject n, String key, float fill, int count) {
        float[] out = new float[count];
        java.util.Arrays.fill(out, fill);
        if (!n.has(key)) return out;
        JsonArray a = n.getAsJsonArray(key);
        for (int i = 0; i < count && i < a.size(); i++) out[i] = a.get(i).getAsFloat();
        return out;
    }

    private static float[] mul(float[] a, float[] b) {
        float[] o = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                o[c * 4 + r] = a[r] * b[c * 4] + a[4 + r] * b[c * 4 + 1] + a[8 + r] * b[c * 4 + 2] + a[12 + r] * b[c * 4 + 3];
            }
        }
        return o;
    }

    static JsonArray arr(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
    }
}
