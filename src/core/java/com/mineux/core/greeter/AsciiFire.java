/*
 * The simulation follows the "ASCII Fire" animation at asciiart.eu/animations/ascii-fire:
 * its fuel rows, flares, weighted upward diffusion with wind, decay and turbulence, embers
 * and sparks, re-implemented here for Minecraft's GUI.
 */

package com.mineux.core.greeter;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Random;

/**
 * Fire drawn in text characters: a heat field on a grid of cells, fed along the bottom,
 * carried upward cell to cell (weighted towards the cell straight below, so it rises in
 * tongues) while it cools and flickers, and drawn as {@code " .:-=+*#%@"} from cold to hot,
 * each in its own shade from the palette. Embers drift up out of it and sparks fly.
 * <p>
 * Two shapes: a {@link #WALL wall of fire} along the whole bottom, the greeter's
 * {@code ascii} animation, and a {@link #CAMPFIRE campfire} burning from the middle third,
 * which is the fetch's logo.
 * <p>
 * It steps at a fixed 30 per second of wall time however often it is drawn, so one fire drawn
 * in two places in a frame does not burn twice as fast.
 */
public final class AsciiFire {

    public static final String WALL = "wall", CAMPFIRE = "campfire";

    private static final String CHARS = " .:-=+*#%@";
    private static final long STEP_NS = 1_000_000_000L / 30;

    // The site's palettes, cold to hot. One array each and never an int[][]: an
    // obfuscator mistypes a value read out of an int[][] into a local, and the JVM refuses the
    // class ("Bad type on operand stack") -- which crashed every launch of an obfuscated build.
    private static final int[] CLASSIC = {0x000000, 0x1A0A02, 0x3D1206, 0x5E1A08, 0x8C2A08, 0xB84510, 0xD86A18, 0xF49922, 0xFFC844, 0xFFF8DC};
    private static final int[] BLUE = {0x000000, 0x020A1A, 0x061A3D, 0x0A2A5E, 0x104080, 0x1860A8, 0x2088D0, 0x40B0F0, 0x80D8FF, 0xE0F8FF};
    private static final int[] LAVA = {0x000000, 0x1A0000, 0x3D0404, 0x600808, 0x8A1010, 0xB82020, 0xE04040, 0xF08060, 0xFFC0A0, 0xFFFFFF};
    private static final int[] MATRIX = {0x000000, 0x001A02, 0x003D08, 0x006010, 0x008820, 0x10B030, 0x30D850, 0x60F080, 0xA0FFB0, 0xE0FFE8};
    private static final int[] GOLD = {0x000000, 0x1A1402, 0x3D2F06, 0x5E4808, 0x8C6A08, 0xB88E10, 0xD8AE18, 0xF2C230, 0xFFDF60, 0xFFF8DC};

    /** Palette names; see {@link #palette}. */
    public static final String[] PALETTE_NAMES = {"classic", "blue", "lava", "matrix", "gold"};

    private static final int EMBERS = 256, SPARKS = 128;

    private final boolean campfire;
    private final float intensity;
    private final float decay;
    private final float turbulence;
    private final float wind;
    private final int[] palette;
    private final Random random = new Random();

    private int cols, rows;
    private float[] heat, noise;
    /** How stoked it is: 0 at rest, up to 2 after a few clicks, dying back over a few seconds. */
    private float stoked;
    private float drift;
    private long lastStep = System.nanoTime();

    private final float[] ex = new float[EMBERS], ey = new float[EMBERS], evx = new float[EMBERS], evy = new float[EMBERS], elife = new float[EMBERS];
    private int embers;
    private final float[] sx = new float[SPARKS], sy = new float[SPARKS], svx = new float[SPARKS], svy = new float[SPARKS], slife = new float[SPARKS];
    private int sparks;

    /**
     * @param mode       {@link #WALL} or {@link #CAMPFIRE}
     * @param palette    one of {@link #PALETTE_NAMES}; anything else is classic
     * @param intensity  heat of the fuel, 1 to 9 (the site's default is 7)
     */
    public AsciiFire(String mode, String palette, float intensity) {
        this.campfire = mode != null && mode.equals(CAMPFIRE);
        this.intensity = intensity;
        this.decay = 1f;
        this.turbulence = 5f;
        this.wind = 0f;
        this.palette = palette(palette);
    }

    /** No string switch: an obfuscator a host may run over this code, such as Skidfuscator, breaks those. */
    private static int[] palette(String name) {
        if (name == null) return CLASSIC;
        if (name.equals("blue")) return BLUE;
        if (name.equals("lava")) return LAVA;
        if (name.equals("matrix")) return MATRIX;
        if (name.equals("gold")) return GOLD;
        return CLASSIC;
    }

    /** Whether {@code name} is one of {@link #PALETTE_NAMES}. */
    public static boolean isPalette(String name) {
        for (String p : PALETTE_NAMES) {
            if (p.equals(name)) return true;
        }
        return false;
    }

