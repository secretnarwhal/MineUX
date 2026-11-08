package com.mineux.core.rpc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Discord's local RPC socket, spoken directly: a named pipe on Windows
 * ({@code \\.\pipe\discord-ipc-N}), a Unix socket elsewhere. No library and no native code --
 * the client only ever handshakes and sets its activity.
 * <p>
 * A frame is an opcode and a length, both little-endian ints, then that many bytes of JSON.
 * Everything here is one thread's: a request is written and its answer read before the next,
 * because a synchronous pipe handle blocked in a read on Windows blocks a write on it too.
 */
final class DiscordIpc implements Closeable {

    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;
    private static final int OP_PING = 3;
    private static final int OP_PONG = 4;

    /** Discord takes the first pipe free from 0, and its PTB and Canary the ones after. */
    private static final int PIPES = 10;
    /** Frames read past while waiting for an answer before it is given up on. */
    private static final int PATIENCE = 16;
    /** Bigger than anything Discord sends back to a client like this one. */
    private static final int MAX_FRAME = 1 << 20;

    private final RandomAccessFile pipe;
    private final SocketChannel socket;
    private int nonce;

    private DiscordIpc(RandomAccessFile pipe, SocketChannel socket) {
        this.pipe = pipe;
        this.socket = socket;
    }

    /** The first Discord found, handshaken as the application; throws when none is running. */
    static DiscordIpc open(String appId) throws IOException {
        DiscordIpc ipc = connect();
        try {
            JsonObject hello = new JsonObject();
            hello.addProperty("v", 1);
            hello.addProperty("client_id", appId);
            ipc.write(OP_HANDSHAKE, hello.toString());
            // READY, or a close saying why not (an application id Discord does not know).
            Frame ready = ipc.read();
            if (ready.op == OP_CLOSE) throw new IOException("Discord refused the handshake: " + ready.body);
            return ipc;
        } catch (IOException | RuntimeException e) {
            ipc.close();
            throw e;
        }
    }

    private static DiscordIpc connect() throws IOException {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (windows) {
            for (int i = 0; i < PIPES; i++) {
                try {
                    return new DiscordIpc(new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw"), null);
                } catch (IOException e) {
                    // Not that one.
                }
            }
            throw new IOException("Discord is not running");
        }
        for (Path dir : socketDirs()) {
            for (int i = 0; i < PIPES; i++) {
                Path path = dir.resolve("discord-ipc-" + i);
                if (!Files.exists(path)) continue;
                SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
                try {
                    channel.connect(UnixDomainSocketAddress.of(path));
                    return new DiscordIpc(null, channel);
                } catch (IOException e) {
                    channel.close();
                }
            }
        }
        throw new IOException("Discord is not running");
    }

    /** Where Discord puts its socket: the runtime dir, the temp dirs, and the Flatpak and Snap sandboxes inside them. */
    private static List<Path> socketDirs() {
        List<Path> roots = new ArrayList<>();
        String[] vars = {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"};
        for (String var : vars) {
            String v = System.getenv(var);
            if (v != null && !v.isBlank()) roots.add(Path.of(v));
        }
        roots.add(Path.of("/tmp"));
        List<Path> dirs = new ArrayList<>();
        String[] sandboxes = {"", "app/com.discordapp.Discord", "app/com.discordapp.DiscordCanary", "snap.discord", "snap.discord-canary", ".flatpak/dev.vencord.Vesktop/xdg-run"};
        for (Path root : roots) {
            for (String sub : sandboxes) {
                Path dir = sub.isEmpty() ? root : root.resolve(sub);
                if (!dirs.contains(dir)) dirs.add(dir);
            }
        }
        return dirs;
    }

    /**
     * Sets the activity this process shows, or clears it for a null one, and waits for Discord's
     * answer. Throws when Discord says no or has gone.
     */
    void setActivity(JsonObject activity) throws IOException {
        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        if (activity != null) args.add("activity", activity);
        nonce++;
        String id = "mux-" + nonce;
        JsonObject cmd = new JsonObject();
        cmd.addProperty("cmd", "SET_ACTIVITY");
        cmd.add("args", args);
        cmd.addProperty("nonce", id);
        write(OP_FRAME, cmd.toString());

        for (int i = 0; i < PATIENCE; i++) {
            Frame frame = read();
            if (frame.op == OP_PING) {
                write(OP_PONG, frame.body);
                continue;
            }
            if (frame.op == OP_CLOSE) throw new IOException("Discord closed the connection: " + frame.body);
            if (frame.op != OP_FRAME) continue;
            JsonObject answer = parse(frame.body);
            if (answer == null || !answer.has("nonce") || !id.equals(answer.get("nonce").getAsString())) continue;
            JsonElement evt = answer.get("evt");
            if (evt != null && evt.isJsonPrimitive() && evt.getAsString().equals("ERROR")) {
                throw new RefusedException(String.valueOf(answer.get("data")));
            }
            return;
        }
        throw new IOException("Discord never answered");
    }

    private static JsonObject parse(String body) {
        try {
            JsonElement e = JsonParser.parseString(body);
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void write(int op, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer frame = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        frame.putInt(op).putInt(body.length).put(body);
        if (pipe != null) {
            pipe.write(frame.array());
        } else {
            frame.flip();
            while (frame.hasRemaining()) socket.write(frame);
        }
    }

    private Frame read() throws IOException {
        ByteBuffer head = ByteBuffer.wrap(readFully(8)).order(ByteOrder.LITTLE_ENDIAN);
        int op = head.getInt();
        int length = head.getInt();
        if (length < 0 || length > MAX_FRAME) throw new IOException("Bad frame from Discord: " + length + " bytes");
        return new Frame(op, new String(readFully(length), StandardCharsets.UTF_8));
    }

    private byte[] readFully(int n) throws IOException {
        byte[] out = new byte[n];
        if (pipe != null) {
            pipe.readFully(out);
            return out;
        }
        ByteBuffer buf = ByteBuffer.wrap(out);
        while (buf.hasRemaining()) {
            if (socket.read(buf) < 0) throw new IOException("Discord closed the connection");
        }
        return out;
    }

    @Override
    public void close() {
        try {
            if (pipe != null) pipe.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            // Gone either way.
        }
    }

    private static final class Frame {
        final int op;
        final String body;

        Frame(int op, String body) {
            this.op = op;
            this.body = body;
        }
    }

    /** Discord took the request and said no: the activity is wrong, not the connection. */
    static final class RefusedException extends IOException {
        RefusedException(String why) {
            super(why);
        }
    }
}
