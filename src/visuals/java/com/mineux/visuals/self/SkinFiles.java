package com.mineux.visuals.self;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Locale;

/**
 * The skin changer's files, without the game: whether some bytes are a skin png, whether a skin
 * has slim arms, and keeping a chosen png in config/mineux/skins under a name of its own.
 * <p>
 * A copy is kept rather than the path the file came from, so the skin stays when the original is
 * moved or deleted, and every skin chosen before is still in the folder to choose again.
 */
final class SkinFiles {

    /** No skin png is anywhere near this; anything bigger is not one. */
    static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int MAX_NAME = 48;
    private static final int MAX_COPIES = 999;

    private SkinFiles() {}

    /** A png's width and height from its header, or null for anything that is not a png. */
    static int[] pngSize(byte[] b) {
        if (b == null || b.length < 24) return null;
        if ((b[0] & 0xFF) != 137 || b[1] != 80 || b[2] != 78 || b[3] != 71) return null;
        int w = (b[16] & 0xFF) << 24 | (b[17] & 0xFF) << 16 | (b[18] & 0xFF) << 8 | b[19] & 0xFF;
        int h = (b[20] & 0xFF) << 24 | (b[21] & 0xFF) << 16 | (b[22] & 0xFF) << 8 | b[23] & 0xFF;
        return new int[] {w, h};
    }

    /** What is wrong with these bytes as a skin, naming the file; null when they are one. */
    static String problem(byte[] bytes, String name) {
        if (bytes == null || bytes.length == 0) return name + " is empty";
        if (bytes.length > MAX_BYTES) return name + " is too big to be a skin";
        int[] size = pngSize(bytes);
        if (size == null) return name + " is not a png";
        if (!skinSize(size[0], size[1])) {
            return "a skin is 64x64 (or the old 64x32); " + name + " is " + size[0] + "x" + size[1];
        }
        return null;
    }

    static boolean skinSize(int w, int h) {
        return w == 64 && (h == 64 || h == 32);
    }

    /**
     * Whether a 64x64 skin is drawn for slim arms: the columns a wide arm has and a slim one does
     * not -- the right arm's top and bottom past x 50, its back past x 54 -- are see-through.
     * {@code argb} is the picture row by row, alpha in the top byte; a 64x32 skin is always wide.
     */
    static boolean slim(int[] argb, int w, int h) {
        if (argb == null || w != 64 || h != 64 || argb.length < w * h) return false;
        return clear(argb, w, 50, 16, 2, 4) && clear(argb, w, 54, 20, 2, 12);
    }

    private static boolean clear(int[] argb, int w, int x0, int y0, int cw, int ch) {
        for (int y = y0; y < y0 + ch; y++) {
            for (int x = x0; x < x0 + cw; x++) {
                if ((argb[y * w + x] >>> 24) != 0) return false;
            }
        }
        return true;
    }

    /** A file name to keep a skin under: the original's, lower case, safe characters only, no extension. */
    static String safeBase(String original) {
        String name = original == null ? "" : original.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        if (name.toLowerCase(Locale.ROOT).endsWith(".png")) name = name.substring(0, name.length() - 4);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length() && out.length() < MAX_NAME; i++) {
            char c = Character.toLowerCase(name.charAt(i));
            boolean ok = c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '-' || c == '_' || c == '.';
            out.append(ok ? c : '_');
        }
        String base = out.toString();
        while (base.startsWith(".")) base = base.substring(1);
        return base.isEmpty() ? "skin" : base;
    }

    /**
     * Keeps a skin in {@code dir} and returns the name it is kept under. A file already in the
     * folder is kept where it is; the same picture chosen again is the copy made the first time;
     * another picture under a name already taken gets a number on the end.
     */
    static String store(byte[] bytes, Path source, Path dir) throws IOException {
        Path folder = dir.toAbsolutePath().normalize();
        Path from = source.toAbsolutePath().normalize();
        if (folder.equals(from.getParent())) return from.getFileName().toString();
        Files.createDirectories(folder);
        String base = safeBase(from.getFileName().toString());
        for (int i = 1; i <= MAX_COPIES; i++) {
            String name = copyName(base, i);
            Path target = folder.resolve(name);
            if (!Files.exists(target)) {
                Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                return name;
            }
            if (Arrays.equals(Files.readAllBytes(target), bytes)) return name;
        }
        throw new IOException("too many skins named " + base);
    }

    static String copyName(String base, int i) {
        return i <= 1 ? base + ".png" : base + "-" + i + ".png";
    }

    /** Whether the folder has a png in it yet. */
    static boolean anyPng(Path dir) {
        try (java.util.stream.Stream<Path> files = Files.list(dir)) {
            return files.anyMatch(SkinFiles::isPng);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static boolean isPng(Path p) {
        return p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png") && Files.isRegularFile(p);
    }
}
