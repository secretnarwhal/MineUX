package com.mineux.visuals.world;

import com.mineux.core.config.ConfigFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Config file: config/mineux/shaders.json -- the sky shaders and the world shaders.
 * <p>
 * Every choice is a string, never an enum: the obfuscation a host may apply renames enum constants, and
 * Gson would then read back nothing. Colours are {@code #rrggbb}.
 *
 * @see SkyShader
 * @see WorldShader
 */
public final class ShaderConfig implements ConfigFile.Backed {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/shaders");

    private static final ConfigFile<ShaderConfig> FILE =
        new ConfigFile<>(ShaderConfig.class, "shaders.json", ShaderConfig::new, LOGGER);

    private static ShaderConfig instance;

    // ---- the sky ----------------------------------------------------------------------------

    public boolean sky = false;
    /** One of {@link SkyLook}'s presets. */
    public String skyPreset = "aurora";
    /** The preset's own colours; off for {@link #skyColor} and {@link #skyColor2}. */
    public boolean skyNatural = true;
    public String skyColor = "#2bff88";
    public String skyColor2 = "#8a4dff";
    public double skyIntensity = 1.0;
    public double skyScale = 1.0;
    public double skySpeed = 1.0;
    /** How fast the sky turns round, as the stars do; below nothing the other way. */
    public double skyRotation = 0.3;
    public double skyStars = 1.0;
    public double skyBrightness = 1.0;
    /** How much the horizon fades into the fog's colour. */
    public double skyHorizon = 0.5;
    /** full, 75 or 50: the share of the screen's pixels the sky is worked out at. */
    public String skyResolution = "full";
    public boolean skyInEnd = true;
    public boolean skyHideClouds = true;
    /** Keep the sun and the moon in front of the shader's sky. */
    public boolean skySunMoon = false;
    /** The world's fog in the sky's horizon colour. */
    public boolean skyMatchFog = true;
    /** The fog preset's own fog distance, blocks. */
    public double skyFogDistance = 48.0;

    // ---- the world --------------------------------------------------------------------------

    public boolean world = false;
    /** One of {@link WorldLook}'s presets. */
    public String worldPreset = "realistic";
    /** low, medium, high or ultra. */
    public String quality = "medium";
    /** The preset's own sun and fog colours; off for {@link #sunColor} and {@link #fogColor}. */
    public boolean worldNatural = true;
    public String sunColor = "#ffe2b0";
    public String fogColor = "#b8c8dc";
    public double sunLight = 1.0;
    public double shadows = 1.0;
    public double godRays = 1.0;
    public double fog = 1.0;
    public double bloom = 1.0;
    public double exposure = 1.0;
    public double contrast = 1.0;
    public double saturation = 1.0;
    public double warmth = 0.0;
    public double vignette = 1.0;
    /** Blocks above the ground the low fog reaches. */
    public double fogHeight = 12.0;
    /** Blocks round you the fog leaves clear. */
    public double clearRadius = 6.0;
    public double fogDrift = 1.0;
    public boolean sunGlowInFog = true;

    public static synchronized ShaderConfig get() {
        if (instance == null) instance = FILE.load();
        return instance;
    }

    @Override
    public void ensureDefaults() {
        if (SkyLook.index(skyPreset) < 0) skyPreset = "aurora";
        if (SkyShader.resolutionIndex(skyResolution) < 0) skyResolution = "full";
        if (skyColor == null) skyColor = "#2bff88";
        if (skyColor2 == null) skyColor2 = "#8a4dff";
        skyIntensity = clamp(skyIntensity, 0.0, 3.0);
        skyScale = clamp(skyScale, 0.25, 3.0);
        skySpeed = clamp(skySpeed, 0.0, 5.0);
        skyRotation = clamp(skyRotation, 0.0 - 5.0, 5.0);
        skyStars = clamp(skyStars, 0.0, 3.0);
        skyBrightness = clamp(skyBrightness, 0.0, 3.0);
        skyHorizon = clamp(skyHorizon, 0.0, 1.0);
        skyFogDistance = clamp(skyFogDistance, 8.0, 256.0);

        if (WorldLook.index(worldPreset) < 0) worldPreset = "realistic";
        if (WorldShader.qualityIndex(quality) < 0) quality = "medium";
        if (sunColor == null) sunColor = "#ffe2b0";
        if (fogColor == null) fogColor = "#b8c8dc";
        sunLight = clamp(sunLight, 0.0, 2.0);
        shadows = clamp(shadows, 0.0, 2.0);
        godRays = clamp(godRays, 0.0, 3.0);
        fog = clamp(fog, 0.0, 3.0);
        bloom = clamp(bloom, 0.0, 3.0);
        exposure = clamp(exposure, 0.4, 2.5);
        contrast = clamp(contrast, 0.5, 1.6);
        saturation = clamp(saturation, 0.0, 2.0);
        warmth = clamp(warmth, 0.0 - 1.0, 1.0);
        vignette = clamp(vignette, 0.0, 2.0);
        fogHeight = clamp(fogHeight, 1.0, 64.0);
        clearRadius = clamp(clearRadius, 0.0, 64.0);
        fogDrift = clamp(fogDrift, 0.0, 4.0);
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }

    public void save() {
        FILE.save(this);
    }
}
