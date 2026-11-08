package com.mineux.core.account;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * The account calls to the MineUX API: {@code POST /auth/login},
 * {@code GET /me}, {@code POST /auth/logout}. Blocking: call it off the render thread.
 * <p>
 * Nothing here logs a request or a reply: the request carries the password, the reply the
 * token. A failure comes back as {@link Reply#status} 0, without the exception, whose message
 * can carry the address and the query.
 */
public final class MuxClient {

    /** How long a call may take, connecting included. */
    static final Duration TIMEOUT = Duration.ofSeconds(8);

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    private final String base;

    /** @param base {@code https://host/api}, no trailing slash ({@code MuxApi.base()}) */
    public MuxClient(String base) {
        this.base = base;
    }

    /** {@code POST /auth/login {user, password}}: 200 and {@code {token, user}} when it is right. */
    public Reply login(String user, String password) {
        JsonObject body = new JsonObject();
        body.addProperty("user", user);
        body.addProperty("password", password);
        return send(request("/auth/login", null).POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)));
    }

    /** {@code GET /me} as the token's holder: 200 while the session is good, 401 once it is not. */
    public Reply me(String token) {
        return send(request("/me", token).GET());
    }

    /** {@code GET /health}: 200 and {@code {ok:true}} while the server is up. */
    public Reply health() {
        return send(request("/health", null).GET());
    }

    /** {@code POST /auth/logout}: ends the token's session on the server. */
    public Reply logout(String token) {
        return send(request("/auth/logout", token).POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8)));
    }

    private HttpRequest.Builder request(String path, String token) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
            .timeout(TIMEOUT)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "MineUX");
        if (token != null) b.header("Authorization", "Bearer " + token);
        return b;
    }

    private static Reply send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Reply(response.statusCode(), parse(response.body()));
        } catch (IOException | RuntimeException e) {
            return new Reply(0, new JsonObject());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Reply(0, new JsonObject());
        }
    }

    private static JsonObject parse(String text) {
        try {
            JsonElement e = JsonParser.parseString(text == null ? "" : text);
            return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    /**
     * The user name in a reply: {@code user} as a string, or an object's {@code name} /
     * {@code user} / {@code username}; then a top-level {@code username} / {@code name}. Null
     * when there is none.
     */
    public static String userName(JsonObject body) {
        JsonElement user = body.get("user");
        if (user != null && user.isJsonPrimitive()) return user.getAsString();
        if (user != null && user.isJsonObject()) {
            String inner = field(user.getAsJsonObject());
            if (inner != null) return inner;
        }
        return field(body);
    }

    private static String field(JsonObject o) {
        String[] names = {"name", "user", "username"};
        for (String n : names) {
            JsonElement e = o.get(n);
            if (e != null && e.isJsonPrimitive()) return e.getAsString();
        }
        return null;
    }

    /** A string field, or null. */
    public static String string(JsonObject body, String name) {
        JsonElement e = body.get(name);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    /** One reply: the HTTP status (0: the server could not be reached) and its JSON body ({} when it had none). */
    public static final class Reply {
        public final int status;
        public final JsonObject body;

        Reply(int status, JsonObject body) {
            this.status = status;
            this.body = body;
        }

        public boolean ok() {
            return status >= 200 && status < 300;
        }

        public boolean unreachable() {
            return status == 0;
        }
    }
}
