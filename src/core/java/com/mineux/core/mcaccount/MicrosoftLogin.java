/*
 * OAuth flow ported from idkhack's MicrosoftLogin, written by riverxia -- thanks. Rebuilt on java.net.http + Gson so it
 * carries none of that client's HTTP helper across. The Azure application it signs in with
 * comes from the host (Host#microsoftClientId); with none, the browser sign-in is off.
 */

package com.mineux.core.mcaccount;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.mineux.core.MineUX;
import net.minecraft.util.Util;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The Microsoft device-less OAuth login, start to finish: browser to refresh token
 * ({@link #getRefreshToken}), then refresh token to a live Minecraft session
 * ({@link #login}).
 * <p>
 * The first half stands up a loopback HTTP server on {@link #PORT} and opens the Microsoft
 * consent page pointed back at it. Microsoft redirects to {@code 127.0.0.1:PORT?code=...}
 * once the user picks an account, the handler trades that code for a refresh token, and the
 * server is torn down -- one request is all it is ever there for. Azure only has
 * {@code http://127.0.0.1} registered as the redirect for the Azure application behind
 * {@link #clientId} -- it ignores the port on loopback redirects (RFC 8252), so
 * {@link #PORT} can be anything and doesn't need to be registered.
 * <p>
 * The second half is Mojang's own five-leg exchange -- Live token, XBL, XSTS, Minecraft
 * login, entitlement and profile -- and is pure HTTP with no Minecraft on the thread, which
 * is why {@code Account} runs it off the render thread.
 */
public final class MicrosoftLogin {

    private MicrosoftLogin() {}

    /** What {@code accounts login} says while there is no Microsoft application to sign in with. */
    public static final String WAITING = "OAuth login still waiting on Mojang approval, try importing a session token instead.";
    private static final int PORT = 9675;
    private static final String REDIRECT = "http://127.0.0.1:" + PORT;
    private static final String AUTHORIZE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String SCOPE = "XboxLive.SignIn XboxLive.offline_access";

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    private static volatile HttpServer server;
    private static volatile Consumer<String> callback;

    /** {@link Result#failure} values. Strings, not an enum: see {@link Account#CRACKED}. */
    public static final String REFRESH = "refresh", XBOX = "xbox", OWNS = "owns", PROFILE = "profile";

    /** Everything a successful login yields. A null {@link #accessToken} means it failed. */
    public static final class Result {
        public String accessToken;
        public String refreshToken;
        public String uuid;
        public String username;
        /** On a failure, which leg said no: {@link #REFRESH}, {@link #XBOX}, {@link #OWNS} or {@link #PROFILE}. */
        public String failure;

        public boolean ok() {
            return accessToken != null;
        }
    }

    // ---- browser -> refresh token ------------------------------------

    /**
     * Opens the consent page and calls {@code onToken} with the refresh token once the
     * browser comes back, or with {@code null} if the user cancelled or the exchange failed.
     * The callback runs on the loopback server's own thread, so anything it does with
     * Minecraft has to hop back to the client thread itself.
     *
     * @return whether the browser was asked to open; the page works from {@link #authorizeUrl}
     *     pasted into any browser on this PC either way, since the loopback server is up
     */
    /** The host's Microsoft application; blank while there is none. */
    static String clientId() {
        if (!com.mineux.core.MineUX.ready()) return "";
        String id = com.mineux.core.MineUX.host().microsoftClientId();
        return id == null ? "" : id.trim();
    }

    /** Whether the browser sign-in can run: the host has a Microsoft application. */
    public static boolean available() {
        return !clientId().isEmpty();
    }

    public static boolean getRefreshToken(Consumer<String> onToken) {
        callback = onToken;
        startServer();
        return openBrowser();
    }

    /** The consent page's address: shown and copied when the browser does not open by itself. */
    public static String authorizeUrl() {
        return AUTHORIZE_URL
            + "?client_id=" + clientId()
            + "&response_type=code"
            + "&redirect_uri=" + enc(REDIRECT)
            + "&scope=" + enc(SCOPE)
            + "&prompt=select_account";
    }

    /**
     * Opens the consent page in the default browser. The system can refuse quietly or throw (no
     * default browser, a blocked shell call), which left {@code accounts login} with nowhere to go.
     *
     * @return false when it threw
     */
    public static boolean openBrowser() {
        try {
            Util.getPlatform().openUri(authorizeUrl());
            return true;
        } catch (RuntimeException e) {
            MineUX.LOGGER.warn("Could not open the browser for Microsoft sign-in", e);
            return false;
        }
    }

    private static void startServer() {
        if (server != null) return;

        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            server.createContext("/", MicrosoftLogin::handle);
            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();
        } catch (IOException e) {
            MineUX.LOGGER.warn("Could not start Microsoft login callback server", e);
            stopServer();
        }
    }

    public static void stopServer() {
        HttpServer current = server;
        if (current != null) {
            current.stop(0);
            server = null;
        }
        callback = null;
    }

    private static void handle(HttpExchange exchange) throws IOException {
        Consumer<String> cb = callback;
        String code = param(exchange.getRequestURI().getRawQuery(), "code");

        if (code == null) {
            respond(exchange, "Cannot authenticate. You may close this page.");
            if (cb != null) cb.accept(null);
        } else {
            respond(exchange, "Signed in. You may close this page and return to the game.");
            if (cb != null) cb.accept(exchangeCode(code));
        }

        stopServer();
    }

    /** Trades the one-shot authorization code for a refresh token, or null on failure. */
    private static String exchangeCode(String code) {
        JsonObject res = postForm(TOKEN_URL,
            "client_id=" + clientId()
                + "&code=" + code
                + "&grant_type=authorization_code"
                + "&redirect_uri=" + enc(REDIRECT));

        return res == null ? null : asString(res, "refresh_token");
    }

    // ---- refresh token -> Minecraft session --------------------------

    /**
     * The whole Xbox/Minecraft exchange. Any leg failing returns a {@link Result} whose
     * {@link Result#ok()} is false rather than throwing, so the caller has one thing to check.
     */
    public static Result login(String refreshToken) {
        Result result = new Result();
        result.failure = REFRESH;

        JsonObject token = postForm(TOKEN_URL,
            "client_id=" + clientId()
                + "&refresh_token=" + enc(refreshToken)
                + "&grant_type=refresh_token"
                + "&redirect_uri=" + enc(REDIRECT));
        if (token == null) return result;

        String accessToken = asString(token, "access_token");
        result.refreshToken = asString(token, "refresh_token");
        if (accessToken == null) return result;
        result.failure = XBOX;

        JsonObject xbl = postJson("https://user.auth.xboxlive.com/user/authenticate",
            "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\","
                + "\"RpsTicket\":\"d=" + accessToken + "\"},"
                + "\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}");
        if (xbl == null) return result;

        String xblToken = asString(xbl, "Token");
        String uhs = userHash(xbl);
        if (xblToken == null || uhs == null) return result;

        JsonObject xsts = postJson("https://xsts.auth.xboxlive.com/xsts/authorize",
            "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xblToken + "\"]},"
                + "\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}");
        if (xsts == null) return result;

        String xstsToken = asString(xsts, "Token");
        if (xstsToken == null) return result;

        JsonObject mc = postJson("https://api.minecraftservices.com/authentication/login_with_xbox",
            "{\"identityToken\":\"XBL3.0 x=" + uhs + ";" + xstsToken + "\"}");
        if (mc == null) return result;

        String mcToken = asString(mc, "access_token");
        if (mcToken == null) return result;

        result.failure = OWNS;
        if (!ownsGame(mcToken)) return result;

        return profileInto(result, mcToken);
    }

    /**
     * A Minecraft access token someone already has -- the session token a launcher starts the
     * game with -- checked against the profile endpoint, which answers only while it is live.
     * There is nothing to refresh: it lasts the day Mojang gave it and no longer.
     */
    public static Result session(String mcToken) {
        return profileInto(new Result(), mcToken);
    }

    private static Result profileInto(Result result, String mcToken) {
        result.failure = PROFILE;
        JsonObject profile = getBearer("https://api.minecraftservices.com/minecraft/profile", mcToken);
        if (profile == null) return result;

        String id = asString(profile, "id");
        String name = asString(profile, "name");
        if (id == null || name == null) return result;

        result.accessToken = mcToken;
        result.uuid = id;
        result.username = name;
        result.failure = null;
        return result;
    }

    /** True only if the account owns Minecraft; a demo/no-entitlement token has no profile to log in as. */
    private static boolean ownsGame(String mcToken) {
        JsonObject store = getBearer("https://api.minecraftservices.com/entitlements/mcstore", mcToken);
        if (store == null || !store.has("items") || !store.get("items").isJsonArray()) return false;

        boolean product = false;
        boolean game = false;
        for (var element : store.getAsJsonArray("items")) {
            if (!element.isJsonObject()) continue;
            String name = asString(element.getAsJsonObject(), "name");
            if (Objects.equals("product_minecraft", name)) product = true;
            else if (Objects.equals("game_minecraft", name)) game = true;
        }

        if (!(product && game)) {
            MineUX.LOGGER.warn("Microsoft account has no Minecraft entitlement (product={}, game={})",
                product, game);
        }
        return product && game;
    }

    private static String userHash(JsonObject xbl) {
        try {
            return xbl.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0).getAsJsonObject()
                .get("uhs").getAsString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- tiny HTTP layer ---------------------------------------------

    private static JsonObject postForm(String url, String body) {
        return send(HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build());
    }

    private static JsonObject postJson(String url, String body) {
        return send(HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build());
    }

    private static JsonObject getBearer(String url, String bearer) {
        return send(HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer " + bearer)
            .header("Accept", "application/json")
            .GET()
            .build());
    }

    /** Sends a request and parses a JSON object body, or null on any transport or 4xx/5xx failure. */
    private static JsonObject send(HttpRequest request) {
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                MineUX.LOGGER.warn("Microsoft auth leg {} failed: {} {}",
                    request.uri(), response.statusCode(), response.body());
                return null;
            }
            return GSON.fromJson(response.body(), JsonObject.class);
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            MineUX.LOGGER.warn("Microsoft auth leg {} failed", request.uri(), e);
            return null;
        }
    }

    // ---- small helpers -----------------------------------------------

    private static String asString(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** First value of {@code key} in a raw query string, url-decoded, or null. */
    private static String param(String rawQuery, String key) {
        if (rawQuery == null) return null;

        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;

            String name = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            if (name.equals(key)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void respond(HttpExchange exchange, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
