package com.mineux.core.mcaccount;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * An offline account: a name and the uuid the vanilla server derives from it.
 * <p>
 * The uuid is not random and not stored as a credential -- it is
 * {@code UUID.nameUUIDFromBytes("OfflinePlayer:" + name)}, which is the exact rule an offline
 * server uses to assign one, so the account this logs in as is the same player that server
 * would have made for the name anyway. That is the whole of the "credential": there is
 * nothing to authenticate, so {@link #fetchInfo} and {@link #login} cannot fail.
 */
public class CrackedAccount extends Account {

    public CrackedAccount(String name) {
        super(CRACKED, name);
    }

    private static String offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString();
    }

    @Override
    public boolean fetchInfo() {
        username = name;
        uuid = offlineUuid(name);
        return true;
    }

    @Override
    public boolean login() {
        if (uuid.isEmpty()) fetchInfo();
        return AccountSession.applyOffline(name, UUID.fromString(uuid));
    }
}
