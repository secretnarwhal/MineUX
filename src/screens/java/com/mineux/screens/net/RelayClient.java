package com.mineux.screens.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One client's connection to the screens relay ({@code <wsBase>/screens}), headless: plain
 * java.net.http, no game classes, so the tests run two of them against a test relay.
 * <p>
 * It keeps what it has said -- the screens it shares and the set it watches -- and says it
 * again after every reconnect, which backs off from 1 s to 30 s. Sends go out one after another
 * on one chain. A frame is never queued behind another of the same screen, nor past a byte
 * budget: it is dropped and counted ({@link #drops}), which the sender reads to slow down.
 * Callbacks come on the socket's threads; the game hands them to its own thread.
 * <p>
 * The relay is friends only and needs a MineUX sign-in: without one it says why and closes with
 * {@link #SIGNED_OUT}. That is final for this client -- no reconnect ({@link #refused}); a new
 * sign-in makes a new client.
 */
public final class RelayClient {

    /** What the relay says. An abstract class, not an interface: no lambdas for our own types. */
    public abstract static class Listener {
        public void onState(boolean connected) {}
        public void onWelcome(UUID you) {}
        public void onScreens(List<ShareInfo> list) {}
        public void onScreen(ShareInfo screen) {}
        public void onGone(UUID id) {}
        public void onViewers(UUID id, int viewers) {}
        public void onFrame(Wire.Frame frame) {}
        public void onError(String message) {}
        /** The relay will not have this connection (not signed in): no reconnect follows. */
        public void onRefused() {}
    }

    /** The relay's close code for a connection that is not signed in. */
    public static final int SIGNED_OUT = 4001;

    private static final long BUDGET = 768 * 1024;
    private static final int MIN_BACKOFF_MS = 1000;
    private static final int MAX_BACKOFF_MS = 30_000;

    private final URI uri;
    private final Listener listener;
    private final String hello;
    private final ExecutorService pool = Executors.newCachedThreadPool(daemon("mineux-screens-relay"));
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(daemon("mineux-screens-relay-timer"));
    private final HttpClient http = HttpClient.newBuilder().executor(pool).connectTimeout(Duration.ofSeconds(10)).build();

    private final Object lock = new Object();
    private final Map<UUID, String> shares = new LinkedHashMap<>();
    private String watching;
    private WebSocket socket;
    private CompletableFuture<?> chain = CompletableFuture.completedFuture(null);
    private volatile int generation;
    private volatile boolean welcomed;
    private volatile boolean stopped;
    private volatile boolean refused;
    private volatile int backoff = MIN_BACKOFF_MS;

    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final AtomicLong pending = new AtomicLong();
    private final AtomicInteger drops = new AtomicInteger();

    public RelayClient(URI uri, String hello, Listener listener) {
        this.uri = uri;
        this.hello = hello;
        this.listener = listener;
    }

    public void start() {
        connect();
    }

    /** Closes for good: no reconnect. */
    public void stop() {
        stopped = true;
        WebSocket ws;
        synchronized (lock) {
            ws = detach(socket);
        }
        welcomed = false;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").orTimeout(2, TimeUnit.SECONDS)
                    .whenComplete((r, e) -> ws.abort());
            } catch (RuntimeException e) {
                ws.abort();
            }
        }
        timer.schedule(pool::shutdown, 3, TimeUnit.SECONDS);
        timer.schedule(timer::shutdown, 4, TimeUnit.SECONDS);
    }

    /** The relay refused this client (not signed in); it has stopped for good. */
    public boolean refused() {
        return refused;
    }

    /** Welcomed by the relay and not closed since. */
    public boolean connected() {
        return welcomed;
    }

    /** Frames dropped so far, here or (when the relay says so) on the server. */
    public int drops() {
        return drops.get();
    }

    public void share(ShareInfo info) {
        String text = info.toJson().toString();
        synchronized (lock) {
            shares.put(info.id, text);
        }
        if (welcomed) send(text);
    }

    public void unshare(UUID id) {
        boolean had;
        synchronized (lock) {
            had = shares.remove(id) != null;
        }
        if (had && welcomed) send(Wire.unshare(id));
    }

    /** The full set of screens to get frames for. */
    public void watch(Collection<UUID> ids) {
        String text = Wire.watch(ids);
        synchronized (lock) {
            watching = text;
        }
        if (welcomed) send(text);
    }

    /** Queues a frame; false when it was dropped (not connected, one already going, over budget). */
    public boolean sendFrame(UUID id, int seq, byte[] jpeg) {
        if (!welcomed || Wire.HEADER + jpeg.length > Wire.MAX_FRAME) return false;
        if (pending.get() > BUDGET || !inFlight.add(id)) {
            drops.incrementAndGet();
            return false;
        }
        ByteBuffer data = Wire.frame(id, Wire.CODEC_JPEG, seq, jpeg);
        int size = data.remaining();
        pending.addAndGet(size);
        Runnable done = () -> {
            pending.addAndGet(0L - size);
            inFlight.remove(id);
        };
        if (enqueue(null, data, done)) return true;
        done.run();
        return false;
    }

    private void send(String text) {
        enqueue(text, null, null);
    }

    private boolean enqueue(String text, ByteBuffer binary, Runnable done) {
        synchronized (lock) {
            WebSocket ws = socket;
            if (ws == null) return false;
            int gen = generation;
            chain = chain.thenCompose(x -> text != null ? ws.sendText(text, true) : ws.sendBinary(binary, true))
                .handle((r, e) -> {
                    if (done != null) done.run();
                    if (e != null) failed(gen, e);
                    return null;
                });
            return true;
        }
    }

    // ---- connection -----------------------------------------------------

    private void connect() {
        if (stopped) return;
        int gen;
        synchronized (lock) {
            gen = ++generation;
        }
        http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10)).buildAsync(uri, new Socket(gen))
            .whenComplete((ws, e) -> {
                if (e != null) {
                    failed(gen, e);
                    return;
                }
                synchronized (lock) {
                    if (gen != generation || stopped) {
                        ws.abort();
                        return;
                    }
                    socket = ws;
                    chain = CompletableFuture.completedFuture(null);
                }
                send(hello);
            });
    }

    private void failed(int gen, Throwable why) {
        WebSocket ws;
        synchronized (lock) {
            if (gen != generation) return;
            ws = detach(socket);
        }
        if (ws != null) ws.abort();
        dropped(welcomed);
    }

    // The fields come in as parameters, never local copies: the obfuscator would read the field
    // again after it is written (HoistCheck).

    /** Under {@code lock}: the socket taken off, its generation over; returns {@code ws}, the one it was. */
    private WebSocket detach(WebSocket ws) {
        socket = null;
        generation++;
        return ws;
    }

    /** {@code was}: whether the relay had welcomed the dropped socket. */
    private void dropped(boolean was) {
        welcomed = false;
        inFlight.clear();
        pending.set(0);
        if (was) listener.onState(false);
        if (stopped) return;
        retry(backoff);
    }

    private void retry(int wait) {
        backoff = Math.min(wait * 2, MAX_BACKOFF_MS);
        timer.schedule(this::connect, wait, TimeUnit.MILLISECONDS);
    }

    private void welcomed(UUID you) {
        backoff = MIN_BACKOFF_MS;
        welcomed = true;
        List<String> again;
        String watch;
        synchronized (lock) {
            again = new ArrayList<>(shares.values());
            watch = watching;
        }
        for (String share : again) send(share);
        if (watch != null) send(watch);
        listener.onWelcome(you);
        listener.onState(true);
    }

    // ---- what comes in --------------------------------------------------

    private void text(String text) {
        JsonObject o;
        try {
            o = JsonParser.parseString(text).getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        String t = string(o, "t");
        if (t.equals("welcome")) welcomed(uuid(string(o, "you")));
        else if (t.equals("screens")) listener.onScreens(list(o.getAsJsonArray("list")));
        else if (t.equals("screen")) {
            ShareInfo s = ShareInfo.fromJson(o);
            if (s != null) listener.onScreen(s);
        } else if (t.equals("gone")) {
            UUID id = uuid(string(o, "id"));
            if (id != null) listener.onGone(id);
        } else if (t.equals("viewers")) {
            UUID id = uuid(string(o, "id"));
            if (id != null && o.has("n")) listener.onViewers(id, o.get("n").getAsInt());
        } else if (t.equals("error")) {
            String msg = string(o, "msg");
            if (msg.toLowerCase(java.util.Locale.ROOT).contains("drop")) drops.incrementAndGet();
            listener.onError(msg);
        } else if (t.contains("drop")) {
            drops.incrementAndGet();
        }
    }

    private static List<ShareInfo> list(JsonArray array) {
        List<ShareInfo> out = new ArrayList<>();
        if (array == null) return out;
        for (JsonElement e : array) {
            if (!e.isJsonObject()) continue;
            ShareInfo s = ShareInfo.fromJson(e.getAsJsonObject());
            if (s != null) out.add(s);
        }
        return out;
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    /** The socket's listener: whole text and binary messages, then the next one asked for. */
    private final class Socket implements WebSocket.Listener {
        private final int gen;
        private final StringBuilder text = new StringBuilder();
        private final java.io.ByteArrayOutputStream binary = new java.io.ByteArrayOutputStream();

        Socket(int gen) {
            this.gen = gen;
        }

        @Override
        public void onOpen(WebSocket ws) {
            ws.request(1);
        }

        @Override
        public CompletableFuture<?> onText(WebSocket ws, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                String whole = text.toString();
                text.setLength(0);
                if (gen == generation) RelayClient.this.text(whole);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletableFuture<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            binary.writeBytes(chunk);
            if (last) {
                Wire.Frame frame = Wire.parseFrame(binary.toByteArray());
                binary.reset();
                if (frame != null && gen == generation) listener.onFrame(frame);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletableFuture<?> onClose(WebSocket ws, int status, String reason) {
            if (status == SIGNED_OUT && gen == generation) {
                // Not signed in: trying again would only be refused again.
                refused = true;
                stopped = true;
                listener.onRefused();
            }
            failed(gen, new IllegalStateException("closed " + status + " " + reason));
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            failed(gen, error);
        }
    }
}
