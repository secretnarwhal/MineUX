package com.mineux.core.account;

import com.mineux.core.Extensions;
import com.mineux.core.MineUX;
import com.mineux.core.net.MuxApi;

/**
 * The MineUX account on this machine: signing in (through the {@link SignInHook}, when one is
 * set), the remembered session and resuming it at launch, guest mode, and signing out. Keeps
 * {@link MuxApi} in step: signed in there means signed in here.
 * <p>
 * The greeter drives it at launch. Anything else that signs the user out -- the desktop's
 * sign-out -- calls {@link #signOut()}, then {@code Greeter.show()} for the greeter.
 * <p>
 * Every refusal reads the same whatever the server's reason, so the greeter never tells
 * whether a user name exists ({@link #WRONG}); the server's own message is not shown.
 */
public final class Account {

    public static final String WRONG = "wrong user name or password";
    public static final String OFFLINE = "could not reach the MineUX server";
    public static final String BUSY = "too many tries, wait a minute and try again";
    public static final String SERVER = "the MineUX server had a problem, try again";
    public static final String ENDED = "your session has ended, sign in again";
    public static final String FAILED = "sign-in failed unexpectedly";

    /** Where the remembered session stands: ints, not an enum (an obfuscator a host may run over this code, such as Skidfuscator, renames those). */
    public static final int NO_SESSION = 0;
    public static final int RESUMING = 1;
    public static final int RESUMED = 2;
    public static final int REFUSED = 3;
    public static final int UNREACHABLE = 4;

    private static SessionStore store;
    private static volatile int resume = NO_SESSION;
    private static volatile String resumeMessage = "";
    private static boolean resumeStarted;

    private Account() {}

    // ---- the remembered account ------------------------------------------

    /** {@code config/mineux/session.json}, read the first time it is asked for. */
    static synchronized SessionStore store() {
        if (store == null) store = new SessionStore(MineUX.host().configDir().resolve("session.json")).load();
        return store;
    }

    /** For tests: the store to use instead of the host's. */
    static synchronized void useStore(SessionStore s) {
        store = s;
        resume = NO_SESSION;
        resumeMessage = "";
        resumeStarted = false;
    }

    public static synchronized boolean rememberUser() {
        return store().rememberUser;
    }

    public static synchronized boolean rememberMe() {
        return store().rememberMe;
    }

    /** The user name the greeter fills in: the remembered one, or empty. */
    public static synchronized String lastUser() {
        SessionStore s = store();
        return s.rememberUser && s.lastUser != null ? s.lastUser : "";
    }

    /** Sets the remember boxes; one unticked is forgotten now, not at the next sign-in. */
    public static synchronized void setRemember(boolean user, boolean me) {
        SessionStore s = store();
        s.rememberUser = user;
        s.rememberMe = me;
        if (!user) s.lastUser = "";
        if (!me) s.forgetSession();
        s.save();
    }

    // ---- resuming at launch --------------------------------------------------

    /**
     * Starts checking the remembered session with the server ({@code GET /me}), once a launch,
     * on a worker. With no session remembered for this API address it is {@link #NO_SESSION} at
     * once.
     *
     * @return {@link #resumeState()}
     */
    public static synchronized int startResume() {
        if (resumeStarted) return resume;
        resumeStarted = true;
        SessionStore s = store();
        String base = MuxApi.base();
        if (!s.hasSession(base)) return resume;
        String user = s.sessionUser;
        String token = s.token;
        resume = RESUMING;
        Thread worker = new Thread(() -> checkSession(base, user, token), "mineux-resume");
        worker.setDaemon(true);
        worker.start();
        return RESUMING;
    }

    public static int resumeState() {
        return resume;
    }

    /** Why the remembered session did not resume; empty while it has not failed. */
    public static String resumeMessage() {
        return resumeMessage;
    }

    /** The remembered session's user, for "resuming ..."; null for none. */
    public static synchronized String resumeUser() {
        return store().sessionUser;
    }

