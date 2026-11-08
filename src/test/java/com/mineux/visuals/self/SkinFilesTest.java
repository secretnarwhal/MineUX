package com.mineux.visuals.self;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The skin changer's browse files: which pngs are skins, slim arms, and the copies it keeps. */
class SkinFilesTest {

    private static byte[] png(int w, int h, int fill) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, fill);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void readsPngSizeFromHeader() throws IOException {
        assertArrayEquals(new int[] {64, 32}, SkinFiles.pngSize(png(64, 32, 0xFF00FF00)));
        assertNull(SkinFiles.pngSize("not a png at all, just some text".getBytes()));
        assertNull(SkinFiles.pngSize(new byte[3]));
        assertNull(SkinFiles.pngSize(null));
    }

    @Test
    void acceptsOnlySkinSizedPngs() throws IOException {
        assertNull(SkinFiles.problem(png(64, 64, 0xFFFFFFFF), "a.png"));
        assertNull(SkinFiles.problem(png(64, 32, 0xFFFFFFFF), "old.png"));
        String big = SkinFiles.problem(png(128, 128, 0xFFFFFFFF), "hd.png");
        assertNotNull(big);
        assertTrue(big.contains("128x128"), big);
        assertTrue(SkinFiles.problem("GIF89a............................".getBytes(), "x.gif").contains("not a png"));
        assertTrue(SkinFiles.problem(new byte[0], "e.png").contains("empty"));
        assertTrue(SkinFiles.problem(new byte[SkinFiles.MAX_BYTES + 1], "huge.png").contains("too big"));
    }

    private static int[] solid(int argb) {
        int[] px = new int[64 * 64];
        java.util.Arrays.fill(px, argb);
        return px;
    }

    private static void clear(int[] px, int x0, int y0, int w, int h) {
        for (int y = y0; y < y0 + h; y++) for (int x = x0; x < x0 + w; x++) px[y * 64 + x] = 0x00FFFFFF;
    }

    @Test
    void slimArmsAreTheSeeThroughColumns() {
        int[] wide = solid(0xFF336699);
        assertFalse(SkinFiles.slim(wide, 64, 64));

        int[] slim = solid(0xFF336699);
        clear(slim, 50, 16, 2, 4);
        clear(slim, 54, 20, 2, 12);
        assertTrue(SkinFiles.slim(slim, 64, 64));

        // One painted pixel in the wide-only columns: wide.
        slim[25 * 64 + 55] = 0xFF000000;
        assertFalse(SkinFiles.slim(slim, 64, 64));

        // Only half the columns clear: wide.
        int[] half = solid(0xFF336699);
        clear(half, 54, 20, 2, 12);
        assertFalse(SkinFiles.slim(half, 64, 64));

        // The old 64x32 layout has no slim arms.
        assertFalse(SkinFiles.slim(new int[64 * 32], 64, 32));
        assertFalse(SkinFiles.slim(null, 64, 64));
    }

    @Test
    void namesAreSafe() {
        assertEquals("steve", SkinFiles.safeBase("Steve.PNG"));
        assertEquals("my_skin__1_", SkinFiles.safeBase("C:\\Users\\x\\Downloads\\My Skin (1).png"));
        assertEquals("skin", SkinFiles.safeBase("../.png"));
        assertEquals("skin", SkinFiles.safeBase(""));
        assertEquals("a.b", SkinFiles.safeBase("/tmp/..a.b.png"));
        assertTrue(SkinFiles.safeBase("x".repeat(200) + ".png").length() <= 48);
        assertEquals("s.png", SkinFiles.copyName("s", 1));
        assertEquals("s-3.png", SkinFiles.copyName("s", 3));
    }

    @Test
    void copiesIntoTheSkinsFolder(@TempDir Path tmp) throws IOException {
        Path skins = tmp.resolve("config/mineux/skins");
        Path downloads = Files.createDirectories(tmp.resolve("Downloads"));
        byte[] first = png(64, 64, 0xFF112233);
        Path chosen = Files.write(downloads.resolve("Cool Skin.png"), first);

        String kept = SkinFiles.store(first, chosen, skins);
        assertEquals("cool_skin.png", kept);
        assertArrayEquals(first, Files.readAllBytes(skins.resolve(kept)));

        // The original can go; the copy stays.
        Files.delete(chosen);
        assertTrue(Files.isRegularFile(skins.resolve(kept)));

        // The same picture again is the same copy.
        Path again = Files.write(downloads.resolve("cool skin.png"), first);
        assertEquals("cool_skin.png", SkinFiles.store(first, again, skins));

        // Another picture under that name gets a number; the first is left alone.
        byte[] second = png(64, 64, 0xFF445566);
        Path other = Files.write(downloads.resolve("cool skin.png"), second);
        assertEquals("cool_skin-2.png", SkinFiles.store(second, other, skins));
        assertArrayEquals(first, Files.readAllBytes(skins.resolve("cool_skin.png")));
        assertArrayEquals(second, Files.readAllBytes(skins.resolve("cool_skin-2.png")));

        // A skin chosen from the folder itself is picked where it is, not copied.
        assertEquals("cool_skin.png", SkinFiles.store(first, skins.resolve("cool_skin.png"), skins));
        try (java.util.stream.Stream<Path> files = Files.list(skins)) {
            assertEquals(2, files.count());
        }
        assertTrue(SkinFiles.anyPng(skins));
        assertFalse(SkinFiles.anyPng(Files.createDirectories(tmp.resolve("empty"))));
    }
}
