package com.mineux.screens.displays;

/**
 * How a screen keeps its place: ints, not an enum (an obfuscator a host may run over this code, such as Skidfuscator, renames constants).
 */
public final class Anchor {

    /** Stays at its spot in the world. */
    public static final int WORLD = 0;
    /** Stays in front of the player's face, following movement and head rotation. */
    public static final int FOLLOW_VIEW = 1;
    /** Moves along with the player but keeps its orientation when the player turns. */
    public static final int FOLLOW_POSITION = 2;
    public static final int COUNT = 3;

    private static final String[] TITLES = {"World", "Follow view", "Follow position"};
    private static final String[] HELP = {
        "Stays at its spot in the world",
        "Stays in front of you, turning when you turn",
        "Moves with you, but doesn't turn when you turn"
    };

    private Anchor() {}

    public static String title(int mode) {
        return TITLES[Math.clamp(mode, 0, COUNT - 1)];
    }

    public static String help(int mode) {
        return HELP[Math.clamp(mode, 0, COUNT - 1)];
    }
}
