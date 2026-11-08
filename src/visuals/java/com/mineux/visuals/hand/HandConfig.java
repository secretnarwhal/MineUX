package com.mineux.visuals.hand;

import com.mineux.core.config.ConfigFile;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Config file: config/mineux/hand.json
 * <p>
 * The first-person hand and the item in it, drawn only. Every switch is off to begin with. Every
 * choice is a string, never an enum: the obfuscation a host may apply renames enum constants, and Gson
 * would then read back nothing. A name the code does not know is put back to the default.
 *
 * @see Hand
 */
public final class HandConfig implements ConfigFile.Backed {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/hand");

    private static final ConfigFile<HandConfig> FILE =
        new ConfigFile<>(HandConfig.class, "hand.json", HandConfig::new, LOGGER);

    private static HandConfig instance;

    // ---- view model: where each hand sits ------------------------------------------------------

    public boolean viewModel = false;
    /** The off hand as the main hand's mirror image, its own numbers unused. */
    public boolean vmMirror = false;
    public double vmMainX = 0.0, vmMainY = 0.0, vmMainZ = 0.0;
    public double vmMainRotX = 0.0, vmMainRotY = 0.0, vmMainRotZ = 0.0;
    public double vmMainScale = 1.0;
    public double vmOffX = 0.0, vmOffY = 0.0, vmOffZ = 0.0;
    public double vmOffRotX = 0.0, vmOffRotY = 0.0, vmOffRotZ = 0.0;
    public double vmOffScale = 1.0;

    // ---- hand view: your arm holding the item --------------------------------------------------

    public boolean handView = false;
    /** grip: the arm holds the item as it would in third person; overlay: the arm drawn under vanilla's item. */
    public String hvStyle = "grip";
    public boolean hvOffHand = true;
    public boolean hvSleeve = true;
    public double hvOffsetX = 0.0, hvOffsetY = 0.0, hvOffsetZ = 0.0;
    public double hvRotation = 0.0;
    /** Eating, drinking, blocking, drawing a bow and the rest, the arm's own way. */
    public boolean hvUseAnims = true;
    public double hvUseSpeed = 1.0;

    // ---- swing animations ----------------------------------------------------------------------

    public boolean animations = false;
    public String animMode = "old";
    public int animStrength = 20;

    /** The swing drawn over this many ticks instead of the game's six; the real swing is untouched. */
    public boolean swingSpeed = false;
    public int swingTicks = 6;

    /** Your own first-person swing not drawn; the swing still happens and is still sent. */
    public boolean noSwing = false;

    // ---- inspect -------------------------------------------------------------------------------

    public boolean inspect = false;
    public int inspectKey = GLFW.GLFW_KEY_Y;
    public String inspectAnim = "auto";
    public boolean inspectOnSwitch = false;
    public double inspectDuration = 1.6;
    public double inspectSpeed = 1.0;
    public double inspectIntensity = 1.0;
    public double inspectSmooth = 0.5;
    public boolean inspectCancel = true;
    public boolean inspectHideOff = true;
    public boolean inspectEmpty = false;

    // ---- enchant glint -------------------------------------------------------------------------

    public boolean glint = false;
    public String glintColor = "#8040ff";

    // ---- hand shaders --------------------------------------------------------------------------

    public boolean shaders = false;
    public String shader = "chroma";
    public double chromaSpeed = 0.3, chromaSaturation = 1.0, chromaBrightness = 1.0;
    public double invertIntensity = 1.0;
    public double edgeGlow = 0.0;
    public String glowColor = "#00e5ff";
    public String outlineColor = "#ffffff";
    public double outlineThickness = 1.5;
    public boolean tint = false;
    public String tintColor = "#ff0000";
    public double tintAmount = 0.4;
    public double pixelSize = 6.0;
    public String accent = "#a855f7";
    public String accent2 = "#22d3ee";

    public static synchronized HandConfig get() {
        if (instance == null) instance = FILE.load();
        return instance;
    }

    @Override
    public void ensureDefaults() {
        vmMainScale = clamp(vmMainScale, 0.1, 3.0);
        vmOffScale = clamp(vmOffScale, 0.1, 3.0);
        if (hvStyle == null || !hvStyle.equals("overlay")) hvStyle = "grip";
        hvUseSpeed = clamp(hvUseSpeed, 0.2, 3.0);
        if (Swing.modeIndex(animMode) < 0) animMode = "old";
        animStrength = Math.max(20, Math.min(75, animStrength));
        swingTicks = Math.max(1, Math.min(20, swingTicks));
        if (Inspect.animIndex(inspectAnim) < 0) inspectAnim = "auto";
        inspectDuration = clamp(inspectDuration, 0.3, 5.0);
        inspectSpeed = clamp(inspectSpeed, 0.1, 4.0);
        inspectIntensity = clamp(inspectIntensity, 0.0, 2.0);
        inspectSmooth = clamp(inspectSmooth, 0.0, 1.0);
        if (HandShaders.index(shader) < 0) shader = "chroma";
        chromaSpeed = clamp(chromaSpeed, 0.0, 3.0);
        chromaSaturation = clamp(chromaSaturation, 0.0, 1.0);
        chromaBrightness = clamp(chromaBrightness, 0.0, 1.0);
        invertIntensity = clamp(invertIntensity, 0.0, 1.0);
        edgeGlow = clamp(edgeGlow, 0.0, 3.0);
        outlineThickness = clamp(outlineThickness, 0.5, 8.0);
        tintAmount = clamp(tintAmount, 0.0, 1.0);
        pixelSize = clamp(pixelSize, 2.0, 24.0);
        glintColor = colour(glintColor, "#8040ff");
        glowColor = colour(glowColor, "#00e5ff");
        outlineColor = colour(outlineColor, "#ffffff");
        tintColor = colour(tintColor, "#ff0000");
        accent = colour(accent, "#a855f7");
        accent2 = colour(accent2, "#22d3ee");
    }

    public void save() {
        FILE.save(this);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** A colour as #rrggbb, or the default when it does not read as one. */
    private static String colour(String hex, String fallback) {
        return rgb(hex, 0 - 1) < 0 ? fallback : hex;
    }

    /** #rrggbb (or rrggbb) as 0xRRGGBB, or the fallback. */
    static int rgb(String hex, int fallback) {
        if (hex == null) return fallback;
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() != 6) return fallback;
        try {
            return Integer.parseInt(h, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
