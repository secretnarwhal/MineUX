package com.mineux.visuals.cosmetics;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cape list: what the settings offer is what {@link Capes} knows, every built-in cape has its
 * texture, a removed one in an old config falls back to off, and a typed file name can never
 * reach outside config/mineux/capes/.
 */
class CapesTest {

    @Test
    void settingsOfferExactlyTheKnownCapes() throws IOException {
        assertEquals(Capes.NAMES, choices("cape"));
    }

    @Test
    void everyBuiltInCapeHasItsTexture() throws IOException {
        for (String name : Capes.NAMES) {
            if (name.equals("off") || name.equals(Capes.GIRL) || name.equals(Capes.CUSTOM)) continue;
            try (InputStream in = Capes.class.getResourceAsStream("/mineux/capes/" + name + ".png")) {
                assertNotNull(in, "texture for cape " + name);
            }
        }
        for (String extra : Arrays.asList("cute-anime-girl-placeholder.png", "README.txt")) {
            try (InputStream in = Capes.class.getResourceAsStream("/mineux/capes/" + extra)) {
                assertNotNull(in, extra);
            }
        }
    }

    @Test
    void removedCapesAreGoneAndFallBackToOff() {
        for (String gone : Arrays.asList("bikini", "bikini2", "israel", "minecon2011", "mojang", "vanilla", "astolfo")) {
            assertFalse(Capes.NAMES.contains(gone), gone);
            assertNull(Capes.class.getResource("/mineux/capes/" + gone + ".png"), gone + ".png");
            CosmeticsConfig config = new CosmeticsConfig();
            config.cape = gone;
            config.ensureDefaults();
            assertEquals("off", config.cape);
        }
        CosmeticsConfig kept = new CosmeticsConfig();
        kept.cape = Capes.GIRL;
        kept.capeFile = null;
        kept.ensureDefaults();
        assertEquals(Capes.GIRL, kept.cape);
        assertEquals("", kept.capeFile);
    }

    @Test
    void fileNamesStayInTheFolder() {
        assertEquals("mycape", CapeFolder.clean(" mycape.PNG "));
        assertEquals("my cape-2_b.v1", CapeFolder.clean("my cape-2_b.v1"));
        for (String bad : Arrays.asList(null, "", "  ", ".png", "../x", "..", "a/b", "a\\b", "c:x", "x?", "a..b")) {
            assertNull(CapeFolder.clean(bad), String.valueOf(bad));
        }
        String id = CapeFolder.textureName("My Cape.v1");
        assertTrue(id.matches("[a-z0-9_.-]+"), id);
        assertFalse(id.equals(CapeFolder.textureName("my cape.v1")), "names differing in case get their own texture");
    }

    @Test
    void seeThroughPixelsShowTheLining() {
        assertEquals(0xFF102030, CapeFit.over(0x00FFFFFF, 0xFF102030));
        assertEquals(0xFFABCDEF, CapeFit.over(0xFFABCDEF, 0xFF102030));
        assertEquals(0x7F4020, CapeFit.scale(0xFF8040, 50));
    }

    /** The choices a {@code set <key>} line of visuals.txt offers, '_' read as a space. */
    private static List<String> choices(String key) throws IOException {
        try (InputStream in = Capes.class.getResourceAsStream("/mineux/visuals.txt")) {
            assertNotNull(in, "visuals.txt on the classpath");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!line.startsWith("set " + key + " ")) continue;
                for (String part : line.split("\\|")[0].trim().split("\\s+")) {
                    if (!part.startsWith("choices=")) continue;
                    List<String> out = new ArrayList<>();
                    for (String c : part.substring("choices=".length()).split(";")) out.add(c.replace('_', ' '));
                    return out;
                }
            }
        }
        throw new AssertionError("no choices for " + key);
    }
}
