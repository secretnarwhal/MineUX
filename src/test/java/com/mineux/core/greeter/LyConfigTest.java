package com.mineux.core.greeter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LyConfigTest {

    @TempDir
    Path dir;

    private Dotfile file() {
        return new Dotfile(dir.resolve("dotfiles/ly/config.ini"), LyConfig.TEMPLATE);
    }

    @Test
    void aMissingFileIsWrittenWithItsDefaults() throws Exception {
        Dotfile f = file();
        LyConfig ly = LyConfig.load(f);
        assertTrue(Files.exists(f.path()));
        assertEquals(LyConfig.STREAM, ly.animation);
        assertEquals(LyConfig.DEFAULT_COLOR, ly.streamColor);
        assertEquals(0.22f, ly.tintOpacity, 1e-6);
        assertEquals(LyConfig.KEEP, ly.window);
        assertEquals("classic", ly.asciiPalette);
        assertEquals("", ly.api);
        String text = Files.readString(f.path());
        assertTrue(text.contains("# the MineUX greeter"));
        assertNull(com.mineux.core.BrandWords.foundIn(text), text);
    }

    @Test
    void settingAKeyKeepsEveryOtherLine() throws Exception {
        Dotfile f = file();
        LyConfig.load(f);
        String before = Files.readString(f.path());
        assertNull(LyConfig.set(f, "animation", "Fire"));
        assertNull(LyConfig.set(f, "colour", "0x00ff00"));
        assertNull(LyConfig.set(f, "api", "http://127.0.0.1:8090/api"));
        String after = Files.readString(f.path());
        assertEquals(before.split("\n").length, after.split("\n").length, "comments and lines stay where they are");
        LyConfig ly = LyConfig.load(f);
        assertEquals(LyConfig.DOOM, ly.animation);
        assertEquals(0x00FF00, ly.streamColor);
        assertEquals("http://127.0.0.1:8090/api", ly.api);
        assertTrue(after.contains("stream_color = #00FF00"));
        assertTrue(after.contains("# what plays behind the login box"));
    }

    @Test
    void badValuesAreRefusedAndNotWritten() throws Exception {
        Dotfile f = file();
        LyConfig.load(f);
        String before = Files.readString(f.path());
        assertNotNull(LyConfig.set(f, "animation", "lasers"));
        assertNotNull(LyConfig.set(f, "stream_color", "blue"));
        assertNotNull(LyConfig.set(f, "ascii_palette", "rainbow"));
        assertNotNull(LyConfig.set(f, "api", "ftp://x"));
        assertNotNull(LyConfig.set(f, "nope", "1"));
        assertEquals(before, Files.readString(f.path()));
        assertNull(LyConfig.set(f, "tint_opacity", "7"));
        assertEquals(1f, LyConfig.load(f).tintOpacity, 1e-6);
    }

    @Test
    void handEditsAreReadWithCommentsQuotesAndJunk() throws Exception {
        Path p = dir.resolve("ly.ini");
        Files.writeString(p, "[section]\n; note\nanimation = \"ascii\"  # inline\nstream_color = #F2B825\n"
            + "window = maximised\nascii_palette = GOLD\nstream_x = x\n");
        Dotfile f = new Dotfile(p, LyConfig.TEMPLATE);
        Map<String, String> values = f.read();
        assertEquals("ascii", values.get("animation"));
        LyConfig ly = LyConfig.load(f);
        assertEquals(LyConfig.ASCII, ly.animation);
        assertEquals(0xF2B825, ly.streamColor);
        assertEquals(LyConfig.MAXIMIZE, ly.window);
        assertEquals("gold", ly.asciiPalette);
        assertEquals(LyConfig.DEFAULT_X, ly.streamX, 1e-6);
    }

    @Test
    void aKeyTheFileLacksIsAddedAtTheEnd() throws Exception {
        Path p = dir.resolve("old.ini");
        Files.writeString(p, "# an old file\nanimation = none\n");
        Dotfile f = new Dotfile(p, LyConfig.TEMPLATE);
        assertNull(LyConfig.set(f, "api", "https://example.test/api"));
        assertEquals("# an old file\nanimation = none\napi = https://example.test/api\n", Files.readString(p));
    }

    @Test
    void keysComeInPairs() {
        assertEquals(0, LyConfig.KEYS.length % 2);
        for (int i = 0; i < LyConfig.KEYS.length; i += 2) {
            assertTrue(LyConfig.TEMPLATE.contains(LyConfig.KEYS[i] + " ="), LyConfig.KEYS[i]);
        }
    }
}
