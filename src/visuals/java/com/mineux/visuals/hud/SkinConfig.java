package com.mineux.visuals.hud;

import com.mineux.core.config.ConfigFile;
import com.mineux.visuals.util.Words;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Config file: config/mineux/hudskin.json -- the game's own HUD pieces redrawn in the
 * client's look (crosshair, hotbar and status bars, scoreboard, chat) and the
 * main menu's skin with theme textures. Every piece is off by default.
 * <p>
 * Every choice is a string constant, never an enum: the obfuscation a host may apply renames enum
 * constants, and Gson would then read back nothing.
 */
public final class SkinConfig implements ConfigFile.Backed {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/hudskin");

    private static final ConfigFile<SkinConfig> FILE =
        new ConfigFile<>(SkinConfig.class, "hudskin.json", SkinConfig::new, LOGGER);

    private static SkinConfig instance;

    static final Set<String> CROSSHAIR_STYLES = Words.of("cross dot circle crossdot segments chevron diamond brackets");
    static final Set<String> COLOR_MODES = Words.of("custom gradient chroma");
    static final Set<String> BAR_STYLES = Words.of("bars pips");
    static final Set<String> SIDES = Words.of("left right");
    static final Set<String> ACCENTS = Words.of("line bar none");
    static final Set<String> CHAT_BACKS = Words.of("panel vanilla none");
    static final Set<String> CHAT_ANIMS = Words.of("slide fade none");
    static final Set<String> TIME_FORMATS = Words.of("24h 24hs 12h");
    static final Set<String> CORNERS = Words.of("topleft topright bottomleft bottomright");
    static final Set<String> MENU_SKINS = Words.of("terminal scene");
    static final Set<String> SCENES = Words.of("aurora nebula rain bokeh silk grid picture");
    static final Set<String> THEME_SCOPES = Words.of("gui all");

    // ---- crosshair -------------------------------------------------------------------------------

    public boolean crosshair = false;
    public String chStyle = "cross";
    /** Arm length, gap, thickness and dot, in pixels of a 1080p window (scaled with the window). */
    public float chSize = 6.0F;
    public float chGap = 3.0F;
    public float chThickness = 2.0F;
    public float chDot = 3.0F;
    /** Degrees it is turned by, and turns a second (quarter turns a second). */
    public float chRotation = 0.0F;
    public float chSpin = 0.0F;
    public String chColorMode = "custom";
    public String chColor = "#ffffff";
    public String chColor2 = "#7a8cff";
    public boolean chOutline = true;
    public float chOutlineWidth = 1.0F;
    /** A soft halo round the shape, 0 for none. */
    public float chGlow = 0.6F;
    /** The arms open out while you move and while you are in the air. */
    public boolean chDynamic = false;
    /** The arms jump out and back on your hit. */
    public boolean chExpand = true;
    public boolean chHitmarker = true;
    public String chHitColor = "#ff3b3b";
    /** The crosshair flashes the hit colour on your hit. */
    public boolean chFlash = true;
    /** A ring runs out from the middle on your hit. */
    public boolean chShockwave = false;
    /** A burst and a cross in the hit colour when the one you just hit dies. */
    public boolean chKill = true;
    /** Only hits on players count for the marker, the flash and the kill. */
    public boolean chPlayersOnly = true;
    /** Turns the target colour while it is on an entity. */
    public boolean chTarget = false;
    public String chTargetColor = "#ff5050";
    /** The attack charge as a ring round it while it fills. */
    public boolean chCooldown = true;

    // ---- hotbar and status bars ------------------------------------------------------------------

    public boolean hotbar = false;
    /** The slots themselves; off leaves vanilla's slots and redraws only the bars. */
    public boolean hbSlots = true;
    /** Health, absorption, armour, food and air as bars over the hotbar. */
    public boolean hbStatus = true;
    /** The experience bar and level, under the slots. */
    public boolean hbXp = true;
    public String hbStyle = "bars";
    /** The values written on the bars. */
    public boolean hbNumbers = true;
    public boolean hbSlotNumbers = false;
    public String hbAccent = "#efc648";
    public float hbOpacity = 0.72F;
    /** The selection slides between slots and the bars ease to new values. */
    public boolean hbSmooth = true;
    /** Health lost lingers as a pale trail before it drains. */
    public boolean hbTrail = true;
    /** The health bar pulses below a quarter. */
    public boolean hbPulse = true;
    public String hbHealth = "#ff4d5e";
    public String hbAbsorb = "#ffc940";
    public String hbHunger = "#f2a33a";
    public String hbArmor = "#a9c4e8";
    public String hbAir = "#5bc8ff";
    public String hbXpColor = "#7ce35b";

    /** The hotbar, its bars and the scoreboard, over the GUI scale. */
    public float hudScale = 1.0F;
    /** The size they are at GUI scale 3 on a 1080p window, whatever the window; {@link #hudScale} on top. */
    public boolean hudAutoScale = false;

    // ---- scoreboard ------------------------------------------------------------------------------

    public boolean scoreboard = false;
    public String sbSide = "right";
    public int sbX = 4;
    public int sbY = 0;
    public float sbOpacity = 0.55F;
    public int sbPadding = 5;
    /** Extra pixels between lines. */
    public int sbSpacing = 1;
    /** Leave the red score numbers out: on most servers they are only the line order. */
    public boolean sbHideNumbers = true;
    public boolean sbCenterTitle = true;
    public boolean sbBorder = true;
    public String sbAccent = "line";
    public boolean sbShadow = false;
    /** The panel eases to a new height when lines come and go. */
    public boolean sbAnimate = true;

