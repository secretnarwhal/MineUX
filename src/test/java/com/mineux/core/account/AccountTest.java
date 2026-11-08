package com.mineux.core.account;

import com.mineux.core.Extensions;
import com.mineux.core.net.MuxApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The account client against {@link MuxStub}, a local HTTP stand-in for the MineUX API. */
class AccountTest {

    private static final String USER = "mux-test-greeter";
    private static final String PASSWORD = "correct horse";

    @TempDir
    Path dir;
    private MuxStub stub;

    /**
     * One hook for the whole run (a second set is refused): it handles only its own users and
     * hands everyone else to the plain sign-in, so the other tests see no hook at all.
     */
    @BeforeAll
    static void hook() {
        if (Extensions.signInHook() != null) return;
        Extensions.setSignInHook(new SignInHook() {
            @Override
            public SignInResult signIn(String user, String password) {
                if (user.equals("licensed")) {
                    SignInResult mux = Account.mineuxSignIn(USER, password);
                    return mux.ok() ? SignInResult.ok("licensed", mux.token()) : mux;
                }
                if (user.equals("unlicensed")) return SignInResult.refused("this account has no licence");
                return SignInResult.useDefault();
            }
        });
    }

    @BeforeEach
    void start() throws IOException {
        stub = new MuxStub(0);
        MuxApi.setBase(stub.base());
        MuxApi.signOut();
        Account.useStore(new SessionStore(dir.resolve("session.json")));
    }

    @AfterEach
    void stop() {
        stub.stop();
        MuxApi.signOut();
    }

    @Test
    void theRightPasswordSignsIn() {
        SignInResult r = Account.signIn(USER, PASSWORD);
        assertTrue(r.ok(), r.toString());
        assertEquals(USER, r.user());
        assertTrue(stub.sessions.containsKey(r.token()));
        assertTrue(stub.paths.contains("POST /auth/login"));
        assertFalse(r.toString().contains(r.token()), "the token never shows in a log line");
    }

    @Test
    void everyAccountRefusalReadsTheSame() {
        SignInResult wrong = Account.signIn(USER, "nope");
        SignInResult unknown = Account.signIn("nobody-here", "nope");
        assertFalse(wrong.ok());
        assertFalse(unknown.ok());
        assertEquals(Account.WRONG, wrong.message());
        assertEquals(wrong.message(), unknown.message(), "a refusal never tells whether the name exists");
    }

    @Test
    void rateLimitsServerErrorsAndNoServerSaySo() throws IOException {
        assertEquals(Account.BUSY, Account.signIn("slow", "x").message());
        assertEquals(Account.SERVER, Account.signIn("boom", "x").message());
        MuxApi.setBase("http://127.0.0.1:" + freePort() + "/api");
        assertEquals(Account.OFFLINE, Account.signIn(USER, PASSWORD).message());
    }

    @Test
    void adoptingSignsInAndRemembersTheSession() throws Exception {
        SignInResult r = Account.signIn(USER, PASSWORD);
        Account.adopt(r, USER, true, true);
        assertTrue(MuxApi.signedIn());
        assertEquals(USER, MuxApi.user());
        assertEquals(r.token(), MuxApi.token());
        SessionStore back = new SessionStore(dir.resolve("session.json")).load();
        assertEquals(r.token(), back.token);
        assertEquals(stub.base(), back.base);
        assertEquals(USER, back.lastUser);
    }

    @Test
    void notStayingSignedInKeepsNoToken() throws Exception {
        SignInResult r = Account.signIn(USER, PASSWORD);
        Account.adopt(r, USER, true, false);
        assertTrue(MuxApi.signedIn());
        assertFalse(Files.readString(dir.resolve("session.json")).contains(r.token()));
    }

    @Test
    void aRememberedSessionResumesAtLaunch() throws Exception {
        SignInResult r = Account.signIn(USER, PASSWORD);
        Account.adopt(r, USER, true, true);
        MuxApi.signOut();

        // The next launch: a fresh store read from the file.
        Account.useStore(new SessionStore(dir.resolve("session.json")).load());
        assertEquals(Account.RESUMING, Account.startResume());
        assertEquals(Account.RESUMED, awaitResume());
        assertTrue(MuxApi.signedIn());
        assertEquals(USER, MuxApi.user());
        assertEquals(r.token(), MuxApi.token());
        assertTrue(stub.paths.contains("GET /me"));
    }