    private void resize(int c, int r) {
        cols = c;
        rows = r;
        heat = new float[c * r];
        noise = new float[c * r];
        for (int i = 0; i < noise.length; i++) noise[i] = 0.3f * random.nextFloat();
        // Two passes of a 3x3 blur, so the cooling noise is patchy rather than speckled.
        for (int pass = 0; pass < 2; pass++) {
            float[] out = new float[noise.length];
            for (int y = 0; y < r; y++) {
                for (int x = 0; x < c; x++) {
                    float sum = 0;
                    int n = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = x + dx, yy = y + dy;
                            if (xx >= 0 && xx < c && yy >= 0 && yy < r) {
                                sum += noise[yy * c + xx];
                                n++;
                            }
                        }
                    }
                    out[y * c + x] = sum / n;
                }
            }
            noise = out;
        }
        embers = 0;
        sparks = 0;
    }

    /**
     * A poke at the fire: hotter fuel (the brightest colours), slower cooling (taller flames), a
     * wider bed and a burst of sparks, dying back over a few seconds. Stoking again adds to it.
     */
    public void stoke() {
        stoked = Math.min(2f, stoked + 1f);
        if (heat == null) return;
        float[] s = source();
        for (int k = 0; k < 14 && sparks < SPARKS; k++) {
            int i = sparks++;
            sx[i] = s[0] + random.nextFloat() * s[1];
            sy[i] = rows - 2 - 2 * random.nextFloat();
            slife[i] = 0.35f + 0.45f * random.nextFloat();
            svx[i] = 4 * (random.nextFloat() - 0.5f);
            svy[i] = -2.5f - 3f * random.nextFloat();
        }
    }

    /** The burning part of the bottom row: {start, width} in cells; a stoked campfire burns wider. */
    private float[] source() {
        if (!campfire) return new float[] {0, cols};
        float w = (0.3f + 0.12f * Math.min(1f, stoked)) * cols;
        return new float[] {cols / 2f - w / 2f, w};
    }

    private boolean inSource(int x, float[] s) {
        return x >= s[0] && x < s[0] + s[1];
    }

    private void step() {
        float[] s = source();
        int c = cols, r = rows, n = c * r;
        float boost = Math.min(1f, stoked);
        float fuel = Math.min(9.5f, intensity + 2.8f * stoked);
        float cooling = decay * (1f - 0.4f * boost);
        stoked = Math.max(0f, stoked * 0.985f - 0.002f);

        // Fuel along the bottom row.
        int fuelRow = r - 1;
        for (int x = 0; x < c; x++) {
            int i = fuelRow * c + x;
            if (!inSource(x, s)) {
                heat[i] *= 0.3f;
                continue;
            }
            float h = fuel * (0.6f + 0.4f * random.nextFloat());
            if (random.nextFloat() < 0.12f) heat[i] *= 0.7f;
            else heat[i] = Math.max(heat[i], h);
        }

        // Flares: a tongue of heat pushed up from a random point of the source.
        float flareRate = (campfire ? 0.5f : 1f) * (1f + boost);
        int flares = random.nextFloat() < 0.5f * flareRate ? 1 + random.nextInt(4) : 0;
        for (int f = 0; f < flares; f++) {
            int fx = (int) Math.floor(s[0] + random.nextFloat() * s[1]);
            int length = 4 + random.nextInt(8);
            float hot = 7 + 3 * random.nextFloat();
            for (int e = 0; e < length; e++) {
                int y = r - 3 - e;
                if (y < 0) break;
                float half = (float) (1.5 * Math.sin(e / (double) length * Math.PI));
                for (float d = 0f - half; d <= half; d++) {
                    int x = fx + Math.round(d + (random.nextFloat() - 0.5f));
                    if (x < 0 || x >= c) continue;
                    int i = y * c + x;
                    heat[i] = Math.max(heat[i], hot * (1 - e / (float) length * 0.5f) * (0.6f + 0.4f * random.nextFloat()));
                }
            }
        }

        // Upward: each cell takes a weighted average of the row below, cooled and stirred.
        drift += 0.02f;
        float[] row = new float[c];
        for (int y = 0; y < r - 1; y++) {
            float up = 1 - y / (float) r;
            int below = y + 1;
            for (int x = 0; x < c; x++) {
                int from = x;
                if (wind != 0) from = Math.max(0, Math.min(c - 1, Math.round(x + wind * up * (0.5f + 0.3f * random.nextFloat()))));
                float sum = 3 * heat[below * c + from];
                float weight = 3;
                if (from > 0) { sum += 2 * heat[below * c + from - 1]; weight += 2; }
                if (from < c - 1) { sum += 2 * heat[below * c + from + 1]; weight += 2; }
                if (from > 1) { sum += heat[below * c + from - 2]; weight += 1; }
                if (from < c - 2) { sum += heat[below * c + from + 2]; weight += 1; }
                if (below + 1 < r) { sum += heat[(below + 1) * c + from]; weight += 1; }
                float v = sum / weight;
                int u = ((y + (int) Math.floor(10 * drift)) % r) * c + x;
                v -= 0.2f * cooling * (0.6f + noise[Math.abs(u) % n] + 0.4f * random.nextFloat());
                v += (random.nextFloat() - 0.5f) * up * turbulence * 0.12f;
                row[x] = Math.max(0, Math.min(10, v));
            }
            System.arraycopy(row, 0, heat, y * c, c);
        }

        // Soften the bottom few rows sideways.
        for (int y = Math.max(0, r - 8); y < r - 1; y++) {
            for (int x = 1; x < c - 1; x++) {
                int i = y * c + x;
                heat[i] = 0.85f * heat[i] + 0.075f * (heat[i - 1] + heat[i + 1]);
            }
        }

        stepEmbers(s, 1 / 30f);
        stepSparks(s, 1 / 30f);
    }

    private void stepEmbers(float[] s, float dt) {
        if (embers < EMBERS && random.nextFloat() < 0.12f + 0.02f + 0.01f * intensity) {
            int i = embers++;
            ex[i] = s[0] + random.nextFloat() * s[1];
            ey[i] = rows - 2 - 3 * random.nextFloat();
            elife[i] = 0.8f + 1.5f * random.nextFloat();
            evx[i] = 1.2f * (random.nextFloat() - 0.5f) - 0.08f * wind;
            evy[i] = -0.6f - 0.8f * random.nextFloat();
        }
        int kept = 0;
        for (int i = 0; i < embers; i++) {
            elife[i] -= dt;
            if (elife[i] <= 0) continue;
            ex[i] += evx[i] * dt * 30;
            ey[i] += evy[i] * dt * 30;
            evx[i] += 0.5f * (random.nextFloat() - 0.5f) - 0.02f * wind;
            evy[i] -= 0.1f;
            if (ey[i] < 0 || ex[i] < 0 || ex[i] >= cols) continue;
            ex[kept] = ex[i]; ey[kept] = ey[i]; evx[kept] = evx[i]; evy[kept] = evy[i]; elife[kept] = elife[i];
            kept++;
        }
        embers = kept;
    }

    private void stepSparks(float[] s, float dt) {
        if (sparks < SPARKS && random.nextFloat() < 0.25f + 0.03f * intensity) {
            int count = 1 + random.nextInt(3);
            for (int k = 0; k < count && sparks < SPARKS; k++) {
                int i = sparks++;
                sx[i] = s[0] + random.nextFloat() * s[1];
                sy[i] = rows - 3 - 2 * random.nextFloat();
                slife[i] = 0.25f + 0.35f * random.nextFloat();
                svx[i] = 3 * (random.nextFloat() - 0.5f) - 0.15f * wind;
                svy[i] = -1.5f - 2.5f * random.nextFloat();
            }
        }
        int kept = 0;
        for (int i = 0; i < sparks; i++) {
            slife[i] -= dt;
            if (slife[i] <= 0) continue;
            sx[i] += svx[i] * dt * 30;
            sy[i] += svy[i] * dt * 30;
            svx[i] += 2 * (random.nextFloat() - 0.5f) - 0.05f * wind;
            svy[i] += 0.3f;
            if (sy[i] < 0 || sx[i] < 0 || sx[i] >= cols) continue;
            sx[kept] = sx[i]; sy[kept] = sy[i]; svx[kept] = svx[i]; svy[kept] = svy[i]; slife[kept] = slife[i];
            kept++;
        }
        sparks = kept;
    }

    /**
     * Burns in a {@code cols} x {@code rows} grid of {@code cellW} x {@code cellH} cells with
     * its top left at {@code x, y}. A new size starts the fire over.
     *
     * @param alpha 0..1, for fading it
     */
    public void draw(GuiGraphicsExtractor g, Font font, int x, int y, int cols, int rows, int cellW, int cellH, float alpha) {
        if (cols < 4 || rows < 4) return;
        if (heat == null || cols != this.cols || rows != this.rows) resize(cols, rows);

        long now = System.nanoTime();
        int steps = 0;
        while (now - lastStep >= STEP_NS && steps++ < 4) {
            step();
            lastStep += STEP_NS;
        }
        if (now - lastStep > STEP_NS * 8) lastStep = now;

        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255) << 24;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                float h = heat[row * cols + col];
                int ci = Math.min(9, (int) Math.floor(h + 0.5f));
                if (ci <= 0) continue;
                int color = palette[Math.min(9, (int) Math.floor(h))];
                if (color == 0) continue;
                cell(g, font, CHARS.charAt(ci), x, y, col, row, cellW, cellH, a | color);
            }
        }
        for (int i = 0; i < embers; i++) {
            cell(g, font, elife[i] > 0.3f ? '*' : '.', x, y, (int) ex[i], (int) ey[i], cellW, cellH, a | palette[elife[i] > 0.5f ? 9 : 7]);
        }
        for (int i = 0; i < sparks; i++) {
            cell(g, font, '\'', x, y, (int) sx[i], (int) sy[i], cellW, cellH, a | palette[9]);
        }
    }

    /** One character centred in its cell: the default font is not monospace. */
    private static void cell(GuiGraphicsExtractor g, Font font, char c, int x, int y, int col, int row, int cellW, int cellH, int color) {
        String s = String.valueOf(c);
        g.text(font, s, x + col * cellW + (cellW - font.width(s)) / 2, y + row * cellH, color, false);
    }
}
