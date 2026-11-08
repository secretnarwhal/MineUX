package com.mineux.core.ui.term;

import com.mineux.core.config.ConfigFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The word art the desktop cycles through: the set bundled as {@code mineux/ascii.txt}
 * (every {@code .txt} in the repo's {@code ascii/} folder, gathered by the build's
 * {@code bundleAscii} task), then any {@code .txt} dropped into {@code config/mineux/ascii}.
 */
final class Arts {

    private static final String RESOURCE = "/mineux/ascii.txt";
    /** Taller than this and a file is not word art. */
    private static final int MAX_ROWS = 80;

    final List<String> names = new ArrayList<>();
    final List<String[]> arts = new ArrayList<>();
    /** How many of {@link #arts} came with the client; the rest are the user's. */
    int bundled;

    static Arts load() {
        Arts arts = new Arts();

        try (InputStream in = Arts.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                arts.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
        }
        arts.bundled = arts.arts.size();

        arts.loadUserArt();

        if (arts.arts.isEmpty()) {
            arts.names.add("plain");
            arts.arts.add(new String[] {"m i n e u x"});
        }

        return arts;
    }

    /** Where the user's own art goes. */
    static Path folder() {
        return ConfigFile.directory().resolve("ascii");
    }

    /**
     * What the user's folder holds right now, as a string that changes whenever a file there is
     * added, removed or saved -- cheap enough to check every second or so.
     */
    static String folderSignature() {
        Path dir = folder();
        if (!Files.isDirectory(dir)) return "";
        StringBuilder sig = new StringBuilder();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.sorted().toList()) {
                sig.append(file.getFileName()).append(':').append(Files.size(file)).append(':')
                    .append(Files.getLastModifiedTime(file).toMillis()).append(';');
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return sig.toString();
    }

    /** The bundled format: {@code @@ name} starts an art, every line after it belongs to it. */
    void parse(String text) {
        List<String> current = null;

        for (String raw : text.split("\n")) {
            String line = raw.replace("\r", "");
            if (line.startsWith("@@ ")) {
                flush(current);
                names.add(line.substring(3).trim());
                current = new ArrayList<>();
            } else if (current != null) {
                current.add(line.replace("\t", "    ").stripTrailing());
            }
        }

        flush(current);
    }

    private void flush(List<String> lines) {
        if (lines == null) return;
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) lines.remove(lines.size() - 1);
        if (lines.isEmpty()) {
            names.remove(names.size() - 1);
            return;
        }
        arts.add(lines.toArray(new String[0]));
    }

    private void loadUserArt() {
        Path dir = folder();
        if (!Files.isDirectory(dir)) return;

        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                if (!name.toLowerCase(Locale.ROOT).endsWith(".txt")) continue;

                String[] art;
                try {
                    art = trim(Files.readAllLines(file, StandardCharsets.UTF_8));
                } catch (IOException | RuntimeException e) {
                    continue;
                }
                if (art.length == 0 || art.length > MAX_ROWS) continue;

                String stem = name.substring(0, name.length() - 4).toLowerCase(Locale.ROOT);
                // Named as the bundled ones are; a name already taken gets a suffix.
                String base = stem.replaceFirst("^mineux(ascii)?", "");
                if (base.isEmpty()) base = stem;
                String unique = base;
                for (int n = 2; names.contains(unique); n++) unique = base + "-" + n;

                names.add(unique);
                arts.add(art);
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    /** Tabs as spaces, blank lines off both ends, and the left margin every line shares. */
    private static String[] trim(List<String> raw) {
        List<String> lines = new ArrayList<>();
        for (String l : raw) lines.add(l.replace("\t", "    ").replace("﻿", "").stripTrailing());
        while (!lines.isEmpty() && lines.get(0).isBlank()) lines.remove(0);
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) lines.remove(lines.size() - 1);

        int margin = Integer.MAX_VALUE;
        for (String l : lines) {
            if (l.isBlank()) continue;
            margin = Math.min(margin, l.length() - l.stripLeading().length());
        }
        if (margin == Integer.MAX_VALUE) margin = 0;

        String[] out = new String[lines.size()];
        for (int i = 0; i < out.length; i++) {
            String l = lines.get(i);
            out[i] = l.length() >= margin ? l.substring(margin) : "";
        }
        return out;
    }
}
