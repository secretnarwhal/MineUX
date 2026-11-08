package com.mineux.visuals.cosmetics.kill;

import com.mineux.visuals.cosmetics.Cosmetic;

import java.util.Arrays;
import java.util.List;

/** Every kill effect, by the name the settings use. */
public final class KillStyles {

    public static final List<String> NAMES = Arrays.asList(
        "off", "shockwave", "shatter", "strike", "blackhole", "phoenix", "ascend");

    public static final int SHOCKWAVE = 1;

    /** Own colours, primary then secondary, for strike, black hole, phoenix and ascend. */
    private static final int[] NATURAL = {0x6F9BFF, 0xE6F2FF, 0xA55CFF, 0xFFC078, 0xFF6A1A, 0xFFF0A0, 0xFFC94D, 0xFFF6DC};

    private KillStyles() {}

    public static int natural(int index, int which) {
        if (index < 3 || (index - 3) * 2 + 1 >= NATURAL.length) return 0;
        return NATURAL[(index - 3) * 2 + which];
    }

    public static Cosmetic create(int index, KillParams p) {
        if (index == 1) return new Shockwave(p);
        if (index == 2) return new Shatter(p);
        if (index == 3) return new LightningStrike(p);
        if (index == 4) return new BlackHole(p);
        if (index == 5) return new Phoenix(p);
        if (index == 6) return new Ascend(p);
        return null;
    }
}
