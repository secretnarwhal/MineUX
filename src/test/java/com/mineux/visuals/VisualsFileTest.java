package com.mineux.visuals;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The visuals' settings file against the code, the way {@code ConfigRegistryTest} holds
 * modules.txt to its configs: every {@code cfg=} is a registered (so per-workstation)
 * config, every registered config is used, every {@code toggle=} and {@code set} names a public
 * field of that config, every top-level dir is in exactly one tray group, nothing in it names
 * a foreign brand, and none of the advantage settings came along.
 */
class VisualsFileTest {

    private static List<String> lines() throws IOException {
        List<String> out = new ArrayList<>();
        try (InputStream in = VisualsFileTest.class.getResourceAsStream(Visuals.MODULE_FILE)) {
            assertNotNull(in, Visuals.MODULE_FILE + " on the classpath");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) out.add(line);
        }
        return out;
    }

    /** The words before the description. */
    private static String[] words(String line) {
        return line.split("\\|")[0].trim().split("\\s+");
    }

    private static String attr(String[] words, String name) {
        for (String part : words) if (part.startsWith(name + "=")) return part.substring(name.length() + 1);
        return null;
    }

    private static Class<?> configClass(String cfg) {
        VisualConfigs.Entry e = VisualConfigs.byCfg(cfg);
        assertNotNull(e, "cfg=" + cfg + " is not registered in VisualConfigs, so it would not be per workstation");
        return e.type();
    }

    private static Class<?> fieldType(Class<?> type, String field, String where) {
        try {
            return type.getField(field).getType();
        } catch (NoSuchFieldException e) {
            throw new AssertionError(where + ": " + type.getSimpleName() + " has no public field " + field);
        }
    }

    @Test
    void everyCfgIsRegisteredAndEveryFieldExists() throws IOException {
        Set<String> used = new HashSet<>();
        String cfg = null;
        for (String line : lines()) {
            String[] w = words(line);
            if (line.startsWith("dir ")) {
                cfg = attr(w, "cfg");
                assertNotNull(cfg, "a dir without cfg=: " + line);
                used.add(cfg);
                Class<?> type = configClass(cfg);
                String toggle = attr(w, "toggle");
                if (toggle != null) assertEquals(boolean.class, fieldType(type, toggle, line), "toggle " + toggle + " is not a boolean");
            } else if (line.startsWith("set ")) {
                assertNotNull(cfg, "a set before any dir: " + line);
                assertTrue(w.length >= 3, line);
                fieldType(configClass(cfg), w[2], line);
            }
        }
        for (VisualConfigs.Entry e : VisualConfigs.all()) {
            assertTrue(used.contains(e.cfg()), "config " + e.cfg() + " is registered but no dir uses it");
        }
    }

    @Test
    void everyTopLevelDirIsInExactlyOneGroup() throws IOException {
        VisualGroup[] groups = {Visuals.HUD, Visuals.WORLD, Visuals.CAMERA, Visuals.SELF};
        Set<String> tops = new HashSet<>();
        for (String line : lines()) {
            if (!line.startsWith("dir ")) continue;
            String path = words(line)[1];
            int in = 0;
            for (VisualGroup g : groups) if (g.holds(path)) in++;
            assertEquals(1, in, path + " is in " + in + " tray groups");
            if (path.indexOf('/') < 0) tops.add(path);
        }
        for (VisualGroup g : groups) {
            for (String dir : g.dirs()) assertTrue(tops.contains(dir), g.name() + " names " + dir + ", which visuals.txt has no dir for");
        }
    }

    /** The tray: every switch a row of exactly the group holding its dir, every row a dir of the file. */
    @Test
    void everySwitchIsARowOfItsGroup() throws IOException {
        VisualGroup[] groups = {Visuals.HUD, Visuals.WORLD, Visuals.CAMERA, Visuals.SELF};
        Set<String> dirs = new HashSet<>();
        for (String line : lines()) {
            if (!line.startsWith("dir ")) continue;
            String[] w = words(line);
            dirs.add(w[1]);
            if (attr(w, "toggle") == null) continue;
            int in = 0;
            for (VisualGroup g : groups) {
                for (com.mineux.core.TrayGroup.Row row : g.rows()) {
                    if (!row.path().equals(w[1])) continue;
                    in++;
                    assertTrue(g.holds(w[1]), w[1] + " is a row of " + g.name() + ", which does not hold its dir");
                }
            }
            assertEquals(1, in, w[1] + " is a row of " + in + " tray groups");
        }
        for (VisualGroup g : groups) {
            assertFalse(g.rows().isEmpty(), g.name() + " has no rows, so the bar would not draw it");
            for (com.mineux.core.TrayGroup.Row row : g.rows()) {
                assertTrue(dirs.contains(row.path()), g.name() + " row " + row.path() + " is no dir of visuals.txt");
                assertTrue(row.icon().startsWith("minecraft:"), row.path() + " icon " + row.icon());
            }
        }
    }

    @Test
    void nothingNamesWhereItCameFrom() throws IOException {
        for (String line : lines()) {
            assertEquals(null, com.mineux.core.BrandWords.foundIn(line), line);
        }
    }

    @Test
    void noAdvantageSettingCameAlong() throws IOException {
        for (String line : lines()) {
            if (!line.startsWith("dir ") && !line.startsWith("set ")) continue;
            String[] w = words(line.toLowerCase(Locale.ROOT));
            String key = w[1] + " " + (w.length > 2 ? w[2] : "");
            // A field called just walls was the lock-on's and others' totem pops through walls; your
            // own model, jump rings and ambient particles through walls (modelwalls...) are fine.
            assertFalse(line.startsWith("set ") && w.length > 2 && w[2].equals("walls"), line);
            assertFalse(line.startsWith("set ") && w.length > 2 && w[2].equals("target"), "a lock-on round your target: " + line);
            for (String banned : new String[] {"esp", "fullbright", "freecam", "noclip", "crystal", "aim", "serverlook"}) {
                assertFalse((" " + key).contains(" " + banned), "'" + banned + "' in " + line);
            }
        }
    }
}
