package com.mineux.visuals.cosmetics.aura;

import com.mineux.visuals.cosmetics.Cosmetic;

import java.util.Arrays;
import java.util.List;

/**
 * Every aura, by the name the settings use: things orbiting you (shards, swords, orbs, runes,
 * rings), an energy sphere, lightning arcs, a rune circle on the ground, and the elements.
 */
public final class Auras {

    public static final List<String> NAMES = Arrays.asList(
        "off", "shards", "swords", "orbs", "runes", "rings", "sphere", "arcs", "runecircle",
        "flame", "frost", "void", "sakura", "storm");

    /** The elements' own colours, primary then secondary, from {@link #FIRST_ELEMENT} on. */
    private static final int[] NATURAL = {
        0xFFC233, 0xFF3B12, 0xC4F1FF, 0x3D8CFF, 0xC77DFF, 0x3A0F7A, 0xFFC8DF, 0xFF5E9E, 0xCFE3FF, 0x56638F};

    public static final int SPHERE = 6;
    public static final int ARCS = 7;
    public static final int RUNE_CIRCLE = 8;
    public static final int FIRST_ELEMENT = 9;

    private Auras() {}

    /** Whether the aura sits at your feet (the ground circle, the elements) rather than your middle. */
    public static boolean grounded(int index) {
        return index >= RUNE_CIRCLE;
    }

    /** An element's own colour: which 0 or 1; 0 when the aura is no element. */
    public static int natural(int index, int which) {
        if (index < FIRST_ELEMENT || (index - FIRST_ELEMENT) * 2 + 1 >= NATURAL.length) return 0;
        return NATURAL[(index - FIRST_ELEMENT) * 2 + which];
    }

    public static Cosmetic orbit(int index, AuraParams p) {
        if (index == 1) return new Shards(p);
        if (index == 2) return new Swords(p);
        if (index == 3) return new Orbs(p);
        if (index == 4) return new Runes(p);
        if (index == 5) return new Rings(p);
        return null;
    }

    public static Cosmetic element(int index, ElementParams p) {
        if (index == FIRST_ELEMENT) return new FlameAura(p);
        if (index == FIRST_ELEMENT + 1) return new FrostAura(p);
        if (index == FIRST_ELEMENT + 2) return new VoidAura(p);
        if (index == FIRST_ELEMENT + 3) return new SakuraAura(p);
        if (index == FIRST_ELEMENT + 4) return new StormAura(p);
        return null;
    }
}
