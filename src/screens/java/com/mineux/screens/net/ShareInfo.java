package com.mineux.screens.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.UUID;

/**
 * One shared screen as the relay carries it: where it is,
 * how big, how bent, and the pixel size of its stream. No title, no window or monitor name:
 * nothing about what is being shown travels but the pictures themselves.
 * <p>
 * {@link #ANCHOR_PLAYER}: pos is the centre in the owner's view frame (right, up, forward
 * from the eyes) and rot the facing in that frame; {@link #ANCHOR_WORLD}: world coordinates.
 * Plain data, no game classes, so the relay client runs headless.
 */
public final class ShareInfo {

    public static final String ANCHOR_WORLD = "world";
    public static final String ANCHOR_PLAYER = "player";

    public UUID id;
    public String dim = "";
    public String anchor = ANCHOR_WORLD;
    public double x, y, z;
    public float yaw, pitch;
    public double width, height;
    public float curve;
    public int pxWidth, pxHeight;
    /** Set by the relay on screens from others. */
    public UUID owner;
    public String ownerName = "";

    public boolean player() {
        return anchor.equals(ANCHOR_PLAYER);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("t", "share");
        o.addProperty("id", id.toString());
        o.addProperty("dim", dim);
        o.addProperty("anchor", anchor);
        o.add("pos", array(x, y, z));
        o.add("rot", array(yaw, pitch));
        o.add("size", array(width, height));
        o.addProperty("curve", curve);
        JsonArray px = new JsonArray();
        px.add(pxWidth);
        px.add(pxHeight);
        o.add("px", px);
        return o;
    }

    /** From a share, screen or list entry; null when it is not a usable one. */
    public static ShareInfo fromJson(JsonObject o) {
        try {
            ShareInfo s = new ShareInfo();
            s.id = UUID.fromString(o.get("id").getAsString());
            s.dim = string(o, "dim");
            s.anchor = string(o, "anchor").equals(ANCHOR_PLAYER) ? ANCHOR_PLAYER : ANCHOR_WORLD;
            JsonArray pos = o.getAsJsonArray("pos");
            s.x = pos.get(0).getAsDouble();
            s.y = pos.get(1).getAsDouble();
            s.z = pos.get(2).getAsDouble();
            JsonArray rot = o.getAsJsonArray("rot");
            s.yaw = rot.get(0).getAsFloat();
            s.pitch = rot.get(1).getAsFloat();
            JsonArray size = o.getAsJsonArray("size");
            s.width = Math.clamp(size.get(0).getAsDouble(), 0.05, 64.0);
            s.height = Math.clamp(size.get(1).getAsDouble(), 0.05, 64.0);
            s.curve = o.has("curve") ? Math.clamp(o.get("curve").getAsFloat(), 0f, 1f) : 0f;
            JsonArray px = o.getAsJsonArray("px");
            s.pxWidth = px.get(0).getAsInt();
            s.pxHeight = px.get(1).getAsInt();
            if (o.has("owner") && !o.get("owner").isJsonNull()) s.owner = UUID.fromString(o.get("owner").getAsString());
            s.ownerName = string(o, "ownerName");
            return s;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** What a resend compares: the fields rounded, so float noise does not resend every tick. */
    public String key() {
        return String.format(Locale.ROOT, "%s|%s|%.2f|%.2f|%.2f|%.1f|%.1f|%.3f|%.3f|%.2f|%d|%d",
            dim, anchor, x, y, z, yaw, pitch, width, height, curve, pxWidth, pxHeight);
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static JsonArray array(double... values) {
        JsonArray a = new JsonArray();
        for (double v : values) a.add(Math.round(v * 1000.0) / 1000.0);
        return a;
    }
}
