
package com.mineux.core.ui.term;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** How a main menu button another mod adds is named as a command. */
class MenuButtonsTest {

    @Test
    void labelsBecomeCommands() {
        assertEquals("mods", MenuButtons.slug("Mods"));
        assertEquals("options", MenuButtons.slug("Options..."));
        assertEquals("minecraft-realms", MenuButtons.slug("Minecraft Realms"));
        assertEquals("replay-viewer", MenuButtons.slug("  Replay   Viewer! "));
        assertEquals("", MenuButtons.slug("???"));
    }

    /** Vanilla's own buttons, each to its own command -- v78 shipped singleplayer as multiplayer. */
    @Test
    void vanillaButtonsKeepTheirOwnNames() {
        assertEquals("singleplayer", MenuButtons.known("menu.singleplayer"));
        assertEquals("multiplayer", MenuButtons.known("menu.multiplayer"));
        assertEquals("realms", MenuButtons.known("menu.online"));
        assertEquals("language", MenuButtons.known("narrator.button.language"));
        assertEquals("disconnect", MenuButtons.known("menu.returnToMenu"));
        assertEquals(null, MenuButtons.known("some.mod.button"));
        assertEquals(null, MenuButtons.known((String) null));
    }

    /** Java's @file quoting: backslashes and quotes escaped, one quoted argument a line. */
    @Test
    void rebootArgfileQuotes() {
        assertEquals("\"C:\\\\Users\\\\A B\\\\x.jar\"\n\"say \\\"hi\\\"\"\n",
            Reboot.argfile(java.util.List.of("C:\\Users\\A B\\x.jar", "say \"hi\"")));
    }
}
