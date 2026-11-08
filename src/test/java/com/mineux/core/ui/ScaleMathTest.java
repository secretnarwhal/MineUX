package com.mineux.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The desk scale's sums ({@link ScaleMath}): the setting read and kept, the scale picked for a
 * screen, the desk covering the screen, every desk unit on whole pixels, and -- what matters to
 * a player -- the mouse landing on the desk unit drawn under it, at every GUI scale and desk
 * scale on screens that are and are not a whole number of GUI units across.
 */
class ScaleMathTest {

    /** Screens to try: common ones, and ones no GUI scale divides. */
    private static final int[][] SCREENS = {
        {1920, 1080}, {2560, 1440}, {3840, 2160}, {1366, 768}, {1280, 720}, {1600, 900}, {1921, 1083}, {854, 480}, {3440, 1440},
    };

    /** The game's own GUI size along a side: rounded up, as {@code Window} has it. */
    private static int guiSize(int fb, int guiScale) {
        return (fb + guiScale - 1) / guiScale;
    }

    /** The game's largest GUI scale for a screen (auto), as {@code Window#calculateScale} has it. */
    private static int mostGuiScale(int w, int h) {
        int s = 1;
        while (s < w && s < h && w / (s + 1) >= 320 && h / (s + 1) >= 240) s++;
        return s;
    }

    @Test
    void theSettingReadsAndIsKeptAsWritten() {
        assertEquals(0, ScaleMath.parse("auto"));
        assertEquals(0, ScaleMath.parse(" AUTO "));
        for (int n = 1; n <= ScaleMath.MOST; n++) assertEquals(n, ScaleMath.parse(String.valueOf(n)));
        assertEquals(ScaleMath.DEFAULT, ScaleMath.parse(null));
        assertEquals(ScaleMath.DEFAULT, ScaleMath.parse(""));
        assertEquals(ScaleMath.DEFAULT, ScaleMath.parse("5"));
        assertEquals(ScaleMath.DEFAULT, ScaleMath.parse("0"));
        assertEquals(ScaleMath.DEFAULT, ScaleMath.parse("2.5"));
        assertEquals(ScaleMath.DEFAULT, ScaleMath.parse("big"));

        assertEquals("auto", ScaleMath.normal("Auto"));
        assertEquals("3", ScaleMath.normal(" 3"));
        assertNull(ScaleMath.normal("7"));
        assertNull(ScaleMath.normal("two"));
        assertNull(ScaleMath.normal(null));
    }

    @Test
    void autoGoesByTheScreenAsTwoOn1080p() {
        assertEquals(2, ScaleMath.pick(0, 1920, 1080));
        assertEquals(3, ScaleMath.pick(0, 2560, 1440));
        assertEquals(4, ScaleMath.pick(0, 3840, 2160));
        assertEquals(1, ScaleMath.pick(0, 1280, 720));
        assertEquals(2, ScaleMath.pick(0, 3440, 1440 * 3 / 4));
    }

    @Test
    void aSetScaleIsKeptUnlessTheWindowIsTooSmallForIt() {
        for (int n = 1; n <= ScaleMath.MOST; n++) assertEquals(n, ScaleMath.pick(n, 1920, 1080));
        // 4 on 720p would leave a 320x180 desk: capped as vanilla caps its own GUI scale.
        assertEquals(3, ScaleMath.pick(4, 1280, 720));
        assertEquals(1, ScaleMath.pick(4, 400, 300));
        assertEquals(1, ScaleMath.pick(2, 10, 10));
        assertEquals(1, ScaleMath.pick(0, 0, 0));
    }

    @Test
    void theDeskCoversTheScreenAndNoMore() {
        for (int[] screen : SCREENS) {
            for (int t = 1; t <= ScaleMath.MOST; t++) {
                for (int side = 0; side < 2; side++) {
                    int fb = screen[side];
                    int size = ScaleMath.size(fb, t);
                    assertTrue(size * t >= fb, "covers " + fb + " at " + t);
                    assertTrue((size - 1) * t < fb, "no spare unit for " + fb + " at " + t);
                }
            }
        }
        assertEquals(960, ScaleMath.size(1920, 2));
        assertEquals(640, ScaleMath.size(1920, 3));
        assertEquals(456, ScaleMath.size(1366, 3));
    }

    @Test
    void sameScalesChangeNothing() {
        for (int s = 1; s <= 4; s++) {
            assertEquals(1f, ScaleMath.factor(s, s));
            int gui = guiSize(1920, s);
            for (int x = 0; x < gui; x += 7) assertEquals(x, ScaleMath.toDesk(x, 1920, gui, s), 1e-9);
        }
    }

    @Test
    void everyDeskUnitIsWholePixels() {
        // Drawn: a desk position d goes through the pose (times factor) into GUI units, and the
        // game puts a GUI unit on guiScale pixels. A whole d must land on a whole pixel, d * t.
        for (int t = 1; t <= ScaleMath.MOST; t++) {
            for (int s = 1; s <= 6; s++) {
                float f = ScaleMath.factor(t, s);
                for (int d = 0; d <= 2000; d++) {
                    double px = d * (double) f * s;
                    assertEquals(d * t, px, 1e-3 * Math.max(1, d), "desk " + d + " at desk " + t + ", gui " + s);
                }
            }
        }
    }

    @Test
    void theMouseLandsOnTheDeskUnitDrawnUnderIt() {
        for (int[] screen : SCREENS) {
            int most = mostGuiScale(screen[0], screen[1]);
            for (int s = 1; s <= most; s++) {
                for (int t = 1; t <= ScaleMath.MOST; t++) {
                    for (int side = 0; side < 2; side++) {
                        int fb = screen[side];
                        int gui = guiSize(fb, s);
                        for (int px = 0; px < fb; px++) {
                            // Where the game says the cursor is, on pixel px (window as big as its framebuffer).
                            double guiPos = (px + 0.5) * gui / (double) fb;
                            double desk = ScaleMath.toDesk(guiPos, fb, gui, t);
                            // The desk unit drawn over pixel px is px / t.
                            assertEquals(px / t, ScaleMath.deskPixel(desk), "pixel " + px + " of " + fb + ", gui " + s + ", desk " + t);
                            // And back.
                            assertEquals(guiPos, ScaleMath.toGui(desk, fb, gui, t), 1e-9);
                        }
                        // The cursor on the last pixel is still on the desk.
                        double last = ScaleMath.toDesk((fb - 0.5) * gui / (double) fb, fb, gui, t);
                        assertTrue(ScaleMath.deskPixel(last) < ScaleMath.size(fb, t));
                    }
                }
            }
        }
    }

    @Test
    void aHiDpiWindowMapsTheSameWay() {
        // A window half its framebuffer's size (a retina screen): the game's mouse is screen
        // position times GUI size over window size; the desk unit under it is still px / t.
        int fb = 2880, window = 1440, s = 2, t = 3;
        int gui = guiSize(fb, s);
        for (int wx = 0; wx < window; wx++) {
            double guiPos = (wx + 0.25) * gui / (double) window;
            int px = (int) Math.floor((wx + 0.25) * fb / window);
            assertEquals(px / t, ScaleMath.deskPixel(ScaleMath.toDesk(guiPos, fb, gui, t)));
        }
    }

    @Test
    void wholeDeskPositionsSurviveRoundingNoise() {
        assertEquals(2, ScaleMath.deskPixel(2.0 - 1e-9));
        assertEquals(1, ScaleMath.deskPixel(1.999));
        assertEquals(0, ScaleMath.deskPixel(0));
        assertEquals(0 - 1, ScaleMath.deskPixel(-0.5));
    }
}
