package com.mineux.core.mcaccount;

import java.util.UUID;

/**
 * A real Microsoft account, held as its OAuth refresh token and nothing else.
 * <p>
 * The refresh token is the only durable secret: everything else -- the Minecraft access
 * token that actually signs you in, the profile name, the uuid -- is minted fresh from it
 * through {@link MicrosoftLogin} on every login and never written to disk. A refresh token
 * outlives an access token by a wide margin and Microsoft rotates it on each use, so
 * {@link #login} takes the new one {@code MicrosoftLogin} hands back and keeps it; the next
 * launch spends that rather than the one the file was written with.
 * <p>
 * {@link #fetchInfo} runs the same exchange but throws the session token away -- it exists so
 * a freshly added account has a name in the list before its first login, and so a launch can
 * confirm the token still works without becoming that account.
 */
public class MicrosoftAccount extends Account {

    private String refreshToken;

    public MicrosoftAccount(String refreshToken) {
        super(MICROSOFT, "");
        this.refreshToken = refreshToken;
    }

    public MicrosoftAccount(String refreshToken, String name) {
        super(MICROSOFT, name);
        this.refreshToken = refreshToken;
    }

    /** The durable credential: what {@code accounts add <name> <token>} takes, and what the list's copy button exports. */
    public String refreshToken() {
        return refreshToken;
    }

    @Override
    public boolean fetchInfo() {
        return check().ok();
    }

    /** {@link #fetchInfo}, keeping the result so a caller can say which leg refused. */
    public MicrosoftLogin.Result check() {
        MicrosoftLogin.Result result = MicrosoftLogin.login(refreshToken);
        if (!result.ok()) return result;

        if (result.refreshToken != null) refreshToken = result.refreshToken;
        username = result.username;
        uuid = result.uuid;
        return result;
    }

    @Override
    public boolean login() {
        MicrosoftLogin.Result result = MicrosoftLogin.login(refreshToken);
        if (!result.ok()) return false;

        if (result.refreshToken != null) refreshToken = result.refreshToken;
        username = result.username;
        uuid = result.uuid;

        return AccountSession.applyMicrosoft(result.username, parseUuid(result.uuid), result.accessToken);
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
        stored.refreshToken = refreshToken;
        return stored;
    }
}
