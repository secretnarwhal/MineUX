package com.mineux.screens.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * The relay's messages: text frames are JSON, binary frames
 * are {@code [16 bytes screen uuid][1 byte codec][4 bytes big-endian seq][payload]}.
 */
public final class Wire {

    public static final int VERSION = 1;
    public static final int CODEC_JPEG = 1;
    public static final int HEADER = 21;
    /** Largest binary frame the relay takes, header included. */
    public static final int MAX_FRAME = 256 * 1024;

    private Wire() {}

    /** The room of a server: hex sha256 of its lower-cased address. */
    public static String room(String serverAddress) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(serverAddress.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hello(String token, String room, UUID player, String name) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "hello");
        o.addProperty("v", VERSION);
        if (token == null) o.add("token", com.google.gson.JsonNull.INSTANCE);
        else o.addProperty("token", token);
        o.addProperty("room", room);
        o.addProperty("player", player.toString());
        o.addProperty("name", name);
        return o.toString();
    }

    public static String unshare(UUID id) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "unshare");
        o.addProperty("id", id.toString());
        return o.toString();
    }

    public static String watch(Collection<UUID> ids) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "watch");
        JsonArray a = new JsonArray();
        for (UUID id : ids) a.add(id.toString());
        o.add("ids", a);
        return o.toString();
    }

    public static ByteBuffer frame(UUID id, int codec, int seq, byte[] payload) {
        ByteBuffer b = ByteBuffer.allocate(HEADER + payload.length);
        b.putLong(id.getMostSignificantBits());
        b.putLong(id.getLeastSignificantBits());
        b.put((byte) codec);
        b.putInt(seq);
        b.put(payload);
        b.flip();
        return b;
    }

    /** A received binary frame, split. */
    public record Frame(UUID id, int codec, int seq, byte[] payload) {}

    /** Null when it is too short to be a frame. */
    public static Frame parseFrame(byte[] bytes) {
        if (bytes.length < HEADER) return null;
        ByteBuffer b = ByteBuffer.wrap(bytes);
        UUID id = new UUID(b.getLong(), b.getLong());
        int codec = b.get() & 0xFF;
        int seq = b.getInt();
        byte[] payload = new byte[bytes.length - HEADER];
        b.get(payload);
        return new Frame(id, codec, seq, payload);
    }
}
