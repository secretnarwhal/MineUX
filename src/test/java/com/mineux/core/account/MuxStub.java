package com.mineux.core.account;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tiny stand-in for the MineUX API's account calls, on 127.0.0.1: {@code POST /auth/login},
 * {@code GET /me}, {@code POST /auth/logout}, {@code GET /health}. JDK only (no Gson), so it
 * also runs on its own for a dev client:
 * {@code java src/test/java/com/mineux/core/account/MuxStub.java 8090} and
 * {@code ./gradlew runClient -Dmineux.api=http://127.0.0.1:8090/api}... (see docs).
 * <p>
 * Users: {@code mux-test-greeter} / {@code correct horse}. It answers a wrong password with 401
 * and an unknown user with 404 and a different message, so a test can check the client says
 * the same for both. {@code slow} gets 429, {@code boom} 500.
 */
public final class MuxStub {

    private static final Pattern FIELD = Pattern.compile("\"(user|password)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    public final Map<String, String> users = new ConcurrentHashMap<>();
    /** token -> user */
    public final Map<String, String> sessions = new ConcurrentHashMap<>();
    public final List<String> loggedOut = new CopyOnWriteArrayList<>();
    public final List<String> paths = new CopyOnWriteArrayList<>();
    private final HttpServer server;

    public MuxStub(int port) throws IOException {
        users.put("mux-test-greeter", "correct horse");
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/api/", this::handle);
        server.start();
    }

    public String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
    }

    public void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath().substring("/api".length());
        paths.add(ex.getRequestMethod() + " " + path);
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
        if (path.equals("/health")) {
            reply(ex, 200, "{\"ok\":true}");
        } else if (path.equals("/auth/login") && ex.getRequestMethod().equals("POST")) {
            login(ex, body);
        } else if (path.equals("/me")) {
            String user = token == null ? null : sessions.get(token);
            if (user == null) reply(ex, 401, "{\"error\":\"not signed in\"}");
            else reply(ex, 200, "{\"user\":{\"id\":\"1\",\"username\":\"" + user + "\"},\"friends\":0}");
        } else if (path.equals("/auth/logout") && ex.getRequestMethod().equals("POST")) {
            if (token != null) {
                sessions.remove(token);
                loggedOut.add(token);
            }
            reply(ex, 200, "{\"ok\":true}");
        } else {
            reply(ex, 404, "{\"error\":\"no such path\"}");
        }
    }

    private void login(HttpExchange ex, String body) throws IOException {
        String user = null;
        String password = null;
        Matcher m = FIELD.matcher(body);
        while (m.find()) {
            if (m.group(1).equals("user")) user = m.group(2);
            else password = m.group(2);
        }
        if ("slow".equals(user)) {
            reply(ex, 429, "{\"error\":\"rate limited\"}");
            return;
        }
        if ("boom".equals(user)) {
            reply(ex, 500, "{\"error\":\"oops\"}");
            return;
        }
        String want = user == null ? null : users.get(user);
        if (want == null) {
            reply(ex, 404, "{\"error\":\"no account called " + user + "\"}");
            return;
        }
        if (!want.equals(password)) {
            reply(ex, 401, "{\"error\":\"wrong password\"}");
            return;
        }
        String token = UUID.randomUUID().toString();
        sessions.put(token, user);
        reply(ex, 200, "{\"token\":\"" + token + "\",\"expiresAt\":\"2027-01-01T00:00:00Z\",\"user\":{\"id\":\"1\",\"username\":\"" + user + "\"}}");
    }

    private static void reply(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** {@code java MuxStub.java [port]}: serves until killed. */
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8090;
        MuxStub stub = new MuxStub(port);
        System.out.println("MineUX stub API at " + stub.base() + " (user mux-test-greeter / correct horse)");
    }
}
