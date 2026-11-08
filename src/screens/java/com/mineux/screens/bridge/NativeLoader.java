package com.mineux.screens.bridge;

import com.mineux.screens.Screens;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Loads screens.dll from inside the jar (/mineux/native/screens.dll). A library cannot be loaded
 * from a jar, nor from a host's in-memory jar, so its bytes are written once to a cache
 * folder under a name made from their sha256 and loaded from there: the same build is written
 * once and reused, a new build gets a new file, and a file another game still holds open is
 * never overwritten.
 */
final class NativeLoader {

    private static final String RESOURCE = "/mineux/native/screens.dll";

    private NativeLoader() {}

    static void load() {
        byte[] bytes = read();
        if (bytes == null) throw new UnsatisfiedLinkError("Screens' native library " + RESOURCE + " is not in the jar");
        String hash = sha256(bytes);
        Path dir = cacheDir();
        Path file = dir.resolve("screens-" + hash.substring(0, 16) + ".dll");
        try {
            if (!matches(file, hash)) write(dir, file, bytes);
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("Could not unpack Screens' native library to " + file + ": " + e);
        }
        System.load(file.toAbsolutePath().toString());
        Screens.LOGGER.info("Screens native library loaded from {}", file);
    }

    private static byte[] read() {
        InputStream in = NativeLoader.class.getResourceAsStream(RESOURCE);
        if (in == null) {
            ClassLoader context = Thread.currentThread().getContextClassLoader();
            if (context != null) in = context.getResourceAsStream(RESOURCE.substring(1));
        }
        if (in == null) return null;
        try (InputStream open = in) {
            return open.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static Path cacheDir() {
        String local = System.getenv("LOCALAPPDATA");
        Path base = local != null ? Path.of(local) : Path.of(System.getProperty("java.io.tmpdir"));
        return base.resolve("MineUX").resolve("native");
    }

    private static boolean matches(Path file, String hash) {
        if (!Files.isRegularFile(file)) return false;
        try {
            return hash.equals(sha256(Files.readAllBytes(file)));
        } catch (IOException e) {
            return false;
        }
    }

    private static void write(Path dir, Path file, byte[] bytes) throws IOException {
        Files.createDirectories(dir);
        Path part = Files.createTempFile(dir, "screens-", ".part");
        try {
            Files.write(part, bytes);
            try {
                Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // Another game wrote (and holds) the same file meanwhile: it is the same bytes.
            if (!matches(file, sha256(bytes))) throw e;
        } finally {
            Files.deleteIfExists(part);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
