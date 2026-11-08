package com.mineux.visuals.fx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hit slots: what each choice plays, which slot a hit's game particles answer to, how a
 * tick's packets make a hit a crit or not, and the old single setting carried over unchanged.
 */
class FxSlotsTest {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ---- the slots ----------------------------------------------------------------------------

    @Test
    void offAndVanillaPlayNothingOfOurs() {
        assertEquals(-1, FxSlots.burst("off"));
        assertEquals(-1, FxSlots.burst("vanilla"));
        assertEquals(-1, FxSlots.burst("nonsense"));
        assertEquals(-1, FxSlots.burst(null));
    }

    @Test
    void theBurstsKeepTheirOldOrder() {
        // Fx's particle array is in the old list's order: crit, magic, flame, soul, heart, totem, spark, rod.
        String[] old = {"crit", "magic", "flame", "soul", "heart", "totem", "spark", "rod"};
        for (int i = 0; i < old.length; i++) assertEquals(i, FxSlots.burst(old[i]), old[i]);
    }

    @Test
    void aCritAnswersToTheCritSlotAndAnyOtherHitToTheNonCritSlot() {
        assertEquals("flame", FxSlots.own(true, "flame", "heart"));
        assertEquals("heart", FxSlots.own(false, "flame", "heart"));
    }

    @Test
    void theGameKeepsItsParticlesOnlyWhereTheHitsOwnSlotIsVanilla() {
        assertTrue(FxSlots.keepsGame(true, "vanilla", "off"));
        assertFalse(FxSlots.keepsGame(true, "off", "vanilla"), "off on crits still drew the crit stars");
        assertFalse(FxSlots.keepsGame(true, "flame", "vanilla"), "a crit burst did not replace the stars");
        assertTrue(FxSlots.keepsGame(false, "flame", "vanilla"));
        assertFalse(FxSlots.keepsGame(false, "vanilla", "off"));
        assertFalse(FxSlots.keepsGame(false, "vanilla", "soul"));
    }

    // ---- a tick's hits ------------------------------------------------------------------------

    @Test
    void aHitWithCritStarsIsACritAndPlaysOnce() {
        FxHits hits = new FxHits();
        hits.hit(7);
        assertEquals(0, hits.crit(7), "the first stars should make it a crit");
        assertEquals(1, hits.crit(7), "a second packet of stars played the crit twice");
        assertEquals(1, hits.magic(7), "the sparkles after the stars are a crit's");
        assertTrue(hits.flush().isEmpty(), "a crit was played as a plain hit too");
        assertFalse(hits.any());
    }

    @Test
    void aHitWithNoCritStarsIsPlainAtTheEndOfTheTick() {
        FxHits hits = new FxHits();
        hits.hit(3);
        hits.hit(9);
        assertEquals(0, hits.magic(3), "sparkles with no stars are a plain hit's");
        hits.crit(9);
        List<Integer> plain = hits.flush();
        assertEquals(List.of(3), plain);
        assertEquals(-1, hits.crit(3), "a hit outlived its tick");
    }

    @Test
    void starsOnSomeoneYouDidNotHitAreNotYours() {
        FxHits hits = new FxHits();
        hits.hit(1);
        assertEquals(-1, hits.crit(2));
        assertEquals(-1, hits.magic(2));
    }

    @Test
    void theClientsOwnSparklesAreACritsOnlyRightAfterItsOwnStars() {
        FxHits hits = new FxHits();
        assertFalse(hits.ownCritOf(5, 100));
        hits.ownCrit(5, 100);
        assertTrue(hits.ownCritOf(5, 100));
        assertFalse(hits.ownCritOf(5, 101), "a crit a tick ago made this hit's sparkles a crit's");
        assertFalse(hits.ownCritOf(6, 100));
    }

    // ---- the old setting ----------------------------------------------------------------------

    @Test
    void theOldBurstBecomesEveryHitAndTheGameKeepsItsCrits() {
        FxConfig config = GSON.fromJson("{\"enabled\":true,\"hitParticles\":true,\"particle\":\"flame\",\"count\":20}", FxConfig.class);
        config.ensureDefaults();
        assertEquals("flame", config.everyHit);
        assertEquals("vanilla", config.critHit);
        assertEquals("vanilla", config.nonCritHit);
        assertEquals(20, config.count);
        assertNull(config.hitParticles);
        assertNull(config.particle);
        String saved = GSON.toJson(config);
        assertFalse(saved.contains("hitParticles"), "the old setting was written back: " + saved);
        assertFalse(saved.contains("\"particle\""), "the old setting was written back: " + saved);
    }

    @Test
    void hitsThatBurstNothingBecomeEveryHitOff() {
        FxConfig config = GSON.fromJson("{\"hitParticles\":false,\"particle\":\"heart\"}", FxConfig.class);
        config.ensureDefaults();
        assertEquals("off", config.everyHit);
        assertEquals("vanilla", config.critHit);
        assertEquals("vanilla", config.nonCritHit);
    }

    @Test
    void anOldBurstItDoesNotKnowIsTheOldDefault() {
        FxConfig config = GSON.fromJson("{\"hitParticles\":true,\"particle\":\"glitter\"}", FxConfig.class);
        config.ensureDefaults();
        assertEquals("crit", config.everyHit);
    }

    @Test
    void aNewFileIsLeftAsItIs() {
        FxConfig config = GSON.fromJson("{\"everyHit\":\"off\",\"critHit\":\"soul\",\"nonCritHit\":\"off\"}", FxConfig.class);
        config.ensureDefaults();
        assertEquals("off", config.everyHit);
        assertEquals("soul", config.critHit);
        assertEquals("off", config.nonCritHit);
        // Again: a second load does not migrate anything.
        FxConfig again = GSON.fromJson(GSON.toJson(config), FxConfig.class);
        again.ensureDefaults();
        assertEquals("off", again.everyHit);
        assertEquals("soul", again.critHit);
    }

    @Test
    void aFreshConfigLooksAsTheOldOneDid() {
        FxConfig config = new FxConfig();
        config.ensureDefaults();
        assertEquals("crit", config.everyHit, "the old default was a crit burst on every hit");
        assertEquals("vanilla", config.critHit);
        assertEquals("vanilla", config.nonCritHit);
        assertEquals("off", config.critSound);
        assertEquals("off", config.nonCritSound);
    }

    @Test
    void slotsItDoesNotKnowAreRepaired() {
        FxConfig config = new FxConfig();
        config.everyHit = "x";
        config.critHit = null;
        config.nonCritHit = "y";
        config.critSound = "z";
        config.ensureDefaults();
        assertEquals("crit", config.everyHit);
        assertEquals("vanilla", config.critHit);
        assertEquals("vanilla", config.nonCritHit);
        assertEquals("off", config.critSound);
    }
}
