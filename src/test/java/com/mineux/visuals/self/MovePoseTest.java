package com.mineux.visuals.self;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** The walk styles: no strength leaves the game's pose, and a busy arm keeps the game's swing. */
class MovePoseTest {

    private static MovePose gamePose() {
        MovePose p = new MovePose();
        float[] ys = {0.0F, 0.0F, 2.0F, 2.0F, 12.0F, 12.0F};
        float[] xs = {0.0F, 0.0F, 0.0F - 5.0F, 5.0F, 0.0F - 1.9F, 1.9F};
        for (int i = 0; i < 6; i++) {
            p.x[i] = xs[i];
            p.y[i] = ys[i];
            p.xr[i] = 0.1F * i;
        }
        return p;
    }

    private static MovePose.In walking() {
        MovePose.In in = new MovePose.In();
        in.pos = 3.0F;
        in.spd = 0.8F;
        in.age = 40.0F;
        in.yawRate = 200.0F;
        in.lateral = 2.0F;
        return in;
    }

    @Test
    void noStrengthLeavesTheGamesPose() {
        MovePose p = gamePose();
        MovePose before = gamePose();
        p.apply(MovePose.styleIndex("athletic"), walking(), 0.0F);
        assertArrayEquals(before.xr, p.xr);
        assertArrayEquals(before.y, p.y);
    }

    @Test
    void everyStyleMovesTheLegs() {
        for (String style : MovePose.STYLES) {
            MovePose p = gamePose();
            p.apply(MovePose.styleIndex(style), walking(), 1.0F);
            assertNotEquals(0.4F, p.xr[MovePose.RLEG], style);
        }
    }

    @Test
    void aBusyArmKeepsTheGamesSwing() {
        MovePose p = gamePose();
        MovePose.In in = walking();
        in.armsFree = false;
        in.turnLean = false;
        p.apply(MovePose.styleIndex("natural"), in, 1.0F);
        assertEquals(0.2F, p.xr[MovePose.RARM], 1.0E-6F);
        assertEquals(0.3F, p.xr[MovePose.LARM], 1.0E-6F);
    }

    @Test
    void styleNamesAreTheConfigsChoices() {
        assertEquals(0, MovePose.styleIndex("natural"));
        assertEquals(0 - 1, MovePose.styleIndex("moonwalk"));
    }
}
