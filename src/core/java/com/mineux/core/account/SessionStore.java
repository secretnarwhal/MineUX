package com.mineux.core.account;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The remembered account, {@code config/mineux/session.json}: the two remember boxes, the
 * user name the greeter fills in, and -- with "stay signed in" ticked -- the session token and
 * the server it belongs to. Never the password.
 * <p>
 * The file holds a bearer token, so it is written private to the user (owner read and write
 * only, see {@link Private}) and replaced atomically through a temporary file next to it. It is
 * read and written as a plain {@code JsonObject}, not mapped onto fields by reflection, so
 * an obfuscated build reads the same file.
 */
public final class SessionStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("MineUX/account");

    private final Path file;

    /** Tick "remember user name": the greeter fills it in next launch. */
    public boolean rememberUser = true;
    /** Tick "stay signed in": the token is kept, and the next launch skips the greeter while it is good. */
    public boolean rememberMe = true;
    /** First launch, is login screen shown first time. */
    public boolean firstLaunchDone = false;
    /** The user name the greeter fills in; empty for none. */
    public String lastUser = "";
    /** The remembered session's user, token and API base; null token for none. */
    public String sessionUser;
    public String token;
    public String base;

    public SessionStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** Whether a remembered session for {@code apiBase} is there to resume. */
    public boolean hasSession(String apiBase) {
        return token != null && !token.isEmpty() && apiBase.equals(base);
    }

    public void remember(String user, String sessionToken, String apiBase) {
        sessionUser = user;
        token = sessionToken;
        base = apiBase;
    }

    public void forgetSession() {
        sessionUser = null;
        token = null;
        base = null;
    }

    /** Reads the file; one that is missing or broken leaves the defaults. */
    public SessionStore load() {
        if (!Files.isRegularFile(file)) return this;
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) return this;
            JsonObject o = root.getAsJsonObject();

            rememberUser = bool(o, "rememberUser", rememberUser);
            rememberMe = bool(o, "rememberMe", rememberMe);
            firstLaunchDone = bool(o, "firstLaunchDone", firstLaunchDone);
            String last = MuxClient.string(o, "lastUser");
            lastUser = last == null ? "" : last;
            sessionUser = MuxClient.string(o, "sessionUser");
            token = MuxClient.string(o, "token");
            base = MuxClient.string(o, "base");
            // An older or hand-made file may be readable by others: tighten it now.
            Private.restrict(file);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read {}; starting signed out", file);
        }
        return this;
    }

    /** Writes the file, private to the user. False when it could not be written. */
    public boolean save() {
        JsonObject o = new JsonObject();
        o.addProperty("v", 1);
        o.addProperty("rememberUser", rememberUser);
        o.addProperty("rememberMe", rememberMe);
        o.addProperty("firstLaunchDone", firstLaunchDone);
        o.addProperty("lastUser", rememberUser && lastUser != null ? lastUser : "");
        if (rememberMe && token != null) {
            o.addProperty("sessionUser", sessionUser);
            o.addProperty("token", token);
            o.addProperty("base", base);
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Private.create(temp);
            Files.writeString(temp, o.toString(), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            Private.restrict(file);
            return true;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not write {}", file);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // Nothing more to do.
            }
            return false;
        }
    }

    private static boolean bool(JsonObject o, String name, boolean fallback) {
        JsonElement e = o.get(name);
        if (e == null || !e.isJsonPrimitive()) return fallback;
        try {
            return e.getAsBoolean();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }
}
