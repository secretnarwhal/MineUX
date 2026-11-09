package com.mineux.core.ui;

import com.mineux.core.config.ConfigFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Config file: config/mineux/ui.json -- how the desktop itself looks and answers, rather than
 * any one module: how big it draws, whether a key that changed something says so. The person's
 * (their screen, their hands), not a workstation's. The settings tree has it as {@code desk}.
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

    /**
     * The desktop's own GUI scale ({@link DeskScale}): {@code auto} or 1-4. The player's GUI
     * scale stays theirs for the HUD and the game's screens; the desktop looks the same whatever
     * it is. 2 is what it was designed at.
     */
    public String deskScale = String.valueOf(ScaleMath.DEFAULT);

    public static synchronized UiConfig get() {
        if (instance == null) instance = FILE.load();
        return instance;
    }

    @Override
    public void ensureDefaults() {
        String scale = ScaleMath.normal(deskScale);
        deskScale = scale != null ? scale : String.valueOf(ScaleMath.DEFAULT);
    }

    public void save() {
        FILE.save(this);
    }
}
