package com.mineux.core.account;

import com.mineux.core.net.MuxApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The account client against the real MineUX API ({@link MuxApi#DEFAULT_BASE}, or
 * {@code MINEUX_LIVE_BASE}). Off unless {@code MINEUX_LIVE=1}, so a plain build never touches
 * the network:
 * <pre>MINEUX_LIVE=1 ./gradlew test --tests '*LiveApiTest*'</pre>
 * The refusal and resume checks need no account. The full sign-in / resume / sign-out round
 * runs only with an account given as {@code MINEUX_LIVE_USER} and {@code MINEUX_LIVE_PASSWORD}.
 */
@EnabledIfEnvironmentVariable(named = "MINEUX_LIVE", matches = "1|true")
class LiveApiTest {

    @TempDir
    Path dir;

    @BeforeEach
    void start() {
        String base = System.getenv("MINEUX_LIVE_BASE");
        MuxApi.setBase(base == null || base.isBlank() ? MuxApi.DEFAULT_BASE : base);
        MuxApi.signOut();
        Account.useStore(new SessionStore(dir.resolve("session.json")));
    }

    @AfterEach
    void stop() {
        MuxApi.signOut();
    }

    @Test
    void theServerIsUp() {
        MuxClient.Reply reply = new MuxClient(MuxApi.base()).health();
        assertEquals(200, reply.status, "GET /health at " + MuxApi.base());
    }

    @Test
    void anUnknownAccountGetsTheUniformRefusal() {
        SignInResult r = Account.signIn("mux-no-such-user-e-" + System.nanoTime(), "not-a-real-password");
        assertFalse(r.ok());
        assertEquals(Account.WRONG, r.message());
    }

    @Test
    void aBogusRememberedTokenEndsTheSession() throws Exception {
        SessionStore s = new SessionStore(dir.resolve("session.json"));
        s.remember("someone", "not-a-real-token", MuxApi.base());
        s.save();
        Account.useStore(new SessionStore(dir.resolve("session.json")).load());
        Account.startResume();
        long until = System.currentTimeMillis() + 15_000;
        while (Account.resumeState() == Account.RESUMING && System.currentTimeMillis() < until) Thread.sleep(20);
        assertEquals(Account.REFUSED, Account.resumeState());
        assertEquals(Account.ENDED, Account.resumeMessage());
        assertNull(new SessionStore(dir.resolve("session.json")).load().token);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MINEUX_LIVE_USER", matches = ".+")
    void aRealAccountSignsInResumesAndSignsOut() throws Exception {
        String user = System.getenv("MINEUX_LIVE_USER");
        String password = System.getenv("MINEUX_LIVE_PASSWORD");
        SignInResult r = Account.signIn(user, password == null ? "" : password);
        assertTrue(r.ok(), r.toString());
        Account.adopt(r, user, true, true);
        assertTrue(MuxApi.signedIn());
        MuxApi.signOut();

        Account.useStore(new SessionStore(dir.resolve("session.json")).load());
        Account.startResume();
        long until = System.currentTimeMillis() + 15_000;
        while (Account.resumeState() == Account.RESUMING && System.currentTimeMillis() < until) Thread.sleep(20);
        assertEquals(Account.RESUMED, Account.resumeState(), Account.resumeMessage());
        assertEquals(r.token(), MuxApi.token());

        String token = MuxApi.token();
        Account.signOut();
        assertFalse(MuxApi.signedIn());
        // The logout runs in the background; once it has, the token is dead.
        until = System.currentTimeMillis() + 15_000;
        int status = 200;
        while (status == 200 && System.currentTimeMillis() < until) {
            Thread.sleep(200);
            status = new MuxClient(MuxApi.base()).me(token).status;
        }
        assertEquals(401, status, "the token still works after sign-out");
    }
}