    // ---- chat ------------------------------------------------------------------------------------

    public boolean chat = false;
    public boolean chatTimestamps = false;
    public String chatTimeFormat = "24h";
    /** A message the same as one of the last few comes back as that one, with a count. */
    public boolean chatAntiSpam = true;
    public int chatSpamRange = 5;
    /** 1000 lines kept instead of 100. */
    public boolean chatHistory = true;
    public String chatBackground = "panel";
    public float chatOpacity = 0.45F;
    public boolean chatAccentBar = false;
    public String chatAnimation = "slide";
    public float chatSpeed = 1.0F;

    // ---- main menu -------------------------------------------------------------------------------

    /** terminal: the desktop as ever; scene: the same desktop over an animated scene. */
    public String menuSkin = "terminal";
    public String menuScene = "aurora";
    public float menuSpeed = 1.0F;
    public boolean menuParallax = true;
    /** A soft light that follows the cursor, 0 for none. */
    public float menuSpotlight = 1.0F;
    /** Your face and name in the corner. */
    public boolean menuAccount = true;
    public String menuAccent = "#efc648";

    /** Every gui texture tinted toward one colour; takes a resource reload to come and go. */
    public boolean themeTextures = false;
    public float themeStrength = 0.6F;
    public String themeColor = "#efc648";
    /** gui: menus, buttons, the hud; all: block and item textures too. */
    public String themeScope = "gui";

    public static synchronized SkinConfig get() {
        if (instance == null) instance = FILE.load();
        return instance;
    }

    @Override
    public void ensureDefaults() {
        chStyle = pick(chStyle, CROSSHAIR_STYLES, "cross");
        chColorMode = pick(chColorMode, COLOR_MODES, "custom");
        hbStyle = pick(hbStyle, BAR_STYLES, "bars");
        sbSide = pick(sbSide, SIDES, "right");
        sbAccent = pick(sbAccent, ACCENTS, "line");
        chatBackground = pick(chatBackground, CHAT_BACKS, "panel");
        chatAnimation = pick(chatAnimation, CHAT_ANIMS, "slide");
        chatTimeFormat = pick(chatTimeFormat, TIME_FORMATS, "24h");
        menuSkin = pick(menuSkin, MENU_SKINS, "terminal");
        menuScene = pick(menuScene, SCENES, "aurora");
        themeScope = pick(themeScope, THEME_SCOPES, "gui");
        chSize = clamp(chSize, 0.0F, 40.0F);
        chGap = clamp(chGap, 0.0F, 30.0F);
        chThickness = clamp(chThickness, 0.5F, 10.0F);
        chDot = clamp(chDot, 0.0F, 12.0F);
        chOutlineWidth = clamp(chOutlineWidth, 0.0F, 4.0F);
        chGlow = clamp(chGlow, 0.0F, 2.0F);
        chSpin = clamp(chSpin, 0.0F - 8.0F, 8.0F);
        hbOpacity = clamp(hbOpacity, 0.0F, 1.0F);
        sbOpacity = clamp(sbOpacity, 0.0F, 1.0F);
        chatOpacity = clamp(chatOpacity, 0.0F, 1.0F);
        chatSpeed = clamp(chatSpeed, 0.2F, 5.0F);
        chatSpamRange = Math.max(1, Math.min(50, chatSpamRange));
        menuSpeed = clamp(menuSpeed, 0.0F, 3.0F);
        menuSpotlight = clamp(menuSpotlight, 0.0F, 2.0F);
        themeStrength = clamp(themeStrength, 0.0F, 1.0F);
        // A colour gone from the file: back to its default (a bad one reads as the default where it is drawn).
        chColor = colour(chColor, "#ffffff");
        chColor2 = colour(chColor2, "#7a8cff");
        chHitColor = colour(chHitColor, "#ff3b3b");
        chTargetColor = colour(chTargetColor, "#ff5050");
        hbAccent = colour(hbAccent, "#efc648");
        hbHealth = colour(hbHealth, "#ff4d5e");
        hbAbsorb = colour(hbAbsorb, "#ffc940");
        hbHunger = colour(hbHunger, "#f2a33a");
        hbArmor = colour(hbArmor, "#a9c4e8");
        hbAir = colour(hbAir, "#5bc8ff");
        hbXpColor = colour(hbXpColor, "#7ce35b");
        if (Float.isNaN(hudScale) || hudScale < 0.5F || hudScale > 3.0F) hudScale = 1.0F;
        menuAccent = colour(menuAccent, "#efc648");
        themeColor = colour(themeColor, "#efc648");
    }

    private static String pick(String value, Set<String> allowed, String fallback) {
        return value != null && allowed.contains(value) ? value : fallback;
    }

    private static String colour(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static float clamp(float value, float lo, float hi) {
        return Float.isNaN(value) ? lo : Math.max(lo, Math.min(hi, value));
    }

    public void save() {
        FILE.save(this);
    }

    /** {@code #rrggbb} as an opaque colour; {@code fallback} (rgb) when it does not read. */
    public static int rgb(String hex, int fallback) {
        if (hex == null) return 0xFF000000 | fallback;
        String s = hex.startsWith("#") ? hex.substring(1) : hex;
        if (s.length() != 6) return 0xFF000000 | fallback;
        try {
            return 0xFF000000 | Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return 0xFF000000 | fallback;
        }
    }
}
