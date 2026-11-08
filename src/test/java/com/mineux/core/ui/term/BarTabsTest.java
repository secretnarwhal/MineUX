package com.mineux.core.ui.term;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BarTabsTest {

    private static int sum(int[] a) {
        int s = 0;
        for (int v : a) s += v;
        return s;
    }

    @Test
    void tabsThatFitKeepTheirWidths() {
        int[] natural = {40, 60, 30};
        assertArrayEquals(natural, BarTabs.widths(natural, new int[] {12, 12, 12}, 200));
    }

    @Test
    void theWidestShrinkFirstToOneSharedWidth() {
        int[] out = BarTabs.widths(new int[] {30, 120, 90}, new int[] {12, 12, 12}, 150);
        assertTrue(sum(out) <= 150, "fits");
        assertTrue(out[0] == 30, "the narrow tab keeps its width");
        assertTrue(out[1] == out[2], "the wide ones share one width");
    }

    @Test
    void neverBelowTheNumberAlone() {
        int[] out = BarTabs.widths(new int[] {80, 80, 80}, new int[] {12, 14, 12}, 10);
        assertArrayEquals(new int[] {12, 14, 12}, out);
    }
}
