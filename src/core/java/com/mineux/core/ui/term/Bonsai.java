package com.mineux.core.ui.term;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * cbonsai, more or less: a trunk that wanders up out of a pot, throws shoots left and right,
 * and ends every branch in leaves. Grown once into a list of glyphs in the order they were
 * laid, then revealed a few per tick so it grows on screen.
 */
final class Bonsai {

    static final int COLS = 48;
    static final int ROWS = 24;

    static final String[] POT = {
        ":___________./~~~\\.___________:",
        " \\                           / ",
        "  \\_________________________/  ",
        "  (_)                     (_)  ",
    };

    private static final int TRUNK = 0, LEFT = 1, RIGHT = 2, DYING = 3, DEAD = 4;

    private static final int WOOD = 0xFFB07A3C;
    private static final int WOOD_DARK = 0xFF7A5028;
    private static final int[] LEAF = {0xFF5AF78E, 0xFF3FCF6E, 0xFF9BE35B, 0xFFEFC648};

    record Glyph(int col, int row, String text, int color) {}

    final List<Glyph> glyphs = new ArrayList<>();
    private Random random = new Random();
    private int shown;
    private int branches;

    void grow() {
        glyphs.clear();
        shown = 0;
        branches = 0;
        random = new Random();
        branch(COLS / 2, ROWS - 1, TRUNK, 30 + random.nextInt(8));
    }

    /** Reveals a few more glyphs; call once a tick. */
    void tick() {
        if (shown < glyphs.size()) shown = Math.min(glyphs.size(), shown + 4);
    }

    int shown() {
        return shown;
    }

    boolean done() {
        return shown >= glyphs.size();
    }

    private void branch(int x, int y, int type, int life) {
        branches++;
        int maxLife = life;
        int shootCooldown = 6;

        while (life > 0) {
            life--;
            int age = maxLife - life;

            int dx, dy;
            switch (type) {
                case TRUNK -> {
                    dy = age <= 2 || random.nextInt(10) < 7 ? -1 : 0;
                    dx = random.nextInt(5) - 2;
                    if (dx == 2) dx = 1;
                    if (dx == -2) dx = -1;
                    if (age <= 2) dx = 0;
                }
                case LEFT -> {
                    dy = random.nextInt(10) < 3 ? -1 : random.nextInt(10) < 1 ? 1 : 0;
                    dx = random.nextInt(3) - 2;
                }
                case RIGHT -> {
                    dy = random.nextInt(10) < 3 ? -1 : random.nextInt(10) < 1 ? 1 : 0;
                    dx = random.nextInt(3);
                }
                case DYING -> {
                    dy = random.nextInt(10) < 3 ? -1 : random.nextInt(10) < 2 ? 1 : 0;
                    dx = random.nextInt(5) - 2;
                }
                default -> {
                    dy = random.nextInt(3) - 1;
                    dx = random.nextInt(3) - 1;
                }
            }

            // Keep the canopy off the pot and inside the box.
            if (y + dy >= ROWS - 1 && type != TRUNK) dy = -1;
            x = Math.max(1, Math.min(COLS - 3, x + dx));
            y = Math.max(0, Math.min(ROWS - 1, y + dy));

            if (type == TRUNK && life < 4) {
                branch(x, y, DYING, life + 2);
                return;
            }

            if ((type == LEFT || type == RIGHT) && life < 3) {
                branch(x, y, DYING, life + 3);
                return;
            }

            if (type == TRUNK && --shootCooldown <= 0 && random.nextInt(3) == 0 && branches < 40) {
                shootCooldown = 3 + random.nextInt(3);
                int side = random.nextBoolean() ? LEFT : RIGHT;
                branch(x, y, side, life + random.nextInt(5) - 2);
            }

            if (type == DYING && random.nextInt(8) == 0 && branches < 60 && life > 2) {
                branch(x, y, DEAD, 2 + random.nextInt(3));
            }

            glyphs.add(new Glyph(x, y, glyph(type, dx, dy, life), color(type)));
        }
    }

    private String glyph(int type, int dx, int dy, int life) {
        switch (type) {
            case TRUNK -> {
                if (dy == 0) return "/~";
                if (dx < 0) return "\\|";
                if (dx > 0) return "|/";
                return "/|\\";
            }
            case LEFT -> {
                if (dy > 0) return "/";
                if (dy == 0) return "\\_";
                if (dx < 0) return "\\|";
                if (dx > 0) return "/";
                return "/|";
            }
            case RIGHT -> {
                if (dy > 0) return "\\";
                if (dy == 0) return "_/";
                if (dx > 0) return "|/";
                if (dx < 0) return "\\";
                return "|\\";
            }
            default -> {
                return random.nextInt(6) == 0 ? "&&" : "&";
            }
        }
    }

    private int color(int type) {
        if (type == TRUNK || type == LEFT || type == RIGHT) return random.nextBoolean() ? WOOD : WOOD_DARK;
        return LEAF[random.nextInt(LEAF.length)];
    }
}
