package com.mineux.visuals.self;

import com.mineux.core.config.ConfigFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Config file: config/mineux/self.json -- how your own player looks.
 * <p>
 * Every choice is a string, never an enum: the obfuscation a host may apply renames enum constants, and
 * Gson would then read back nothing. A name the module does not know is put back to the default.
 * Everything here is drawn on your screen only; none of it is sent anywhere.
 *
 * @see Self
 */
public final class SelfConfig implements ConfigFile.Backed {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/self");

    private static final ConfigFile<SelfConfig> FILE =
        new ConfigFile<>(SelfConfig.class, "self.json", SelfConfig::new, LOGGER);

    private static SelfConfig instance;

    // ---- body view: your body in first person ----
    public boolean body = false;
    public boolean bodyArms = false;
    /** How far behind the camera the body stands, blocks. */
    public float bodyOffset = 0.3F;
    public boolean bodyShadow = true;
    public boolean bodyHideSwimFly = true;


    // ---- move animations ----
    public boolean moves = false;
    public String moveStyle = "natural";
    public float moveStrength = 1.0F;
    public boolean moveLean = true;
    public boolean moveSettle = true;
    public boolean moveBounce = true;
    public boolean moveOthers = false;

    // ---- jump effect ----
    public boolean jump = false;
    /** jump, land or both. */
    public String jumpTrigger = "jump";
    public boolean jumpSprintOnly = false;
    /** A ring style ({@link JumpRings#RINGS}) or a particle ({@link JumpRings#PARTICLES}). */
    public String jumpStyle = "ripple";
    public float jumpRadius = 0.6F;
    public int jumpPoints = 16;
    public float ringSize = 1.6F;
    /** Seconds a ring lasts. */
    public float ringTime = 0.9F;
    public String ringColor = "#6fc3ff";
    public String ringColor2 = "#c77dff";
    public boolean ringWalls = false;

    // ---- player model ----
    public boolean model = false;
    /** parka, dress, or custom: a file from config/mineux/models. */
    public String modelKind = "parka";
    public String modelFile = "";
    public boolean modelZUp = false;
    /** The custom model's height, blocks. */
    public float modelHeight = 1.85F;
    public float modelScale = 1.0F;
    public boolean modelLimbs = true;
    public boolean modelBob = true;
    public float modelYaw = 0.0F;
    public boolean modelLit = true;
    public boolean modelWalls = false;

    // ---- skin changer ----
    public boolean skin = false;
    /** A png in config/mineux/skins, "default" for the default skin, or empty for none. */
    public String skinFile = "";
    /** Wear this player's skin instead; empty for none. */
    public String skinCopy = "";
    /** auto, slim or wide. */
    public String skinModel = "auto";

    // ---- armour shaders ----
    public boolean armor = false;
    public boolean armorSelf = true;
    public boolean armorOthers = false;
    public String armorMaterial = "glass";
    /** The material's own colour rather than {@link #armorColor}. */
    public boolean armorNatural = true;
    public String armorColor = "#bfe6ff";
    public float armorOpacity = 1.0F;

    public static synchronized SelfConfig get() {
        if (instance == null) instance = FILE.load();
        return instance;
    }

    @Override
    public void ensureDefaults() {
        bodyOffset = Math.max(0.0F, Math.min(0.8F, bodyOffset));
        if (MovePose.styleIndex(moveStyle) < 0) moveStyle = "natural";
        moveStrength = Math.max(0.0F, Math.min(1.0F, moveStrength));
        if (JumpRings.triggerIndex(jumpTrigger) < 0) jumpTrigger = "jump";
        if (JumpRings.ringIndex(jumpStyle) < 0 && JumpRings.particleIndex(jumpStyle) < 0) jumpStyle = "ripple";
        jumpRadius = Math.max(0.1F, Math.min(3.0F, jumpRadius));
        jumpPoints = Math.max(4, Math.min(64, jumpPoints));
        ringSize = Math.max(0.1F, Math.min(6.0F, ringSize));
        ringTime = Math.max(0.05F, Math.min(5.0F, ringTime));
        if (ringColor == null) ringColor = "#6fc3ff";
        if (ringColor2 == null) ringColor2 = "#c77dff";
        if (SelfModel.kindIndex(modelKind) < 0) modelKind = "parka";
        if (modelFile == null) modelFile = "";
        modelHeight = Math.max(0.2F, Math.min(8.0F, modelHeight));
        modelScale = Math.max(0.1F, Math.min(4.0F, modelScale));
        modelYaw = Math.max(0.0F - 180.0F, Math.min(180.0F, modelYaw));
        if (skinFile == null) skinFile = "";
        if (skinCopy == null) skinCopy = "";
        if (Skins.modelIndex(skinModel) < 0) skinModel = "auto";
        if (ArmorShade.materialIndex(armorMaterial) < 0) armorMaterial = "glass";
        if (armorColor == null) armorColor = "#bfe6ff";
        armorOpacity = Math.max(0.1F, Math.min(1.0F, armorOpacity));
    }

    public void save() {
        FILE.save(this);
    }

    /** {@code #rrggbb} (or {@code rrggbb}) as 0xRRGGBB; anything else is the fallback. */
    static int rgb(String hex, int fallback) {
        if (hex == null) return fallback;
        String s = hex.startsWith("#") ? hex.substring(1) : hex;
        if (s.length() != 6) return fallback;
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
