package com.mineux.visuals.world;

import com.mineux.core.config.ConfigFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Config file: config/mineux/totempop.json
 * <p>
 * Every choice is a string, never an enum: the obfuscation a host may apply renames enum constants, and
 * Gson would then read back nothing. Colours are {@code #rrggbb}.
 *
 * @see TotemPop
 */
public final class TotemConfig implements ConfigFile.Backed {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/totempop");

    private static final ConfigFile<TotemConfig> FILE =
        new ConfigFile<>(TotemConfig.class, "totempop.json", TotemConfig::new, LOGGER);

    private static TotemConfig instance;

    public boolean enabled = false;

    /** ascension, shockwave, halo, runes, supernova or spiral. */
    public String style = "ascension";
    /** Times as long as the style's own. */
    public double duration = 1.0;
    public double size = 1.0;
    public double opacity = 1.0;
    /** totem (its gold and green), theme (the client's gold) or custom (the two colours below). */
    public String colorMode = "totem";
    public String color = "#ffd34d";
    public String color2 = "#6ee06a";
    /** Leave out the game's own cloud of totem particles. */
    public boolean hideVanilla = false;
    /** For your own pops. */
    public boolean self = true;
    /** For everyone else's. */
    public boolean others = true;

    public static synchronized TotemConfig get() {
        if (instance == null) instance = FILE.load();
        return instance;
    }

    @Override
    public void ensureDefaults() {
        if (TotemPop.styleIndex(style) < 0) style = "ascension";
        if (TotemPop.colorModeIndex(colorMode) < 0) colorMode = "totem";
        if (color == null) color = "#ffd34d";
        if (color2 == null) color2 = "#6ee06a";
        duration = Math.max(0.4, Math.min(2.5, duration));
        size = Math.max(0.4, Math.min(2.5, size));
        opacity = Math.max(0.1, Math.min(1.0, opacity));
    }

    public void save() {
        FILE.save(this);
    }
}
