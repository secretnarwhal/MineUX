package com.mineux.visuals.fx;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The three hit effect slots and how they combine, with nothing of the game's in it.
 * <p>
 * Each slot is {@code off}, {@code vanilla} or one of {@link Fx}'s bursts. A hit of yours is a
 * crit or it is not:
 * <ul>
 *   <li>a crit plays the every-hit slot and the crit slot;</li>
 *   <li>any other hit plays the every-hit slot and the non-crit slot.</li>
 * </ul>
 * Only a hit's own slot (crit or non-crit) decides the game's own particles for that hit: kept
 * when it is {@code vanilla}, left out for anything else, {@code off} included. The game's own
 * are the crit stars on a crit and the enchanted-hit sparkles a sharpened weapon puts on any
 * hit. The every-hit slot only ever adds: the game draws nothing of its own for a plain hit, so
 * there {@code off} and {@code vanilla} are the same.
 */
final class FxSlots {

    static final String OFF = "off";
    static final String VANILLA = "vanilla";

    /** off, vanilla, then the bursts in {@link Fx}'s order: a burst's index is its place here less two. */
    static final List<String> EFFECTS =
        Arrays.asList(OFF, VANILLA, "crit", "magic", "flame", "soul", "heart", "totem", "spark", "rod");
    private static final int FIRST_BURST = 2;

    /** The sounds, {@code off} first; {@link Fx} plays them by place. */
    static final List<String> SOUNDS = Arrays.asList("off", "ding", "bell", "pling", "chime", "crit", "levelup", "thunder");

    /** The kill effects, {@code off} first. */
    static final List<String> KILLS = Arrays.asList("off", "lightning", "totem", "flame", "soul");

    private FxSlots() {}

    static int soundIndex(String name) {
        return SOUNDS.indexOf(name);
    }

    static int killIndex(String name) {
        return KILLS.indexOf(name);
    }

    static int effectIndex(String name) {
        return EFFECTS.indexOf(name);
    }

    /** The burst a slot plays, as an index into {@link Fx}'s particles; -1 for off, vanilla or a name it does not know. */
    static int burst(String slot) {
        int index = effectIndex(slot);
        if (index < FIRST_BURST) return -1;
        return index - FIRST_BURST;
    }

    /** The slot that is the hit's own: the crit slot for a crit, the non-crit slot for any other. */
    static String own(boolean crit, String critSlot, String nonCritSlot) {
        if (crit) return critSlot;
        return nonCritSlot;
    }

    /** Whether the game's own particles for a hit of yours stay: only when its own slot is vanilla. */
    static boolean keepsGame(boolean crit, String critSlot, String nonCritSlot) {
        return Objects.equals(VANILLA, own(crit, critSlot, nonCritSlot));
    }

    /** A slot as stored, or the fallback when it is not one of {@link #EFFECTS}. */
    static String valid(String slot, String fallback) {
        if (effectIndex(slot) < 0) return fallback;
        return slot;
    }
}
