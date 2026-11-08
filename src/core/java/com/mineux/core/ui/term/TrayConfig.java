package com.mineux.core.ui.term;

import com.mineux.core.config.ConfigFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Config file: config/mineux/tray.json -- the tray's group switches.
 * <p>
 * A right click on a group's icon in the bar (combat, mace, crystal...) turns the whole group
 * off: every module of it that was on is switched off for real and remembered here, so it comes
 * back exactly as it was when the group is turned on again. Per workstation, like the modules
 * themselves.
 *
 * @see Tray#flipGroup
 */
public final class TrayConfig implements ConfigFile.Backed {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/tray");

    private static final ConfigFile<TrayConfig> FILE =
        new ConfigFile<>(TrayConfig.class, "tray.json", TrayConfig::new, LOGGER);

    private static TrayConfig instance;

    /** The keys of the groups switched off ({@code combat}, {@code macepvp}...). */
    public List<String> off = new ArrayList<>();

    /** The module paths ({@code clicker/hitselect}) that were on when their group went off: they come back on with it. */
    public List<String> held = new ArrayList<>();

    public static synchronized TrayConfig get() {
        if (instance == null) instance = FILE.load();
        return instance;
    }

    @Override
    public void ensureDefaults() {
        if (off == null) off = new ArrayList<>();
        if (held == null) held = new ArrayList<>();
        off.removeIf(s -> s == null || s.isBlank());
        held.removeIf(s -> s == null || s.isBlank());
    }

    public void save() {
        FILE.save(this);
    }
}
