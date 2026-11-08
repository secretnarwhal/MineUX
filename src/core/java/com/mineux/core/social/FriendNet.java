package com.mineux.core.social;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.mineux.core.net.MuxApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The transport. One long-lived server-sent-events stream down, ordinary POSTs up.
 * <p>
 * <b>Server-sent events, not a websocket.</b> The service sits behind the same nginx
 * {@code /api/} block as the auth API, which sets {@code proxy_buffering off} and a thirty
 * second read timeout but does not pass the websocket upgrade headers. SSE needs neither a
 * handshake nor an nginx change, the JDK's own {@code HttpClient} speaks it with no dependency
 * at all, and the server pings inside the timeout to hold the connection open. The cost is that
 * this client cannot push on the same connection, which is no cost: the only automatic traffic
 * is a presence update when the server you are on changes.
 * <p>
 * <b>One daemon thread, and it owns the socket.</b> It reads frames, parses them, and writes
 * into {@link FriendList}. It never touches the game: anything that has to reach Minecraft is
 * handed to {@link #handoff}, which {@link FriendTick} drains on the tick thread.
 * <p>
 * <b>Failures are values.</b> Nothing here throws at a caller. A call that fails answers null
 * and leaves a message on {@link FriendList#error()}; the stream dying is a reconnect. The callers
 * are a UI that needs something to print, not a stack trace.
 */
public final class FriendNet {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/friends");

    private FriendNet() {}

    private static final Gson GSON = new Gson();

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    /** Per request, not just per connect: a server that accepts and then never answers must not hang a call. */
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(15);

    private static final long BACKOFF_MIN_MS = 3_000;
    private static final long BACKOFF_MAX_MS = 60_000;

    private static volatile Thread worker;
    private static volatile boolean running;
    /** The open stream's body, closed by {@link #stop} so a blocked read ends at once. */
    private static volatile InputStream open;

    private static final List<Runnable> HANDOFF = new ArrayList<>();

    // ---- lifecycle -------------------------------------------------------

    /** Opens the stream if it is wanted and not already open. Safe to call as often as liked. */
    public static synchronized void start() {
        if (running) return;
        if (!FriendConfig.get().enabled) return;
        if (!MuxApi.signedIn()) return;

        running = true;
        Thread thread = new Thread(FriendNet::loop, "mineux-friends");
        thread.setDaemon(true);
        worker = thread;
        thread.start();
    }

    /** Closes the stream. You go offline for your friends as soon as the server notices. */
    public static synchronized void stop() {
        running = false;
        dropWorker(worker);
        dropBody(open);
        FriendList.onDisconnected("");
    }

    // The fields come in as parameters, never local copies: the obfuscator would read the field
    // again after it is cleared (HoistCheck).
    private static void dropWorker(Thread thread) {
        worker = null;
        if (thread != null) thread.interrupt();
    }

    private static void dropBody(InputStream body) {
        open = null;
        if (body != null) closeQuietly(body);
    }

    public static boolean started() {
        return running;
    }

    /** Whether the calling thread is the stream's current one: a stopped stream's thread ends even if another started since. */
    private static boolean mine() {
        return running && worker == Thread.currentThread();
    }

    // ---- the stream ------------------------------------------------------

    private static void loop() {
        long backoff = BACKOFF_MIN_MS;

        while (mine()) {
            boolean connected = false;
            try {
                connected = stream();
            } catch (IOException e) {
                // A dropped connection is the normal end of a stream, not news.
                LOGGER.debug("friends stream ended", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                LOGGER.warn("friends stream failed", e);
            }

            if (!mine()) return;
            FriendList.onDisconnected(FriendList.error().isEmpty() ? "disconnected" : FriendList.error());

            // A stream that ran and then dropped is a different thing from one that never
            // opened: only the second should be backed off.
            backoff = connected ? BACKOFF_MIN_MS : Math.min(BACKOFF_MAX_MS, backoff * 2);
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * One connection, from the request to the socket closing.
     *
     * @return whether the stream was ever established, so the caller can tell a server that hung
     *         up from one that refused
     */
    private static boolean stream() throws IOException, InterruptedException {
        String token = MuxApi.token();
        if (token == null || token.isEmpty()) {
            FriendList.onDisconnected("not signed in");
            return false;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url(FriendOps.STREAM)))
            .header("Accept", "text/event-stream")
            .header("Authorization", "Bearer " + token)
            // No request timeout: the point of this request is that it does not finish.
            .GET()
            .build();

        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            FriendList.onDisconnected(describe(response.statusCode()));
            closeQuietly(response.body());
            return false;
        }

        open = response.body();
        if (!mine()) {
            closeQuietly(response.body());
            return false;
        }
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {

            String event = "";
            StringBuilder data = new StringBuilder();

            String line;
            while (mine() && (line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    // A blank line ends a frame.
                    if (!event.isEmpty() && data.length() > 0) dispatch(event, data.toString());
                    event = "";
                    data.setLength(0);
                } else if (line.charAt(0) == ':') {
                    // A comment: the server's ping. Proof the connection is alive, nothing more.
                    continue;
                } else if (line.startsWith("event:")) {
                    event = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    if (data.length() > 0) data.append('\n');
                    data.append(line.substring(5).trim());
                }
            }
        }
        return true;
    }

    private static void dispatch(String event, String data) {
        JsonObject json;
        try {
            json = GSON.fromJson(data, JsonObject.class);
        } catch (RuntimeException e) {
            LOGGER.debug("friends frame was not json: {}", event);
            return;
        }
        if (json == null) return;

        try {
            FriendEvents.handle(event, json);
        } catch (RuntimeException e) {
            // One bad frame must not take the stream down with it.
            LOGGER.warn("friends frame {} failed", event, e);
        }
    }

    // ---- calling ---------------------------------------------------------

    /**
     * One POST to the friends service, on the calling thread.
     * <p>
     * <b>Never call this from the render or tick thread.</b> It is a round trip to the VPS.
     *
     * @return the parsed reply, or null -- with the reason left on {@link FriendList#callError()}.
     *         A refused call leaves the stream's state alone: it was never a disconnect
     */
    static JsonObject post(String path, JsonObject body) {
        return call(path, body, "POST");
    }

    /** A GET. Same contract as {@link #post}. */
    static JsonObject get(String path) {
        return call(path, null, "GET");
    }

    private static JsonObject call(String path, JsonObject body, String method) {
        String token = MuxApi.token();
        if (token == null || token.isEmpty()) {
            FriendList.onCallFailed("not signed in");
            return null;
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url(path)))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + token)
            .timeout(CALL_TIMEOUT);

        if (method != null && method.equals("GET")) {
            builder.GET();
        } else {
            String payload = body == null ? "{}" : body.toString();
            builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        }

        try {
            FriendList.onCallFailed("");
            HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            JsonObject json = parse(response.body());

            if (response.statusCode() / 100 == 2) return json == null ? new JsonObject() : json;

            String message = json == null ? "" : string(json, "message");
            FriendList.onCallFailed(message.isEmpty()
                ? "the server refused that (" + response.statusCode() + ")" : message);
            return null;
        } catch (IOException e) {
            LOGGER.debug("friends call to {} failed", path, e);
            FriendList.onCallFailed("could not reach the server");
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * A GET that leaves the list and its error alone, failing quietly to null: for asking about
     * somebody who is not on the list ({@link House}), where a refusal is nobody's business.
     * Never from the render or tick thread.
     */
    static JsonObject peek(String path) {
        String token = MuxApi.token();
        if (token == null || token.isEmpty()) return null;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + token)
            .timeout(CALL_TIMEOUT)
            .GET()
            .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) return null;
            return parse(response.body());
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * A call to {@code apiBase + path} -- outside {@code /friends} -- that leaves the friends
     * list and its error alone: for the bell ({@link Notices}). Null on any failure, with the
     * server's message (or why it could not be asked) in {@code why[0]}. Never from the render
     * or tick thread.
     */
    static JsonObject quiet(String path, JsonObject body, String[] why) {
        String token = MuxApi.token();
        if (token == null || token.isEmpty()) {
            why[0] = "not signed in";
            return null;
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(MuxApi.base() + path))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + token)
            .timeout(CALL_TIMEOUT);
        if (body == null) {
            builder.GET();
        } else {
            builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        }
        try {
            HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            JsonObject json = parse(response.body());
            if (response.statusCode() / 100 == 2) return json == null ? new JsonObject() : json;
            String message = json == null ? "" : string(json, "message");
            why[0] = message.isEmpty() ? "the server refused that (" + response.statusCode() + ")" : message;
            return null;
        } catch (IOException e) {
            why[0] = "could not reach the server";
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            why[0] = "interrupted";
            return null;
        }
    }

    /**
     * Runs a call off the game threads.
     * <p>
     * A thread per call rather than a pool: these are one a keystroke at most, they are mostly
     * waiting on a socket, and a pool is a thing that has to be shut down.
     */
    static void async(Runnable call) {
        Thread thread = new Thread(call, "mineux-friends-call");
        thread.setDaemon(true);
        thread.start();
    }

    /** Queues work for the tick thread. Used for anything that touches the game. */
    static void handoff(Runnable work) {
        synchronized (HANDOFF) {
            // A client that is not ticking -- sitting on a loading screen -- must not grow this
            // without bound. Dropping the oldest is right: the newest instruction is the one
            // that matters.
            if (HANDOFF.size() > 32) HANDOFF.remove(0);
            HANDOFF.add(work);
        }
    }

    /** Called once a tick. Runs whatever the stream thread left. */
    static void drain(Consumer<Runnable> runner) {
        List<Runnable> work;
        synchronized (HANDOFF) {
            if (HANDOFF.isEmpty()) return;
            work = new ArrayList<>(HANDOFF);
            HANDOFF.clear();
        }
        for (Runnable each : work) runner.accept(each);
    }

    // ---- json ------------------------------------------------------------

    private static String url(String path) {
        return MuxApi.base() + FriendOps.BASE + path;
    }

    private static String describe(int status) {
        if (status == 401) return "signed out";
        if (status == 403) return "a subscription is needed for friends";
        if (status == 503) return "the server is busy";
        return "the server refused the stream (" + status + ")";
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // Closing a body we are discarding; nothing to do about a failure.
        }
    }

    static JsonObject parse(String body) {
        try {
            return GSON.fromJson(body, JsonObject.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String string(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive()
            ? object.get(key).getAsString()
            : "";
    }

    static boolean bool(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive()
            && object.get(key).getAsBoolean();
    }

    static int integer(JsonObject object, String key) {
        try {
            return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                ? object.get(key).getAsInt()
                : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    static JsonArray array(JsonObject object, String key) {
        if (object == null || !object.has(key)) return new JsonArray();
        JsonElement element = object.get(key);
        return element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }
}
