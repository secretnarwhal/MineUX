package com.mineux.core.rpc;

import com.mineux.core.config.ConfigFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Config file: config/mineux/rpc.json
 *
 * @see Presence
 */
public final class RpcConfig implements ConfigFile.Backed {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/rpc");

    private static final ConfigFile<RpcConfig> FILE =
        new ConfigFile<>(RpcConfig.class, "rpc.json", RpcConfig::new, LOGGER);

    private static RpcConfig instance;

    /** The longest custom line Discord takes. */
    public static final int MAX_LINE = 128;

    /** Show what you are doing on your Discord profile while Discord is open. */
    public boolean enabled = true;

    /**
     * The Discord application the presence is shown as ("Playing ..." is its name), and the one
     * the art is uploaded to. Blank for MineUX's own ({@link Presence#APP_ID}); with both blank
     * the presence stays off.
     */
    public String appId = "";

    /** The server's address on the card; off, it says multiplayer. */
    public boolean showServer = true;

    /** How many modules are on, under the server. */
    public boolean showModules = true;

    /** The mineux.com button under the card, which others see. */
    public boolean button = true;

    /** A line of your own in place of the second one; blank for the client's. */
    public String customLine = "";

    /** The large image's asset key (or an https URL). Blank for MineUX's. */
    public String largeImage = "";

    public static synchronized RpcConfig get() {
        if (instance == null) instance = FILE.load();
        return instance;
    }

    @Override
    public void ensureDefaults() {
        if (appId == null) appId = "";
        appId = appId.trim();
        if (customLine == null) customLine = "";
        if (customLine.length() > MAX_LINE) customLine = customLine.substring(0, MAX_LINE);
        if (largeImage == null) largeImage = "";
        largeImage = largeImage.trim();
    }

    public void save() {
        FILE.save(this);
    }
}
