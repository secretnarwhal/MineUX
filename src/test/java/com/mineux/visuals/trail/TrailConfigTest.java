package com.mineux.visuals.trail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The repair pass over trail.json.
 * <p>
 * Every numeric field here is reachable by hand, and every slider on the panel promises a
 * range -- so a hand-edited value outside it has to be pulled back before anything reads it,
 * the same way the zoom's ranges are. The blend
 * mode enum and the sprite path string get the same treatment a missing key would: GSON leaves
 * them null rather than refusing to load, and a null read anywhere downstream would be a crash
 * rather than a setting nobody configured yet.
 */
class TrailConfigTest {

    @Test
    void outOfRangeNumbersAreClampedIntoTheSliderRange() {
        TrailConfig config = new TrailConfig();

        config.lifetimeMs = 0;
        config.spriteSizePx = 0;
        config.stampSpacingPx = 0;
        config.tailSizeScale = -5.0f;
        config.velocitySizeIntensity = -1.0f;
        config.opacity = 2.0f;
        config.colorR = -1.0f;
        config.colorG = 5.0f;
        config.tailColorB = 5.0f;
        config.ensureDefaults();

        assertEquals(TrailConfig.MIN_LIFETIME_MS, config.lifetimeMs, "a hand-edited zero survived");
        assertEquals(TrailConfig.MIN_SPRITE_SIZE_PX, config.spriteSizePx);
        assertEquals(TrailConfig.MIN_STAMP_SPACING_PX, config.stampSpacingPx);
        assertEquals(TrailConfig.MIN_TAIL_SIZE_SCALE, config.tailSizeScale);
        assertEquals(0.0f, config.velocitySizeIntensity);
        assertEquals(1.0f, config.opacity);
        assertEquals(0.0f, config.colorR);
        assertEquals(1.0f, config.colorG);
        assertEquals(1.0f, config.tailColorB);

        config.lifetimeMs = 5000;
        config.spriteSizePx = 5000;
        config.stampSpacingPx = 5000;
        config.tailSizeScale = 50.0f;
        config.ensureDefaults();

        assertEquals(TrailConfig.MAX_LIFETIME_MS, config.lifetimeMs, "a runaway value off the slider survived");
        assertEquals(TrailConfig.MAX_SPRITE_SIZE_PX, config.spriteSizePx);
        assertEquals(TrailConfig.MAX_STAMP_SPACING_PX, config.stampSpacingPx);
        assertEquals(TrailConfig.MAX_TAIL_SIZE_SCALE, config.tailSizeScale);
    }

    @Test
    void aNullStringIsDefaulted() {
        TrailConfig config = new TrailConfig();

        config.blendMode = null;
        config.spritePath = null;
        config.ensureDefaults();

        assertEquals(TrailConfig.ALPHA, config.blendMode);
        assertEquals("", config.spritePath);
    }

    /**
     * The blend mode was an enum until it had to survive obfuscation, which renames enum
     * constants and with them the names Gson reads and writes. The strings it became are those
     * same names, so a file written by the enum reads back as the setting it held -- and a value
     * nobody could have written falls back to the default rather than surviving as a third mode.
     */
    @Test
    void theBlendModeReadsTheNamesTheEnumUsedToWrite() {
        com.google.gson.Gson gson = new com.google.gson.Gson();

        TrailConfig additive = gson.fromJson("{\"blendMode\": \"ADDITIVE\"}", TrailConfig.class);
        additive.ensureDefaults();
        assertEquals(TrailConfig.ADDITIVE, additive.blendMode);
        assertTrue(additive.additive());

        TrailConfig alpha = gson.fromJson("{\"blendMode\": \"ALPHA\"}", TrailConfig.class);
        alpha.ensureDefaults();
        assertEquals(TrailConfig.ALPHA, alpha.blendMode);
        assertFalse(alpha.additive());

        TrailConfig handEdited = gson.fromJson("{\"blendMode\": \"additive\"}", TrailConfig.class);
        handEdited.ensureDefaults();
        assertEquals(TrailConfig.ADDITIVE, handEdited.blendMode);

        TrailConfig nonsense = gson.fromJson("{\"blendMode\": \"MULTIPLY\"}", TrailConfig.class);
        nonsense.ensureDefaults();
        assertEquals(TrailConfig.ALPHA, nonsense.blendMode);

        assertTrue(gson.toJson(additive).contains("\"blendMode\":\"ADDITIVE\""),
            "the file is written in the enum's old spelling");
    }

    /** The fake cursor's own file, put right on load as the trail's is. */
    @Test
    void theFakeCursorIsSanitisedAsWellAsNullGuarded() {
        FakeCursorConfig config = new FakeCursorConfig();

        config.sizePx = -10;
        config.opacity = 5.0f;
        config.colorR = -2.0f;
        config.spritePath = null;
        config.ensureDefaults();

        assertTrue(config.sizePx >= 1, "a non-positive size survived: " + config.sizePx);
        assertEquals(1.0f, config.opacity);
        assertEquals(0.0f, config.colorR);
        assertEquals("", config.spritePath);
    }

    @Test
    void theDefaultsAreTheOnesTheFileIsWrittenWith() {
        TrailConfig config = new TrailConfig();
        config.ensureDefaults();

        assertFalse(config.enabled, "the trail arrived switched on: every visual module starts off");
        assertFalse(new FakeCursorConfig().enabled, "hiding the real cursor is opt-in");
        assertEquals(TrailConfig.ALPHA, config.blendMode);
        assertEquals("", config.spritePath);
        assertEquals(150, config.lifetimeMs);
        assertEquals(11, config.spriteSizePx);
        assertEquals(1, config.stampSpacingPx);
        assertEquals(0.7f, config.tailSizeScale);
        assertEquals(0.8f, config.opacity);
        assertEquals(24, new FakeCursorConfig().sizePx);
    }
}
