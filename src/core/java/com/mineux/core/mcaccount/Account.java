package com.mineux.core.mcaccount;

import java.util.Objects;

/**
 * One saved login. A row in the switcher, a subclass per {@link #type}.
 * <p>
 * Two things every account can do: say who it is without logging in ({@link #fetchInfo},
 * cheap enough to run when the account is added, so the list has a name to show before you
 * ever click it) and become the running session ({@link #login}, which is the click). Both
 * return whether they worked rather than throwing, because both are called off the render
 * thread by the terminal's {@code accounts} command, and a stack trace there is a silent no-op to the player;
 * the terminal reads the boolean and prints a line.
 * <p>
 * {@link #username} and {@link #uuid} are the last known profile, filled by {@code fetchInfo}
 * or {@code login} and persisted by {@link AccountsConfig} so a Microsoft row still has a name
 * on it after a restart, before its token has been spent. They are a display cache, never the
 * credential -- a cracked account derives both from its name every time, and a Microsoft one
 * gets the authoritative pair back from the token exchange.
 */
public abstract class Account {

    /**
     * What the stored row calls each kind: the on-disk contract, so add, don't rename. Strings
     * and not an enum: an obfuscator a host may run renames an enum's static {@code valueOf}.
     */
    public static final String CRACKED = "CRACKED", MICROSOFT = "MICROSOFT", SESSION = "SESSION";

    protected final String type;

    /** The label the row was created with; for cracked accounts it is also the credential. */
    protected String name;

    /** Last known profile name, or empty until {@link #fetchInfo} has run once. */
    protected String username = "";

    /** Last known profile uuid, dashed form, or empty. */
    protected String uuid = "";

    protected Account(String type, String name) {
        this.type = type;
        this.name = name;
    }

    /**
     * Fills {@link #username}/{@link #uuid} without starting a session. Run when the account
     * is added so the list has something to show. Returns false and leaves the cache alone
     * on any failure.
     */
    public abstract boolean fetchInfo();

    /**
     * Makes this account the running client session. Returns whether the swap happened, so
     * the caller can say so; the actual swap goes through {@link AccountSession}.
     */
    public abstract boolean login();

    public String type() {
        return type;
    }

    public boolean microsoft() {
        return Objects.equals(MICROSOFT, type);
    }

    /** What the switcher calls this kind of row: microsoft, session or offline. */
    public String kind() {
        if (Objects.equals(MICROSOFT, type)) return "microsoft";
        if (Objects.equals(SESSION, type)) return "session";
        return "offline";
    }

    /** The name to show in the list: the fetched profile name once there is one, else the label. */
    public String displayName() {
        return username.isEmpty() ? name : username;
    }

    public String uuid() {
        return uuid;
    }

    /** Flattens this account to the on-disk shape. Subclasses add their own credential fields. */
    public AccountsConfig.Stored toStored() {
        AccountsConfig.Stored stored = new AccountsConfig.Stored();
        stored.type = type;
        stored.name = name;
        stored.username = username;
        stored.uuid = uuid;
        return stored;
    }
}
