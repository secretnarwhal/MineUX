package com.mineux.core.account;

/**
 * What a sign-in came to: signed in as a user with a MineUX session token, refused with a
 * message the greeter shows as it is, or -- from a {@link SignInHook} only -- "do the plain
 * MineUX sign-in instead".
 * <p>
 * A refusal's message must be the same whatever the reason the account was refused (no such
 * user, wrong password, suspended): see {@link Account#WRONG}.
 */
public final class SignInResult {

    private static final int OK = 0;
    private static final int REFUSED = 1;
    private static final int DEFAULT = 2;

    private final int kind;
    private final String user;
    private final String token;
    private final String message;

    private SignInResult(int kind, String user, String token, String message) {
        this.kind = kind;
        this.user = user;
        this.token = token;
        this.message = message;
    }

    /** Signed in: {@code token} is the MineUX API session token everything else uses ({@code MuxApi.token()}). */
    public static SignInResult ok(String user, String token) {
        if (user == null || token == null || token.isEmpty()) throw new IllegalArgumentException("a sign-in needs a user and a token");
        return new SignInResult(OK, user, token, "");
    }

    /** Refused: {@code message} is shown under the box, lower case, as ly does. */
    public static SignInResult refused(String message) {
        return new SignInResult(REFUSED, null, null, message == null || message.isEmpty() ? Account.WRONG : message);
    }

    /** From a hook: the plain MineUX sign-in goes ahead as if there were no hook. */
    public static SignInResult useDefault() {
        return new SignInResult(DEFAULT, null, null, "");
    }

    public boolean ok() {
        return kind == OK;
    }

    public boolean wantsDefault() {
        return kind == DEFAULT;
    }

    public String user() {
        return user;
    }

    public String token() {
        return token;
    }

    public String message() {
        return message;
    }

    @Override
    public String toString() {
        // Never the token.
        return kind == OK ? "signed in as " + user : kind == REFUSED ? "refused: " + message : "default";
    }
}
