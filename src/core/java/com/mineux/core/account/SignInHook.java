package com.mineux.core.account;

/**
 * Replaces or extends the greeter's sign-in, for a host's own licence check: set one with
 * {@code Extensions.setSignInHook} before the greeter first shows. An abstract class rather
 * than an interface, so nobody can hand one over as a lambda (an obfuscator a host may run over this code, such as Skidfuscator, breaks
 * those).
 * <p>
 * Every method runs on a worker thread, never the render thread, and may block on the network.
 * To extend rather than replace the MineUX sign-in, call {@link Account#mineuxSignIn} from
 * {@link #signIn} and check what comes back; to leave it alone, return
 * {@link SignInResult#useDefault()}.
 */
public abstract class SignInHook {

    /**
     * The user pressed login with these in the boxes (the name trimmed, neither empty).
     *
     * @return {@link SignInResult#ok} with the MineUX session token, {@link SignInResult#refused}
     *         with what to show, or {@link SignInResult#useDefault()}
     */
    public abstract SignInResult signIn(String user, String password);

    /**
     * A remembered session the server still accepts ({@code GET /me} said yes), at launch:
     * {@link SignInResult#ok} goes straight on without the greeter; a refusal shows the greeter
     * with its message (the user name filled in, the token forgotten). The default lets it
     * through.
     */
    public SignInResult resume(String user, String token) {
        return SignInResult.ok(user, token);
    }

    /** Whether the greeter offers "continue as guest". */
    public boolean allowsGuest() {
        return true;
    }
}
