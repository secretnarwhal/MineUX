package com.mineux.visuals.cosmetics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * {@code config/mineux/capes/}: where your own cape pictures go, one PNG each. Made, with a
 * README saying what goes in it, the first time your cape is drawn.
 */
final class CapeFolder {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/cosmetics");

    /** {@link #stamp} of a file that is not there. */
    static final long MISSING = Long.MIN_VALUE;

    private CapeFolder() {}

    static Path dir() {
        return com.mineux.core.config.ConfigFile.directory().resolve("capes");
    }

    static Path file(String name) {
        return dir().resolve(name + ".png");
    }

    /** Makes the folder and drops its README in, once. */
    static void prepare() {
        try {
            Path dir = dir();
            Files.createDirectories(dir);
            Path readme = dir.resolve("README.txt");
            if (Files.exists(readme)) return;
            try (InputStream in = CapeFolder.class.getResourceAsStream("/mineux/capes/README.txt")) {
                if (in != null) Files.copy(in, readme);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("could not make the cape folder", e);
        }
    }

    /**
     * A file name as typed (with or without {@code .png}), or null when it is empty or could reach
     * outside the folder: letters, digits, space, '-', '_' and '.' only, and no "..".
     */
    static String clean(String typed) {
        if (typed == null) return null;
        String name = typed.trim();
        if (name.toLowerCase(Locale.ROOT).endsWith(".png")) name = name.substring(0, name.length() - 4).trim();
        if (name.isEmpty() || name.length() > 64 || name.contains("..")) return null;
        for (int i = 0; i < name.length(); i++) {
            if (!allowed(name.charAt(i))) return null;
        }
        return name;
    }

    private static boolean allowed(char c) {
        return Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_' || c == '.';
    }

    /** Changes when the file does (its time and size); {@link #MISSING} while it is not there. */
    static long stamp(String name) {
        try {
            Path path = file(name);
            if (!Files.isRegularFile(path)) return MISSING;
            return Files.getLastModifiedTime(path).toMillis() * 31L + Files.size(path);
        } catch (IOException | RuntimeException e) {
            return MISSING;
        }
    }

    /** A texture name for the file, lower case and safe in an identifier, unique to the name as typed. */
    static String textureName(String name) {
        StringBuilder out = new StringBuilder("cape_file_");
        String lower = name.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean plain = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
            out.append(plain ? c : '_');
        }
        return out.append('_').append(Integer.toHexString(name.hashCode())).toString();
    }
}
