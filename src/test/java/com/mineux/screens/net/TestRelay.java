package com.mineux.screens.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The server side of the screens relay, small and in memory, for tests and
 * for trying two clients by hand: a bare RFC 6455 server on the JDK's sockets, with the same
 * rules as the real one. Friends only: a guest (no token) is told to sign in and closed with
 * 4001; two accounts see each other's screens (list, updates, viewer counts, frames) only while
 * {@link #screens} says both have screens on for each other and they are in the same room, and a
 * change re-cuts it live ({@code gone} and the watch dropped, or {@code screen}). 4 shares per
 * account, viewers to the owner, frames only to watchers, the owner's screens gone when it
 * disconnects. A token is its account: any non-empty token is signed in as itself.
 * {@code main} runs it on a port: {@code TestRelay 8787}.
 */
public final class TestRelay implements AutoCloseable {

    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final ServerSocket server;
    private final Thread acceptor;
    private final Set<Conn> conns = new HashSet<>();
    /** Per room, keyed by {@code account/id}: another account's id is never taken. */
    private final Map<String, Map<String, Shared>> rooms = new HashMap<>();
    /** Pairs of accounts (tokens) with screens on both ways, as {@code low|high}. */
    private final Set<String> pairs = new HashSet<>();
    public volatile int framesRelayed;
    /** While set, nothing is read: the clients' sends back up, as on a slow link. */
    public volatile boolean stalled;

    private record Shared(JsonObject share, Conn owner, Set<Conn> watchers) {}

    public TestRelay(int port) throws IOException {
        server = new ServerSocket(port, 50, InetAddress.getLoopbackAddress());
        acceptor = new Thread(this::accept, "test-relay");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    public int port() {
        return server.getLocalPort();
    }

    public String url() {
        return "ws://127.0.0.1:" + port() + "/api/screens";
    }

    public static void main(String[] args) throws Exception {
        TestRelay relay = new TestRelay(args.length > 0 ? Integer.parseInt(args[0]) : 8787);
        System.out.println("screens test relay on " + relay.url());
        Thread.currentThread().join();
    }

    private void accept() {
        while (!server.isClosed()) {
            try {
                Socket s = server.accept();
                Thread t = new Thread(() -> serve(s), "test-relay-conn");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    /** Drops every connection (as a restart would) but keeps listening. */
    public synchronized void kickAll() {
        for (Conn c : Set.copyOf(conns)) c.close();
    }

    @Override
    public void close() throws IOException {
        server.close();
        kickAll();
    }

    // ---- one connection -------------------------------------------------

    private final class Conn {
        final Socket socket;
        final OutputStream out;
        String room;
        String player = "";
        String name = "";
        /** The account (its token), or null before a signed-in hello. */
        String account;
        final Set<String> watching = new HashSet<>();
        final Set<String> owned = new HashSet<>();

        Conn(Socket socket) throws IOException {
            this.socket = socket;
            this.out = socket.getOutputStream();
        }

        synchronized void send(int opcode, byte[] payload) {
            try {
                ByteArrayOutputStream b = new ByteArrayOutputStream(payload.length + 10);
                b.write(0x80 | opcode);
                if (payload.length < 126) b.write(payload.length);
                else if (payload.length < 65536) {
                    b.write(126);
                    b.write(payload.length >> 8);
                    b.write(payload.length);
                } else {
                    b.write(127);
                    for (int i = 7; i >= 0; i--) b.write((int) ((long) payload.length >> (8 * i)));
                }
                b.write(payload);
                out.write(b.toByteArray());
                out.flush();
            } catch (IOException e) {
                close();
            }
        }

        void text(JsonObject o) {
            send(1, o.toString().getBytes(StandardCharsets.UTF_8));
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // gone
            }
        }
    }

    private void serve(Socket socket) {
        Conn c;
        try {
            c = new Conn(socket);
            if (!handshake(socket.getInputStream(), c.out)) {
                socket.close();
                return;
            }
        } catch (IOException e) {
            return;
        }
        synchronized (this) {
            conns.add(c);
        }
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            ByteArrayOutputStream message = new ByteArrayOutputStream();
            int messageOp = 0;
            while (true) {
                while (stalled) Thread.sleep(10);
                int b0 = in.readUnsignedByte();
                int b1 = in.readUnsignedByte();
                int op = b0 & 0x0F;
                long len = b1 & 0x7F;
                if (len == 126) len = in.readUnsignedShort();
                else if (len == 127) len = in.readLong();
                byte[] mask = new byte[4];
                if ((b1 & 0x80) != 0) in.readFully(mask);
                byte[] data = new byte[(int) len];
                in.readFully(data);
                for (int i = 0; i < data.length; i++) data[i] ^= mask[i & 3];
                if (op == 8) break;
                if (op == 9) {
                    c.send(10, data);
                    continue;
                }
                if (op == 10) continue;
                if (op != 0) messageOp = op;
                message.write(data);
                if ((b0 & 0x80) == 0) continue;
                byte[] whole = message.toByteArray();
                message.reset();
                if (messageOp == 1) onText(c, new String(whole, StandardCharsets.UTF_8));
                else if (messageOp == 2) onBinary(c, whole);
            }
        } catch (IOException | InterruptedException e) {
            // closed
        } finally {
            leave(c);
            c.close();
            synchronized (this) {
                conns.remove(c);
            }
        }
    }

    private static boolean handshake(InputStream in, OutputStream out) throws IOException {
        StringBuilder head = new StringBuilder();
        while (!head.toString().endsWith("\r\n\r\n")) {
            int ch = in.read();
            if (ch < 0) return false;
            head.append((char) ch);
        }
        String key = null;
        for (String line : head.toString().split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Sec-WebSocket-Key")) key = line.substring(colon + 1).trim();
        }
        if (key == null || !head.toString().startsWith("GET /api/screens")) {
            out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            return false;
        }
        String accept;
        try {
            accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + GUID).getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII));
        out.flush();
        return true;
    }

    // ---- friends --------------------------------------------------------

    private static String pair(String a, String b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    /**
     * Whether accounts (tokens) {@code a} and {@code b} both have screens on for each other, as the
     * friends service would say; applied to open connections at once.
     */
    public synchronized void screens(String a, String b, boolean both) {
        String k = pair(a, b);
        if (both == pairs.contains(k)) return;
        if (both) pairs.add(k);
        for (Conn x : Set.copyOf(conns)) {
            for (Conn y : Set.copyOf(conns)) {
                if (!a.equals(x.account) || !b.equals(y.account)) continue;
                if (sees(x, y)) recut(x, y, both);
                if (sees(y, x)) recut(y, x, both);
            }
        }
        if (!both) pairs.remove(k);
    }

    /** The one rule: signed in, different accounts, screens on both ways, same room. */
    private boolean sees(Conn viewer, Conn owner) {
        return viewer != owner && viewer.account != null && owner.account != null
            && !viewer.account.equals(owner.account) && viewer.room != null && viewer.room.equals(owner.room)
            && pairs.contains(pair(viewer.account, owner.account));
    }

    private void recut(Conn viewer, Conn owner, boolean on) {
        for (String id : Set.copyOf(owner.owned)) {
            Shared s = room(owner).get(owner.account + "/" + id);
            if (s == null) continue;
            if (on) {
                viewer.text(wire(s, true));
                if (viewer.watching.contains(id) && s.watchers.add(viewer)) viewers(s);
            } else {
                if (s.watchers.remove(viewer)) viewers(s);
                viewer.watching.remove(id);
                viewer.text(gone(id));
            }
        }
    }

    // ---- the relay's rules ----------------------------------------------

    private synchronized void onText(Conn c, String text) {
        JsonObject m = JsonParser.parseString(text).getAsJsonObject();
        String t = m.get("t").getAsString();
        if (t.equals("hello")) {
            String token = m.has("token") && !m.get("token").isJsonNull() ? m.get("token").getAsString() : "";
            if (token.isEmpty()) {
                refuse(c);
                return;
            }
            leave(c);
            c.account = token;
            c.room = m.get("room").getAsString();
            c.player = m.get("player").getAsString();
            c.name = m.get("name").getAsString();
            JsonObject welcome = new JsonObject();
            welcome.addProperty("t", "welcome");
            welcome.addProperty("you", c.player);
            c.text(welcome);
            JsonObject list = new JsonObject();
            list.addProperty("t", "screens");
            JsonArray a = new JsonArray();
            for (Shared s : room(c).values()) if (sees(c, s.owner)) a.add(wire(s, false));
            list.add("list", a);
            c.text(list);
        } else if (c.room == null || c.account == null) {
            error(c, "say hello first");
        } else if (t.equals("share")) {
            share(c, m);
        } else if (t.equals("unshare")) {
            String id = m.get("id").getAsString();
            if (c.owned.contains(id)) unshare(c, id);
        } else if (t.equals("watch")) {
            Set<String> wanted = new HashSet<>();
            for (JsonElement e : m.getAsJsonArray("ids")) wanted.add(e.getAsString());
            for (Shared s : room(c).values()) {
                if (!sees(c, s.owner)) continue;
                boolean changed = wanted.contains(s.share.get("id").getAsString()) ? s.watchers.add(c) : s.watchers.remove(c);
                if (changed) viewers(s);
            }
            c.watching.clear();
            c.watching.addAll(wanted);
        } else {
            error(c, "unknown message");
        }
    }

    /** Not signed in: one error, a 4001 close, and nothing else. */
    private static void refuse(Conn c) {
        error(c, "sign in to MineUX: screens are shared with friends only");
        byte[] reason = "sign in".getBytes(StandardCharsets.UTF_8);
        byte[] close = new byte[2 + reason.length];
        close[0] = (byte) (RelayClient.SIGNED_OUT >> 8);
        close[1] = (byte) RelayClient.SIGNED_OUT;
        System.arraycopy(reason, 0, close, 2, reason.length);
        c.send(8, close);
        c.close();
    }

    private void share(Conn c, JsonObject m) {
        String id = m.get("id").getAsString();
        String key = c.account + "/" + id;
        Shared have = room(c).get(key);
        if (have != null && have.owner != c) {
            error(c, "that screen id is taken");
            return;
        }
        if (have == null && c.owned.size() >= 4) {
            error(c, "at most 4 shared screens");
            return;
        }
        Shared s = new Shared(m, c, have != null ? have.watchers : new HashSet<>());
        room(c).put(key, s);
        if (have == null) {
            c.owned.add(id);
            for (Conn other : conns) if (other.watching.contains(id) && sees(other, c)) s.watchers.add(other);
            if (!s.watchers.isEmpty()) viewers(s);
        }
        for (Conn other : conns) if (sees(other, c)) other.text(wire(s, true));
    }

    private void unshare(Conn c, String id) {
        for (Conn other : conns) if (sees(other, c)) other.text(gone(id));
        room(c).remove(c.account + "/" + id);
        c.owned.remove(id);
    }

    private synchronized void leave(Conn c) {
        if (c.room == null) return;
        for (String id : Set.copyOf(c.owned)) unshare(c, id);
        for (Shared s : room(c).values()) if (s.watchers.remove(c)) viewers(s);
        c.watching.clear();
        c.room = null;
    }

    private void onBinary(Conn c, byte[] data) {
        Set<Conn> to = new HashSet<>();
        synchronized (this) {
            if (c.room == null || c.account == null || data.length <= Wire.HEADER) return;
            Wire.Frame f = Wire.parseFrame(data);
            Shared s = room(c).get(c.account + "/" + f.id());
            if (s == null || s.owner != c || s.watchers.isEmpty()) return;
            for (Conn w : s.watchers) if (sees(w, c)) to.add(w);
        }
        framesRelayed++;
        for (Conn w : to) w.send(2, data);
    }

    private Map<String, Shared> room(Conn c) {
        return rooms.computeIfAbsent(c.room, k -> new LinkedHashMap<>());
    }

    private static JsonObject wire(Shared s, boolean single) {
        JsonObject o = s.share.deepCopy();
        o.addProperty("t", single ? "screen" : "share");
        o.addProperty("owner", s.owner.player);
        o.addProperty("ownerName", s.owner.name);
        return o;
    }

    private static JsonObject gone(String id) {
        JsonObject gone = new JsonObject();
        gone.addProperty("t", "gone");
        gone.addProperty("id", id);
        return gone;
    }

    private static void viewers(Shared s) {
        JsonObject v = new JsonObject();
        v.addProperty("t", "viewers");
        v.addProperty("id", s.share.get("id").getAsString());
        v.addProperty("n", s.watchers.size());
        s.owner.text(v);
    }

    private static void error(Conn c, String msg) {
        JsonObject e = new JsonObject();
        e.addProperty("t", "error");
        e.addProperty("msg", msg);
        c.text(e);
    }

    /** For a test that wants a fixed id. */
    static UUID uuid(String s) {
        return UUID.fromString(s);
    }
}
