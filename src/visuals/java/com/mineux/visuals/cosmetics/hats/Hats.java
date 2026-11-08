package com.mineux.visuals.cosmetics.hats;

import com.mineux.visuals.cosmetics.Cosmetic;

import java.util.Arrays;
import java.util.List;

/** Every hat, by the name the settings use: ears, horns, halos, crowns and the china hat. */
public final class Hats {

    public static final List<String> NAMES = Arrays.asList(
        "off", "catears", "foxears", "bunnyears", "bearears", "demonhorns", "stag", "reindeer", "unicorn", "viking",
        "halo", "doublehalo", "thornhalo", "orbs", "runichalo", "crown", "spikedcrown", "laurel", "tiara",
        "flamecrown", "icecrown", "floatingcrown", "chinahat");

    private Hats() {}

    /** The hat called {@code NAMES.get(index)}, or null for off or a name it does not know. */
    public static Cosmetic create(int index, HatParams p) {
        if (index == 1) return new CatEars(p);
        if (index == 2) return new FoxEars(p);
        if (index == 3) return new BunnyEars(p);
        if (index == 4) return new BearEars(p);
        if (index == 5) return new DemonHorns(p);
        if (index == 6) return new StagAntlers(p);
        if (index == 7) return new ReindeerAntlers(p);
        if (index == 8) return new UnicornHorn(p);
        if (index == 9) return new VikingHorns(p);
        if (index == 10) return new ClassicRing(p);
        if (index == 11) return new DoubleRing(p);
        if (index == 12) return new ThornHalo(p);
        if (index == 13) return new OrbitingOrbs(p);
        if (index == 14) return new RunicHalo(p);
        if (index == 15) return new RoyalCrown(p);
        if (index == 16) return new SpikedCrown(p);
        if (index == 17) return new LaurelWreath(p);
        if (index == 18) return new Tiara(p);
        if (index == 19) return new FlameCrown(p);
        if (index == 20) return new IceCrown(p);
        if (index == 21) return new FloatingCrown(p);
        if (index == 22) return new ChinaHatCosmetic(p);
        return null;
    }
}
