package com.mineux.visuals.trail;

import com.mineux.core.config.ConfigFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Config file: config/mineux/trail.json
 * <p>
 * All cursor-trail knobs. The fake cursor that can draw on top of it is its own config,
 * {@link FakeCursorConfig}. Off by default, like every visual module.
 */
public final class TrailConfig implements ConfigFile.Backed {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/trail");

    private static final ConfigFile<TrailConfig> FILE =
        new ConfigFile<>(TrailConfig.class, "trail.json", TrailConfig::new, LOGGER);

    private static TrailConfig instance;

    // ---- the slider ranges the panel exposes --------------------------

    public static final int MIN_LIFETIME_MS = 50;
    public static final int MAX_LIFETIME_MS = 500;

    public static final int MIN_SPRITE_SIZE_PX = 4;
    public static final int MAX_SPRITE_SIZE_PX = 256;

    public static final int MIN_STAMP_SPACING_PX = 1;
    public static final int MAX_STAMP_SPACING_PX = 64;

    public static final float MIN_TAIL_SIZE_SCALE = 0.0f;
    public static final float MAX_TAIL_SIZE_SCALE = 2.0f;

    public boolean enabled = false;

    public int lifetimeMs = 150;
    public int stampSpacingPx = 1;
    public int spriteSizePx = 11;
    public float tailSizeScale = 0.7f;

    public boolean useVelocitySize = false;
    public float velocitySizeIntensity = 0.5f;

    public float colorR = 1.0f, colorG = 1.0f, colorB = 1.0f;
    public boolean useGradient = false;
    public float tailColorR = 0.0f, tailColorG = 0.0f, tailColorB = 0.0f;

    public float opacity = 0.8f;

    /**
     * How the trail is blended: {@link #ALPHA} or {@link #ADDITIVE}.
     * <p>
     * A string and not an enum, because an enum does not survive the obfuscation a host may apply: Gson
     * writes and reads an enum by its constants' names, and the obfuscator renames them. Every
     * value it can hold is spelled here, and they are the names the enum used to write, so a
     * trail.json from before the change reads back as it was.
     */
    public static final String ALPHA = "ALPHA";
    public static final String ADDITIVE = "ADDITIVE";

    public String blendMode = ALPHA;

    /**
     * Absolute or game-directory-relative path to a custom sprite image
     * (anything Minecraft's NativeImage can decode, max 256x256px). Null or
     * empty = a procedurally-generated soft white dot.
     */
    public String spritePath = "";

    
    public static synchronized TrailConfig get() {
        if (instance == null) instance = FILE.load();
        return instance;
    }

    @Override
    public void ensureDefaults() {
        lifetimeMs = clamp(lifetimeMs, MIN_LIFETIME_MS, MAX_LIFETIME_MS);
        spriteSizePx = clamp(spriteSizePx, MIN_SPRITE_SIZE_PX, MAX_SPRITE_SIZE_PX);
        stampSpacingPx = clamp(stampSpacingPx, MIN_STAMP_SPACING_PX, MAX_STAMP_SPACING_PX);
        tailSizeScale = clamp(tailSizeScale, MIN_TAIL_SIZE_SCALE, MAX_TAIL_SIZE_SCALE);

        velocitySizeIntensity = clamp01(velocitySizeIntensity);
        opacity = clamp01(opacity);

        colorR = clamp01(colorR);
        colorG = clamp01(colorG);
        colorB = clamp01(colorB);
        tailColorR = clamp01(tailColorR);
        tailColorG = clamp01(tailColorG);
        tailColorB = clamp01(tailColorB);

        // Anything that is not additive -- missing, misspelt, hand-edited -- is the default.
        blendMode = blendMode != null && blendMode.equalsIgnoreCase(ADDITIVE) ? ADDITIVE : ALPHA;
        if (spritePath == null) spritePath = "";
    }

    public boolean additive() {
        return Objects.equals(ADDITIVE, blendMode);
    }

    public void save() {
        FILE.save(this);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return clamp(value, 0.0f, 1.0f);
    }
}
