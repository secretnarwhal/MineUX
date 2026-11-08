package com.mineux.core.greeter;

import com.mineux.core.config.ConfigFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One file under {@code config/mineux/dotfiles}: plain {@code key = value} lines with
 * {@code #} comments, meant to be read and edited by hand as much as by MineUX.
 * <p>
 * The dotfiles are where MineUX's look and behaviour are configured the way a Linux desktop
 * is -- one small, commented file per program ({@code ly/config.ini} for the greeter) --
 * rather than in the json MineUX writes for itself. A file that does not exist is written out
 * with its defaults and their comments the first time it is read, so there is always
 * something to open and edit. Editing keeps every comment and line in place and only rewrites
 * the value asked for.
 * <p>
 * Public for the desktop's {@code dotfiles} command and the other programs' dotfiles.
 */
public final class Dotfile {

    private static final Logger LOGGER = LoggerFactory.getLogger("MineUX/dotfiles");

    private final Path path;
    private final String template;

    /**
     * @param path     the file
     * @param template the file as first written: every key with its default and a comment
     */
    public Dotfile(Path path, String template) {
        this.path = path;
        this.template = template;
    }

    /** A file under the host's {@link #directory()}, e.g. {@code ly/config.ini}. */
    public static Dotfile of(String relative, String template) {
        return new Dotfile(directory().resolve(relative), template);
    }

    /** {@code config/mineux/dotfiles} (the host's config dir; under build/ in the unit tests). */
    public static Path directory() {
        return ConfigFile.directory().resolve("dotfiles");
    }

    public Path path() {
        return path;
    }

    /** Every key and its value, in file order. Read fresh each time: the file is edited by hand. */
    public Map<String, String> read() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : lines()) {
            int eq = line.indexOf('=');
            String trimmed = line.trim();
            if (eq <= 0 || trimmed.startsWith("#") || trimmed.startsWith(";") || trimmed.startsWith("[")) continue;
            values.put(line.substring(0, eq).trim().toLowerCase(Locale.ROOT), strip(line.substring(eq + 1)));
        }
        return values;
    }

    /** Sets one key, keeping every other line as it is; a key the file lacks is added at the end. */
    public void write(String key, String value) {
        List<String> lines = lines();
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int eq = line.indexOf('=');
            if (eq <= 0 || line.trim().startsWith("#")) continue;
            if (line.substring(0, eq).trim().equalsIgnoreCase(key)) {
                lines.set(i, key + " = " + value);
                found = true;
            }
        }
        if (!found) lines.add(key + " = " + value);
        save(String.join("\n", lines) + "\n");
    }

    /** The file's lines, writing it from the template first if it is not there yet. */
    private List<String> lines() {
        try {
            if (!Files.exists(path)) save(template);
            return new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read {}, using defaults", path, e);
            return new ArrayList<>(List.of(template.split("\n")));
        }
    }

    private void save(String text) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Could not write {}", path, e);
        }
    }

    /** A value without an inline comment or surrounding quotes. */
    static String strip(String raw) {
        // Trimmed first, so a value that is itself a colour (#F2B825) is not read as a comment.
        String value = raw.trim();
        int hash = value.indexOf(" #");
        if (hash > 0) value = value.substring(0, hash).trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
        return value;
    }
}
