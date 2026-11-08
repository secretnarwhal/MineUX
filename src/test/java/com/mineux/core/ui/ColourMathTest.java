
package com.mineux.core.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The colour picker's sums: every RGB colour survives a trip through hue, saturation and value,
 * the text a colour setting holds reads and writes back the same, and a drag past a strip's end
 * stays at the end.
 */
class ColourMathTest {

    @Test
    void everyColourRoundTripsThroughHsv() {
        float[] hsv = new float[3];
        // Every 3rd step of each channel, ends included: 86^3 colours.
        for (int r = 0; r <= 255; r += r == 255 ? 1 : Math.min(3, 255 - r)) {
            for (int g = 0; g <= 255; g += g == 255 ? 1 : Math.min(3, 255 - g)) {
                for (int b = 0; b <= 255; b += b == 255 ? 1 : Math.min(3, 255 - b)) {
                    int rgb = (r << 16) | (g << 8) | b;
                    ColourMath.toHsv(rgb, hsv);
                    assertTrue(hsv[0] >= 0f && hsv[0] < 1f, "hue in range for " + Integer.toHexString(rgb));
                    assertEquals(rgb, ColourMath.hsv(hsv[0], hsv[1], hsv[2]), "round trip of " + Integer.toHexString(rgb));
                }
            }
        }
    }

    @Test
    void primariesAndCorners() {
        assertEquals(0xFF0000, ColourMath.hsv(0f, 1f, 1f));
        assertEquals(0xFF0000, ColourMath.hsv(1f, 1f, 1f), "a hue of 1 is red again");
        assertEquals(0xFFFF00, ColourMath.hsv(1f / 6f, 1f, 1f));
        assertEquals(0x00FF00, ColourMath.hsv(2f / 6f, 1f, 1f));
        assertEquals(0x00FFFF, ColourMath.hsv(3f / 6f, 1f, 1f));
        assertEquals(0x0000FF, ColourMath.hsv(4f / 6f, 1f, 1f));
        assertEquals(0xFF00FF, ColourMath.hsv(5f / 6f, 1f, 1f));
        assertEquals(0xFFFFFF, ColourMath.hsv(0.4f, 0f, 1f), "no saturation is grey, whatever the hue");
        assertEquals(0x000000, ColourMath.hsv(0.4f, 1f, 0f), "no value is black");
        assertEquals(0xFFFFFF, ColourMath.hsv(0.3f, -0.5f, 2f), "out-of-range saturation and value are held");
    }

    @Test
    void hsvGivesRgbAndBack() {
        float[] hsv = new float[3];
        for (int h = 0; h < 360; h += 7) {
            for (int s = 0; s <= 10; s++) {
                for (int v = 0; v <= 10; v++) {
                    int rgb = ColourMath.hsv(h / 360f, s / 10f, v / 10f);
                    ColourMath.toHsv(rgb, hsv);
                    assertEquals(rgb, ColourMath.hsv(hsv[0], hsv[1], hsv[2]));
                    assertEquals(v / 10f, hsv[2], 1f / 255f + 1e-4f, "value back");
                }
            }
        }
    }

    @Test
    void hueHeldThroughGreyAndBlack() {
        float[] hsv = {0.55f, 0.8f, 0.9f};
        ColourMath.keepHue(0x808080, hsv);
        assertEquals(0.55f, hsv[0], 1e-6f, "a grey keeps the hue");
        assertEquals(0f, hsv[1], 1e-6f);
        hsv[1] = 0.7f;
        ColourMath.keepHue(0x000000, hsv);
        assertEquals(0.55f, hsv[0], 1e-6f, "black keeps the hue");
        assertEquals(0.7f, hsv[1], 1e-6f, "black keeps the saturation");
        assertEquals(0f, hsv[2], 1e-6f);
        ColourMath.keepHue(0x00FF00, hsv);
        assertEquals(1f / 3f, hsv[0], 1e-5f, "a real colour sets the hue");
    }