    @Test
    void anEndedSessionIsForgotten() throws Exception {
        SessionStore s = new SessionStore(dir.resolve("session.json"));
        s.remember(USER, "revoked-token", stub.base());
        s.lastUser = USER;
        s.save();
        Account.useStore(new SessionStore(dir.resolve("session.json")).load());
        Account.startResume();
        assertEquals(Account.REFUSED, awaitResume());
        assertEquals(Account.ENDED, Account.resumeMessage());
        assertFalse(MuxApi.signedIn());
        SessionStore back = new SessionStore(dir.resolve("session.json")).load();
        assertNull(back.token);
        assertEquals(USER, back.lastUser, "the name stays for the greeter to fill in");
    }

    @Test
    void anUnreachableServerKeepsTheSessionForNextTime() throws Exception {
        String dead = "http://127.0.0.1:" + freePort() + "/api";
        SessionStore s = new SessionStore(dir.resolve("session.json"));
        s.remember(USER, "some-token", dead);
        s.save();
        MuxApi.setBase(dead);
        Account.useStore(new SessionStore(dir.resolve("session.json")).load());
        Account.startResume();
        assertEquals(Account.UNREACHABLE, awaitResume());
        assertEquals(Account.OFFLINE, Account.resumeMessage());
        assertEquals("some-token", new SessionStore(dir.resolve("session.json")).load().token);
    }

    @Test
    void aSessionForAnotherServerIsNotSent() {
        SessionStore s = new SessionStore(dir.resolve("session.json"));
        s.remember(USER, "elsewhere", "https://another.example/api");
        s.save();
        Account.useStore(new SessionStore(dir.resolve("session.json")).load());
        assertEquals(Account.NO_SESSION, Account.startResume());
        assertTrue(stub.paths.isEmpty());
    }

    @Test
    void signingOutEndsTheSessionEverywhere() throws Exception {
        SignInResult r = Account.signIn(USER, PASSWORD);
        Account.adopt(r, USER, true, true);
        Account.signOut();
        assertFalse(MuxApi.signedIn());
        assertNull(new SessionStore(dir.resolve("session.json")).load().token);
        long until = System.currentTimeMillis() + 5000;
        while (!stub.loggedOut.contains(r.token()) && System.currentTimeMillis() < until) Thread.sleep(20);
        assertTrue(stub.loggedOut.contains(r.token()), "POST /auth/logout with the token");
        assertFalse(stub.sessions.containsKey(r.token()));
    }

    @Test
    void guestStaysSignedOut() {
        Account.guest();
        assertFalse(MuxApi.signedIn());
        assertNull(MuxApi.token());
    }

    @Test
    void theHookExtendsOrReplacesTheSignIn() {
        SignInResult licensed = Account.signIn("licensed", PASSWORD);
        assertTrue(licensed.ok());
        assertEquals("licensed", licensed.user());
        assertEquals("this account has no licence", Account.signIn("unlicensed", PASSWORD).message());
        assertEquals(Account.WRONG, Account.signIn("licensed", "bad").message());
    }

    @Test
    void theAddressComesFromThePropertyThenTheDotfile() {
        String old = System.getProperty(MuxApi.BASE_PROPERTY);
        try {
            System.clearProperty(MuxApi.BASE_PROPERTY);
            assertEquals(MuxApi.DEFAULT_BASE, MuxApi.configure(""));
            assertEquals("http://dotfile.test/api", MuxApi.configure("http://dotfile.test/api/"));
            System.setProperty(MuxApi.BASE_PROPERTY, "http://property.test/api");
            assertEquals("http://property.test/api", MuxApi.configure("http://dotfile.test/api"));
            assertEquals("http://property.test", MuxApi.site());
            assertEquals("ws://property.test/api", MuxApi.wsBase());
        } finally {
            if (old == null) System.clearProperty(MuxApi.BASE_PROPERTY);
            else System.setProperty(MuxApi.BASE_PROPERTY, old);
        }
    }

    private static int awaitResume() throws InterruptedException {
        long until = System.currentTimeMillis() + 10_000;
        while (Account.resumeState() == Account.RESUMING && System.currentTimeMillis() < until) Thread.sleep(10);
        return Account.resumeState();
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
