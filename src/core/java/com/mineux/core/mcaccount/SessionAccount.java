package com.mineux.core.mcaccount;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * A real account held as a Minecraft session token -- the access token a launcher starts the
 * game with ({@code --accessToken}), a JWT that starts {@code eyJ}. Unlike a
 * {@link MicrosoftAccount} there is no refresh token behind it, so it cannot be renewed: it
 * plays until Mojang's expiry in the token (about a day after it was issued) and is dead
 * after that. Any launcher's works, since nothing here is tied to an Azure app.
 * <p>
 * {@link #fetchInfo} and {@link #login} both ask the profile endpoint first, which answers
 * only while the token is live, so an expired row fails cleanly instead of joining a server
 * with a token the session server will refuse.
 */
public class SessionAccount extends Account {

    private static final Gson GSON = new Gson();

    private final String accessToken;

    public SessionAccount(String accessToken, String name) {
        super(SESSION, name);
        this.accessToken = accessToken;
    }

    /** Whether {@code token} is shaped like a Minecraft session token (a JWT) rather than a refresh token. */
    public static boolean looksLikeSession(String token) {
        return token.startsWith("eyJ") && token.split("\\.", -1).length == 3;
    }

    public String accessToken() {
        return accessToken;
    }

    /**
     * When the token stops working, in epoch milliseconds, from its own {@code exp} claim; 0 if
     * it has none or cannot be read. Read without verifying the signature -- it only decides
     * what the list says, and the profile endpoint is what decides whether it works.
     */
    public long expiresAt() {
        try {
            String[] parts = accessToken.split("\\.");
            String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonObject claims = GSON.fromJson(json, JsonObject.class);
            return claims.has("exp") ? claims.get("exp").getAsLong() * 1000L : 0L;
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    @Override
    public boolean fetchInfo() {
        MicrosoftLogin.Result result = MicrosoftLogin.session(accessToken);
        if (!result.ok()) return false;
        username = result.username;
        uuid = result.uuid;
        return true;
    }

    @Override
    public boolean login() {
        if (!fetchInfo()) return false;
        return AccountSession.applyMicrosoft(username, parseUuid(uuid), accessToken);
    }

    /** Undashed uuids come back from the profile endpoint; {@link UUID#fromString} needs the dashes. */
    private static UUID parseUuid(String uuid) {
        if (uuid.contains("-")) return UUID.fromString(uuid);
        return UUID.fromString(uuid.replaceFirst(
            "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
            "$1-$2-$3-$4-$5"));
    }

    @Override
    public AccountsConfig.Stored toStored() {
        AccountsConfig.Stored stored = super.toStored();
        stored.accessToken = accessToken;
        return stored;
    }
}
