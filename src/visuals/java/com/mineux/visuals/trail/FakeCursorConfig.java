package com.mineux.visuals.trail;

import com.mineux.core.config.ConfigFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Replaces what the player visually sees at the mouse position with a
 * custom sprite, rather than just overlaying one on top of the real
 * cursor -- this is meant to be what the player actually sees.
 * <p>
 * Minecraft's cursor-shape API (com.mojang.blaze3d.platform.cursor.CursorType)
 * only wraps GLFW's built-in system shapes (arrow, hand, ibeam, ...); there's
 * no public way to hand it an arbitrary custom image, so a true OS-level
 * cursor swap isn't available. Instead {@link FakeCursorRenderer} hides the
 * real cursor (GLFW_CURSOR_HIDDEN) and draws this sprite at the tracked mouse
 * position every frame -- visually equivalent to a real replacement for the
 * player, even though technically it's "hidden real cursor + drawn fake
 * one" rather than an OS cursor swap.
 * <p>
 * Config file: config/mineux/cursor.json (a
 * config of its own, not part of trail.json, so it is one entry, cfg=cursor, among the per-workstation configs).
 */
public final class FakeCursorConfig implements ConfigFile.Backed {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/trail");

    private static final ConfigFile<FakeCursorConfig> FILE =
        new ConfigFile<>(FakeCursorConfig.class, "cursor.json", FakeCursorConfig::new, LOGGER);

    private static FakeCursorConfig instance;

    public static final int MIN_SIZE_PX = 4;
    public static final int MAX_SIZE_PX = 128;

    public boolean enabled = false;

    public int sizePx = 24;

    public float colorR = 1.0f, colorG = 1.0f, colorB = 1.0f;
    /** Clamped to [0, 1] by {@link #sanitise()}. */
    public float opacity = 1.0f;

    /**
     * Absolute or game-directory-relative path to a custom cursor image.
     * Empty = a procedurally-drawn arrow, anchored at its tip (top-left of
     * the sprite), matching where a real cursor's hotspot usually sits.
     */
    public String spritePath = "";

    /** Clamps anything a hand-edited file could have put out of range. */
    public void sanitise() {
        sizePx = Math.max(MIN_SIZE_PX, Math.min(MAX_SIZE_PX, sizePx));
        colorR = clamp(colorR);
        colorG = clamp(colorG);
        colorB = clamp(colorB);
        opacity = clamp(opacity);
        if (spritePath == null) spritePath = "";
    }

    public static synchronized FakeCursorConfig get() {
        if (instance == null) instance = FILE.load();
        return instance;
    }

    @Override
    public void ensureDefaults() {
        sanitise();
    }

    public void save() {
        FILE.save(this);
    }

    private static float clamp(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }
}
