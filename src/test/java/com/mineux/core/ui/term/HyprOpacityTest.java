
package com.mineux.core.ui.term;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** SUPER+K / SUPER+L: the template's opacity binds, and the steps and levels they take. */
final class HyprOpacityTest {

    @Test
    void templateBindsOpacityOnKAndL() {
        HyprConf conf = new HyprConf();
        conf.parse(HyprConf.template());
        assertTrue(conf.errors.isEmpty(), conf.errors.toString());
        String k = null;
        String l = null;
        String cycle = null;
        for (HyprConf.Bind b : conf.binds) {
            if (b.keys.endsWith("K")) k = b.dispatcher + " " + b.arg + " " + b.repeat;
            if (b.keys.endsWith("L")) l = b.dispatcher + " " + b.arg + " " + b.repeat;
            if (b.dispatcher.equals("cyclenext")) cycle = b.keys;
        }
        assertEquals("opacity -0.05 true", k);
        assertEquals("opacity +0.05 true", l);
        assertTrue(cycle != null && cycle.endsWith("N"), String.valueOf(cycle));
        assertTrue(Hypr.known("opacity"));
    }

    @Test
    void stepsAndLevels() {
        assertEquals(0.95f, Hypr.opacityAfter(1f, "-0.05"), 1e-5f);
        assertEquals(1f, Hypr.opacityAfter(0.98f, "+0.05"), 1e-5f);
        assertEquals(Pane.MIN_OPACITY, Hypr.opacityAfter(0.2f, "-0.5"), 1e-5f);
        assertEquals(0.6f, Hypr.opacityAfter(1f, "0.6"), 1e-5f);
        assertEquals(0.6f, Hypr.opacityAfter(1f, "60%"), 1e-5f);
        assertEquals(0.6f, Hypr.opacityAfter(1f, "60"), 1e-5f);
        assertEquals(0.9f, Hypr.opacityAfter(1f, "-10%"), 1e-5f);
        assertEquals(1f, Hypr.opacityAfter(0.3f, ""), 1e-5f);
        assertTrue(Float.isNaN(Hypr.opacityAfter(1f, "lots")));
    }
}
