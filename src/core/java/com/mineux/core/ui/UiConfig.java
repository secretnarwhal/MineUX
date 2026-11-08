package com.mineux.core.ui;

import com.mineux.core.config.ConfigFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Config file: config/mineux/ui.json -- how the desktop itself answers, rather than any one
 * module: whether a key that changed something says so. The person's, not a workstation's.
 */
public final class UiConfig implements ConfigFile.Backed {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/ui");

    private static final ConfigFile<UiConfig> FILE =
        new ConfigFile<>(UiConfig.class, "ui.json", UiConfig::new, LOGGER);

    private static UiConfig instance;

    /**
     * Say what changed on the actionbar when a bound key changes it. Without it a key bound to a
     * setting in a window you are not looking at is a press with no answer.
     */
    public boolean announceKeybinds = true;

    public static synchronized UiConfig get() {
        if (instance == null) instance = FILE.load();
        return instance;
    }

    @Override
    public void ensureDefaults() {
    }

    public void save() {
        FILE.save(this);
    }
}
