package com.mineux.core.ui.term;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The hyprland.conf MineUX ships: every line parses, every bind's dispatcher exists, SUPER is Alt. */
final class HyprConfTemplateTest {

    @Test
    void templateParsesWithoutErrorsAndEveryDispatcherIsKnown() {
        String text = HyprConf.template();
        assertFalse(text.isEmpty(), "hyprland.conf on the classpath");
        assertEquals(null, com.mineux.core.BrandWords.foundIn(text), "rebranded");
        HyprConf conf = new HyprConf();
        conf.parse(text);
        assertTrue(conf.errors.isEmpty(), conf.errors.toString());
        assertTrue(conf.binds.size() > 20, "binds: " + conf.binds.size());
        Set<String> workspaces = new HashSet<>();
        boolean terminal = false;
        for (HyprConf.Bind b : conf.binds) {
            assertTrue(Hypr.known(b.dispatcher), "unknown dispatcher " + b.dispatcher + " on " + b.keys);
            if (b.dispatcher.equals("workspace")) workspaces.add(b.arg);
            if (b.dispatcher.equals("exec") && b.arg.equals("muxsh")) terminal = true;
        }
        assertTrue(terminal, "SUPER+Space opens a muxsh terminal");
        for (int n = 1; n <= 9; n++) assertTrue(workspaces.contains(String.valueOf(n)), "SUPER+" + n);
    }

    @Test
    void superIsAltByDefault() {
        assertEquals("alt", Shell.superKey());
        assertEquals(org.lwjgl.glfw.GLFW.GLFW_MOD_ALT, HyprConf.superBit());
    }
}
