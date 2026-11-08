package com.mineux.visuals.cosmetics.wings;

import java.util.Arrays;
import java.util.List;

/** Every pair of wings, by the name the settings use, and how big each is drawn. */
public final class Wings {

    public static final List<String> NAMES = Arrays.asList(
        "off", "angel", "dragon", "butterfly", "phoenix", "crystal", "cyber", "fairy", "classic");

    private static final double[] UNIT = {0.0, 0.6, 0.64, 1.05, 0.42, 0.62, 0.52, 0.66, 1.0};

    private Wings() {}

    public static double unit(int index) {
        if (index <= 0 || index >= UNIT.length) return 1.0;
        return UNIT[index];
    }

    /** One wing, the side the builder's frame was set to. */
    public static void build(int index, WingBuilder b) {
        if (index == 1) WingStyles.angel(b);
        else if (index == 2) WingStyles.dragon(b);
        else if (index == 3) WingStyles.butterfly(b);
        else if (index == 4) WingStyles.phoenix(b);
        else if (index == 5) WingStyles.crystal(b);
        else if (index == 6) WingStyles.cyber(b);
        else if (index == 7) WingStyles.fairy(b);
        else if (index == 8) WingStyles.classic(b);
    }
}