    private static void checkSession(String base, String user, String token) {
        try {
            MuxClient.Reply reply = new MuxClient(base).me(token);
            if (reply.status == 401 || reply.status == 403) {
                forget();
                resumeMessage = ENDED;
                resume = REFUSED;
                return;
            }
            if (!reply.ok()) {
                resumeMessage = reply.unreachable() ? OFFLINE : SERVER;
                resume = UNREACHABLE;
                return;
            }
            String name = MuxClient.userName(reply.body);
            SignInResult result = hookResume(name == null ? user : name, token);
            if (!result.ok()) {
                forget();
                resumeMessage = result.message();
                resume = REFUSED;
                return;
            }
            MuxApi.signIn(result.user(), result.token());
            resume = RESUMED;
        } catch (RuntimeException e) {
            resumeMessage = FAILED;
            resume = UNREACHABLE;
        }
    }

    private static SignInResult hookResume(String user, String token) {
        SignInHook hook = Extensions.signInHook();
        if (hook == null) return SignInResult.ok(user, token);
        SignInResult r = hook.resume(user, token);
        return r == null || r.wantsDefault() ? SignInResult.ok(user, token) : r;
    }

    private static synchronized void forget() {
        SessionStore s = store();
        s.forgetSession();
        s.save();
    }

    // ---- signing in ---------------------------------------------------------

    /**
     * Signs in with what was typed, through the hook when there is one. Blocking: call it from
     * a worker. Nothing is kept until {@link #adopt}.
     */
    public static SignInResult signIn(String user, String password) {
        try {
            SignInHook hook = Extensions.signInHook();
            if (hook != null) {
                SignInResult r = hook.signIn(user, password);
                if (r != null && !r.wantsDefault()) return r;
            }
            return mineuxSignIn(user, password);
        } catch (RuntimeException e) {
            // Generic on purpose: an exception from the http stack can carry the request.
            return SignInResult.refused(FAILED);
        }
    }

    /** The plain MineUX sign-in, {@code POST /auth/login}. Blocking. Public for a hook that extends it. */
    public static SignInResult mineuxSignIn(String user, String password) {
        MuxClient.Reply reply = new MuxClient(MuxApi.base()).login(user, password);
        if (reply.ok()) {
            String token = MuxClient.string(reply.body, "token");
            if (token == null || token.isEmpty()) return SignInResult.refused(SERVER);
            String name = MuxClient.userName(reply.body);
            return SignInResult.ok(name == null || name.isEmpty() ? user : name, token);
        }
        return SignInResult.refused(refusal(reply.status));
    }

    /** What the greeter says for a failed login's HTTP status: one message for every account refusal. */
    public static String refusal(int status) {
        if (status == 0) return OFFLINE;
        if (status == 429) return BUSY;
        if (status >= 500) return SERVER;
        return WRONG;
    }

    /**
     * A sign-in that succeeded: into {@link MuxApi}, and remembered as the boxes say. On the
     * render thread.
     *
     * @param typed the user name as typed, which is what the greeter fills in next time
     */
    public static synchronized void adopt(SignInResult result, String typed, boolean keepUser, boolean keepSession) {
        if (!result.ok()) throw new IllegalArgumentException("not a sign-in: " + result);
        MuxApi.signIn(result.user(), result.token());
        SessionStore s = store();
        s.rememberUser = keepUser;
        s.rememberMe = keepSession;
        s.lastUser = keepUser ? typed : "";
        if (keepSession) s.remember(result.user(), result.token(), MuxApi.base());
        else s.forgetSession();
        s.save();
    }

    /** Continue as guest: signed out of MineUX for this launch, the remembered user name kept. */
    public static void guest() {
        if (MuxApi.signedIn()) MuxApi.signOut();
    }

    // ---- signing out --------------------------------------------------------

    /**
     * Signs out: out of {@link MuxApi} at once, the remembered session forgotten, and the
     * session ended on the server in the background ({@code POST /auth/logout}). The
     * remembered user name stays, so the greeter offers it again. Safe to call signed out.
     * Call {@code Greeter.show()} after it for the greeter.
     */
    public static void signOut() {
        String token = MuxApi.token();
        String base = MuxApi.base();
        MuxApi.signOut();
        forget();
        if (token == null) return;
        Thread worker = new Thread(() -> new MuxClient(base).logout(token), "mineux-signout");
        worker.setDaemon(true);
        worker.start();
    }
}
