package com.mineux.core.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionStoreTest {

    @TempDir
    Path dir;

    @Test
    void keepsTheSessionAndTheNameBetweenLaunches() {
        Path file = dir.resolve("session.json");
        SessionStore s = new SessionStore(file);
        s.lastUser = "alice";
        s.remember("alice", "tok-123", "https://h/api");
        assertTrue(s.save());

        SessionStore back = new SessionStore(file).load();
        assertEquals("alice", back.lastUser);
        assertEquals("tok-123", back.token);
        assertEquals("alice", back.sessionUser);
        assertTrue(back.hasSession("https://h/api"));
        assertFalse(back.hasSession("https://other/api"), "a token is only good for the server that gave it");
        assertFalse(Files.exists(dir.resolve("session.json.tmp")));
    }

    @Test
    void untickedBoxesKeepNothing() throws Exception {
        Path file = dir.resolve("session.json");
        SessionStore s = new SessionStore(file);
        s.rememberUser = false;
        s.rememberMe = false;
        s.lastUser = "bob";
        s.remember("bob", "secret-token", "https://h/api");
        s.save();
        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertFalse(text.contains("secret-token"), text);
        assertFalse(text.contains("bob"), text);

        SessionStore back = new SessionStore(file).load();
        assertFalse(back.rememberUser);
        assertFalse(back.rememberMe);
        assertNull(back.token);
        assertEquals("", back.lastUser);
    }

    @Test
    void neverWritesAPassword() throws Exception {
        Path file = dir.resolve("session.json");
        SessionStore s = new SessionStore(file);
        s.remember("carol", "t", "b");
        s.save();
        assertFalse(Files.readString(file).toLowerCase(Locale.ROOT).contains("password"));
    }

    @Test
    void aBrokenFileIsSignedOut() throws Exception {
        Path file = dir.resolve("session.json");
        Files.writeString(file, "{not json");
        SessionStore s = new SessionStore(file).load();
        assertNull(s.token);
        assertTrue(s.rememberUser);
        Files.writeString(file, "[1,2]");
        assertNull(new SessionStore(file).load().token);
    }

    @Test
    void theFileIsPrivate() throws Exception {
        Path file = dir.resolve("session.json");
        SessionStore s = new SessionStore(file);
        s.remember("dave", "t", "b");
        assertTrue(s.save());
        // Saved twice: the second replaces the first through the temporary file.
        assertTrue(s.save());
        PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (posix != null) {
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
        if (acl == null) return;
        List<AclEntry> entries = acl.getAcl();
        assertEquals(1, entries.size(), entries.toString());
        assertEquals(Files.getOwner(file), entries.get(0).principal());
    }
}
