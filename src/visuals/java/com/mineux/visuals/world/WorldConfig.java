package com.mineux.visuals.world;

import com.mineux.core.config.ConfigFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Config file: config/mineux/world.json -- the look of the world around you: ambience,
 * no render, ambient particles and item physics. All of it is drawn on this client only.
 * <p>
 * Every choice is a string, never an enum: the obfuscation a host may apply renames enum constants,
 * and Gson would then read back nothing. Colours are {@code #rrggbb}.
 *
 * @see Ambience
 * @see NoRender
 * @see Particles
 * @see ItemPhysics
 */
public final class WorldConfig implements ConfigFile.Backed {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/world");

    private static final ConfigFile<WorldConfig> FILE =
        new ConfigFile<>(WorldConfig.class, "world.json", WorldConfig::new, LOGGER);

    private static WorldConfig instance;

    // ---- ambience ---------------------------------------------------------------------------

    public boolean ambience = false;

    /** Your own time of day: the sun, the sky and the light as at {@link #timeOfDay}. */
    public boolean time = true;
    /** Ticks into the day, 0 to 23999: 6000 is noon, 18000 midnight. */
    public int timeOfDay = 6000;

    /** off (the server's weather), clear, rain or thunder. */
    public String weather = "off";

    public boolean fogColour = false;
    public String fogColor = "#c0d8ff";
    public boolean fogDistance = false;
    /** Where the fog closes in, chunks; 0 is as thick as it goes. */
    public int fogChunks = 16;

    public boolean skyColour = false;
    public String skyColor = "#78a7ff";
    /** Fade from {@link #skyColor} overhead to {@link #skyColor2} as you look down. */
    public boolean skyGradient = false;
    public String skyColor2 = "#1a2a6c";

    /** Every light in the world multiplied by {@link #lightmapColor}. */
    public boolean lightmap = false;
    public String lightmapColor = "#ffffff";

    // ---- no render --------------------------------------------------------------------------

    public boolean noRender = false;
    public boolean hideExplosions = false;
    public boolean hideScoreboard = false;
    public boolean hideNausea = true;
    public boolean hidePortal = true;
    public boolean hideSpyglass = true;
    public boolean hideVignette = false;
    public boolean hideTotemAnimation = false;
    public boolean hideFire = true;

    // ---- particles --------------------------------------------------------------------------

    public boolean particles = false;
    /** One of {@link Particles}' names: drawn by this client (snow, glow, rain) or the game's own. */
    public String particle = "snow";
    /** New ones a tick. */
    public int particleRate = 4;
    public double particleRadius = 16.0;
    public double particleHeight = 8.0;
    /** How fast they fall, blocks a tick. */
    public double particleSpeed = 0.05;
    public double particleSize = 1.0;
    /** Snow, glow and rain in their own colours; off for {@link #particleColor} and {@link #particleColor2}. */
    public boolean particleNatural = true;
    public String particleColor = "#8fd3ff";
    public String particleColor2 = "#ff9ae6";
    public boolean particleWalls = false;

    // ---- item physics -----------------------------------------------------------------------

    public boolean itemPhysics = false;
    /** How fast a falling item tumbles. */
    public double itemSpin = 1.0;
    /** How much it rocks before it settles flat. */
    public double itemBounce = 0.5;

    public static synchronized WorldConfig get() {
        if (instance == null) instance = FILE.load();
        return instance;
    }

    @Override
    public void ensureDefaults() {
        timeOfDay = Math.max(0, Math.min(23999, timeOfDay));
        if (Ambience.weatherIndex(weather) < 0) weather = "off";
        fogChunks = Math.max(0, Math.min(32, fogChunks));
        if (fogColor == null) fogColor = "#c0d8ff";
        if (skyColor == null) skyColor = "#78a7ff";
        if (skyColor2 == null) skyColor2 = "#1a2a6c";
        if (lightmapColor == null) lightmapColor = "#ffffff";

        if (Particles.index(particle) < 0) particle = "snow";
        particleRate = Math.max(1, Math.min(40, particleRate));
        particleRadius = Math.max(4.0, Math.min(48.0, particleRadius));
        particleHeight = Math.max(2.0, Math.min(24.0, particleHeight));
        particleSpeed = Math.max(0.01, Math.min(0.5, particleSpeed));
        particleSize = Math.max(0.25, Math.min(4.0, particleSize));
        if (particleColor == null) particleColor = "#8fd3ff";
        if (particleColor2 == null) particleColor2 = "#ff9ae6";

        itemSpin = Math.max(0.1, Math.min(3.0, itemSpin));
        itemBounce = Math.max(0.0, Math.min(1.0, itemBounce));
    }

    public void save() {
        FILE.save(this);
    }
}