    @Test
    void parsesTheSettingFormats() {
        assertEquals(0xFF78A7FFL, ColourMath.parse("#78a7ff"));
        assertEquals(0xFF78A7FFL, ColourMath.parse("78A7FF"));
        assertEquals(0xFF78A7FFL, ColourMath.parse("  #78a7FF "));
        assertEquals(0x8078A7FFL, ColourMath.parse("#8078a7ff"));
        assertEquals(0x00000000L, ColourMath.parse("#00000000"));
        assertEquals(0xFFFFFFFFL, ColourMath.parse("#ffffffff"));
        assertEquals(0xFFFF00AAL, ColourMath.parse("#f0a"));
        assertEquals(-1L, ColourMath.parse("esp"));
        assertEquals(-1L, ColourMath.parse("health"));
        assertEquals(-1L, ColourMath.parse("#12345"));
        assertEquals(-1L, ColourMath.parse("#12345g"));
        assertEquals(-1L, ColourMath.parse(""));
        assertEquals(-1L, ColourMath.parse(null));
        assertEquals(-1L, ColourMath.parse("#"));
    }

    @Test
    void alphaOnlyWithEightDigits() {
        assertTrue(ColourMath.hasAlpha("#8078a7ff"));
        assertTrue(ColourMath.hasAlpha("8078a7ff"));
        assertFalse(ColourMath.hasAlpha("#78a7ff"));
        assertFalse(ColourMath.hasAlpha("#fff"));
        assertFalse(ColourMath.hasAlpha("esp"));
        assertFalse(ColourMath.hasAlpha(null));
    }

    @Test
    void writesTheFormatItRead() {
        assertEquals("#78a7ff", ColourMath.hex(0xFF78A7FF, false));
        assertEquals("#78a7ff", ColourMath.hex(0x0078A7FF, false), "no alpha written for a six-digit setting");
        assertEquals("#000000", ColourMath.hex(0, false));
        assertEquals("#8078a7ff", ColourMath.hex(0x8078A7FF, true));
        assertEquals("#00000000", ColourMath.hex(0, true));
        assertEquals("#ffffffff", ColourMath.hex(0xFFFFFFFF, true));
        for (String text : new String[]{"#78a7ff", "#000000", "#ffffff", "#0a0b0c"}) {
            assertEquals(text, ColourMath.hex((int) ColourMath.parse(text), ColourMath.hasAlpha(text)));
        }
        for (String text : new String[]{"#8078a7ff", "#00000000", "#ffffffff", "#01020304"}) {
            assertEquals(text, ColourMath.hex((int) ColourMath.parse(text), ColourMath.hasAlpha(text)));
        }
    }

    @Test
    void channels() {
        int c = 0x80123456;
        assertEquals(0x80, ColourMath.channel(c, 24));
        assertEquals(0x12, ColourMath.channel(c, 16));
        assertEquals(0x34, ColourMath.channel(c, 8));
        assertEquals(0x56, ColourMath.channel(c, 0));
        assertEquals(0x80FF3456, ColourMath.withChannel(c, 16, 255));
        assertEquals(0x80120056, ColourMath.withChannel(c, 8, -40), "held at 0");
        assertEquals(0xFF123456, ColourMath.withChannel(c, 24, 999), "held at 255");
    }

    @Test
    void dragHeldAtTheEnds() {
        assertEquals(0.0, ColourMath.along(-50, 10, 100));
        assertEquals(0.0, ColourMath.along(10, 10, 100));
        assertEquals(0.5, ColourMath.along(60, 10, 100), 1e-9);
        assertEquals(1.0, ColourMath.along(110, 10, 100));
        assertEquals(1.0, ColourMath.along(1e6, 10, 100));
        assertEquals(0.0, ColourMath.along(5, 10, 0), "a strip with no length");
        // Monotonic: moving the cursor right never moves the pick left, so nothing jumps at the edges.
        double last = -1;
        for (double x = -20; x <= 130; x += 0.25) {
            double t = ColourMath.along(x, 10, 100);
            assertTrue(t >= last);
            last = t;
        }
    }
}
