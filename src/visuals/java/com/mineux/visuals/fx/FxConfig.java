package com.mineux.visuals.fx;

import com.mineux.core.config.ConfigFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Config file: config/mineux/fx.json
 * <p>
 * Every choice is a string, never an enum: the obfuscation a host may apply renames enum constants, and
 * Gson would then read back nothing. The names are the ones {@link FxSlots} knows; one it does
 * not is put back to the default.
 *
 * @see Fx
 * @see FxSlots how the three hit slots combine
 */
public final class FxConfig implements ConfigFile.Backed {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/fx");

    private static final ConfigFile<FxConfig> FILE =
        new ConfigFile<>(FxConfig.class, "fx.json", FxConfig::new, LOGGER);

    private static FxConfig instance;

    public boolean enabled = false;

    /**
     * The three hit slots, each off, vanilla or a burst ({@link FxSlots#EFFECTS}): every hit of
     * yours, your crits, your other hits. A crit plays every-hit and crit, any other hit
     * every-hit and non-crit; crit and non-crit keep the game's own particles only at vanilla.
     */
    public String everyHit = "crit";
    public String critHit = FxSlots.VANILLA;
    public String nonCritHit = FxSlots.VANILLA;
    /** Particles in each burst. */
    public int count = 12;

    /** A sound of your own when your hit lands, and on top of it for a crit or for any other hit: off, or one of {@link FxSlots#SOUNDS}. */
    public String hitSound = "off";
    public String critSound = "off";
    public String nonCritSound = "off";

    /** Something on whoever dies within a few seconds of your hit: off, lightning, totem, flame, soul. */
    public String killEffect = "lightning";
    public String killSound = "levelup";

    /**
     * The one hit setting from before the three slots: whether a hit burst at all, and of what.
     * Read once into {@link #everyHit} and then emptied; Gson writes no nulls, so it is gone from
     * the file after. Only an old fx.json (or an old workstation's copy) has them.
     */
    public Boolean hitParticles = null;
    public String particle = null;

    public static synchronized FxConfig get() {
        if (instance == null) instance = FILE.load();
        return instance;
    }

    @Override
    public void ensureDefaults() {
        migrate();
        count = Math.max(1, Math.min(60, count));
        everyHit = FxSlots.valid(everyHit, "crit");
        critHit = FxSlots.valid(critHit, FxSlots.VANILLA);
        nonCritHit = FxSlots.valid(nonCritHit, FxSlots.VANILLA);
        if (FxSlots.soundIndex(hitSound) < 0) hitSound = "off";
        if (FxSlots.soundIndex(critSound) < 0) critSound = "off";
        if (FxSlots.soundIndex(nonCritSound) < 0) nonCritSound = "off";
        if (FxSlots.soundIndex(killSound) < 0) killSound = "levelup";
        if (FxSlots.killIndex(killEffect) < 0) killEffect = "lightning";
    }

    /**
     * The old single setting into the slots, so that nothing looks different: its burst (or off,
     * when hits burst nothing) becomes the every-hit slot, and crit and non-crit stay vanilla --
     * the game's own crit stars, as before. A slot set already (a file that has both) is only
     * overwritten for every-hit, which is what the old setting was. True when there was one.
     */
    boolean migrate() {
        if (hitParticles == null && particle == null) return false;
        boolean on = hitParticles == null || hitParticles.booleanValue();
        String old = particle;
        // The old repair put a burst it did not know back to crit.
        if (FxSlots.burst(old) < 0) old = "crit";
        everyHit = on ? old : FxSlots.OFF;
        hitParticles = null;
        particle = null;
        return true;
    }

    public void save() {
        FILE.save(this);
    }
}
