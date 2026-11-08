package com.mineux.visuals.cosmetics;

import com.mineux.core.config.ConfigFile;
import com.mineux.visuals.cosmetics.aura.Auras;
import com.mineux.visuals.cosmetics.hats.Hats;
import com.mineux.visuals.cosmetics.kill.KillStyles;
import com.mineux.visuals.cosmetics.pets.Pets;
import com.mineux.visuals.cosmetics.wings.Wings;
import com.mineux.visuals.util.Words;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * Config file: config/mineux/cosmetics.json
 * <p>
 * One pick a slot, every pick a string (the obfuscation a host may apply renames enum constants, and
 * Gson would read back nothing); a name a slot does not know is put back to off.
 *
 * @see Cosmetics
 */
public final class CosmeticsConfig implements ConfigFile.Backed {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/cosmetics");

    private static final ConfigFile<CosmeticsConfig> FILE =
        new ConfigFile<>(CosmeticsConfig.class, "cosmetics.json", CosmeticsConfig::new, LOGGER);

    private static final Set<String> PET_SIDES = Words.of("right left behind");

    private static CosmeticsConfig instance;

    public boolean enabled = false;

    public String cape = "off";
    /** For the {@code custom} cape: which picture in config/mineux/capes/ (see {@link Capes}). */
    public String capeFile = "";
    public String wings = "off";
    public String hat = "off";
    public String aura = "off";
    public String kill = "off";
    public String pet = "off";

    /** The two colours every cosmetic is drawn in, #rrggbb. */
    public String color = "#7fd9ff";
    public String color2 = "#c77dff";
    /** A style that has colours of its own (the elements, soul flame, strike...) uses them. */
    public boolean natural = true;

    public double scale = 1.0;
    public double opacity = 0.9;
    public boolean animate = true;

    public boolean hatFirstPerson = false;
    public boolean auraFirstPerson = true;
    public double hatHeight = 0.0;

    public double flapSpeed = 1.0;

    public int auraCount = 5;
    public double auraRadius = 1.0;
    public double auraSpeed = 0.25;
    /** How much the aura speeds up and spreads as you run, 0 for not at all. */
    public double auraReact = 1.0;


    public String petSide = "right";
    public double petDistance = 1.2;
    public double petSpeed = 1.0;
    public double petScale = 1.0;

    public static synchronized CosmeticsConfig get() {
        if (instance == null) instance = FILE.load();
        return instance;
    }

    @Override
    public void ensureDefaults() {
        cape = known(Capes.NAMES, cape);
        if (capeFile == null) capeFile = "";
        wings = known(Wings.NAMES, wings);
        hat = known(Hats.NAMES, hat);
        aura = known(Auras.NAMES, aura);
        kill = known(KillStyles.NAMES, kill);
        pet = known(Pets.NAMES, pet);
        if (color == null) color = "#7fd9ff";
        if (color2 == null) color2 = "#c77dff";
        if (petSide == null || !PET_SIDES.contains(petSide)) petSide = "right";
        scale = Math.max(0.25, Math.min(3.0, scale));
        opacity = Math.max(0.05, Math.min(1.0, opacity));
        hatHeight = Math.max(0.0 - 0.5, Math.min(1.0, hatHeight));
        flapSpeed = Math.max(0.0, Math.min(4.0, flapSpeed));
        auraCount = Math.max(1, Math.min(16, auraCount));
        auraRadius = Math.max(0.3, Math.min(3.0, auraRadius));
        auraSpeed = Math.max(0.0 - 2.0, Math.min(2.0, auraSpeed));
        auraReact = Math.max(0.0, Math.min(2.0, auraReact));
        petDistance = Math.max(0.5, Math.min(4.0, petDistance));
        petSpeed = Math.max(0.2, Math.min(3.0, petSpeed));
        petScale = Math.max(0.3, Math.min(3.0, petScale));
    }

    private static String known(List<String> names, String name) {
        return name != null && names.contains(name) ? name : "off";
    }

    /** The colour {@code hex} names (#rrggbb), opaque, or {@code fallback}. */
    static int rgb(String hex, int fallback) {
        if (hex == null) return fallback;
        String digits = hex.startsWith("#") ? hex.substring(1) : hex;
        if (digits.length() != 6) return fallback;
        try {
            return 0xFF000000 | Integer.parseInt(digits, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public void save() {
        FILE.save(this);
    }
}
