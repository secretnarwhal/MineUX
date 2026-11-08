package com.mineux.core.social;

import com.mineux.core.config.ConfigFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Settings for the friends list, in {@code config/mineux/friends.json}.
 * <p>
 * <b>The list itself is not here and the permissions are not here.</b> Both live on the server,
 * because both have to be the same on every machine you sign in from, and because a permission
 * that a local file could grant would be one a text editor could grant. What is kept here is how
 * this client behaves: whether to connect, what to show, and what to tell your friends.
 */
public final class FriendConfig implements ConfigFile.Backed {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/friends");

    private static final ConfigFile<FriendConfig> FILE =
        new ConfigFile<>(FriendConfig.class, "friends.json", FriendConfig::new, LOGGER);

    private static FriendConfig instance;

    /** The whole feature. Off means the stream is never opened and nobody sees you. */
    public boolean enabled = true;

    /**
     * Open the stream as soon as the client is signed in and entitled. On, because a friends
     * list only works if it is listening -- one that had to be switched on before anyone could
     * see you online is one nobody would ever appear on.
     */
    public boolean autoConnect = true;

    /**
     * Tell your friends which server you are on and what you are playing.
     * <p>
     * This is the one thing the friends list sends about you by itself, so it gets its own
     * switch. Off, you still see them and can still message them; they see you as offline.
     */
    public boolean sharePresence = true;

    /** The banner when a friend turns up somewhere. */
    public boolean banner = true;

    /** Friends on the same server as you are never a target for a combat module. */
    public boolean friendly = true;

    /** Friend news and messages go to the game chat as well as the terminal. */
    public boolean chatInGame = true;

    public static synchronized FriendConfig get() {
        if (instance == null) instance = FILE.load();
        return instance;
    }

    @Override
    public void ensureDefaults() {
        // Every field is a primitive with a sane value from its initialiser, so a file that
        // parsed at all is complete. Kept so the shape matches every other config and so a
        // field added later has somewhere to be put right.
    }

    public void save() {
        FILE.save(this);
    }
}
