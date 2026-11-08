package com.mineux.screens;

import com.mineux.core.TrayGroup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Screens' tray group against /mineux/screens.txt: every switch is a row, every row a dir. */
class ScreensTrayTest {

    @Test
    void everySwitchIsARow() throws IOException {
        String file;
        try (InputStream in = ScreensTrayTest.class.getResourceAsStream("/mineux/screens.txt")) {
            assertNotNull(in, "/mineux/screens.txt on the classpath");
            file = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Set<String> rows = new HashSet<>();
        for (TrayGroup.Row row : new Screens.Tray().rows()) rows.add(row.path());
        Set<String> dirs = new HashSet<>();
        for (String line : file.split("\\R")) {
            if (!line.startsWith("dir ")) continue;
            String[] w = line.split("\\|")[0].trim().split("\\s+");
            dirs.add(w[1]);
            if (line.contains(" toggle=")) assertTrue(rows.contains(w[1]), w[1] + " has a switch but no row in the screens group");
        }
        for (String row : rows) assertTrue(dirs.contains(row), row + " is no dir of screens.txt");
    }
}
