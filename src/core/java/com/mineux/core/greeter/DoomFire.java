package com.mineux.core.greeter;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Random;

/**
 * ly's "doom" animation: the PSX Doom title fire, burning along the bottom of the greeter.
 * <p>
 * The classic algorithm (Fabien Sanglard's write-up of it): the bottom row is held white-hot,
 * and every step each cell copies the one below it, a little cooler and nudged sideways at
 * random, so the heat climbs, flickers and dies out on its way up. It steps at a fixed rate
 * whatever the frame rate, and is drawn a row at a time with neighbouring cells of the same
 * colour merged into one fill, which keeps it to a few hundred fills a frame rather than one
 * per cell.
 */
public final class DoomFire {

    /** Doom's own 37-step palette, black through red and orange to white. */
    private static final int[] PALETTE = {
        0x070707, 0x1F0707, 0x2F0F07, 0x470F07, 0x571707, 0x671F07, 0x771F07, 0x8F2707,
        0x9F2F07, 0xAF3F07, 0xBF4707, 0xC74707, 0xDF4F07, 0xDF5707, 0xDF5707, 0xD75F07,
        0xD75F07, 0xD7670F, 0xCF6F0F, 0xCF770F, 0xCF7F0F, 0xCF8717, 0xC78717, 0xC78F17,
        0xC7971F, 0xBF9F1F, 0xBF9F1F, 0xBFA727, 0xBFA727, 0xBFAF2F, 0xB7AF2F, 0xB7B72F,
        0xB7B737, 0xCFCF6F, 0xDFDF9F, 0xEFEFC7, 0xFFFFFF,
    };

    private static final int ROWS = 30;
    private static final long STEP_NS = 1_000_000_000L / 28;

    private final Random random = new Random();
    private int cols, cell;
    private int[] heat;
    private long lastStep = System.nanoTime();

    /** Sizes the grid for a screen width; a new width starts the fire over. */
    private void fit(int width) {
        int size = Math.max(3, Math.round(width / 170f));
        int count = width / size + 2;
        if (heat != null && count == cols && size == cell) return;
        cell = size;
        cols = count;
        heat = new int[cols * ROWS];
        for (int x = 0; x < cols; x++) heat[(ROWS - 1) * cols + x] = PALETTE.length - 1;
    }

    private void step() {
        for (int x = 0; x < cols; x++) {
            for (int y = 1; y < ROWS; y++) {
                int from = y * cols + x;
                int value = heat[from];
                if (value == 0) {
                    heat[from - cols] = 0;
                    continue;
                }
                int roll = random.nextInt(4);
                int to = from - cols - roll + 1;
                if (to < 0 || to >= heat.length) continue;
                // Cools about a step and a half a row, so the flames die out inside the grid
                // rather than being cut off by its top edge.
                heat[to] = Math.max(0, value - random.nextInt(4));
            }
        }
    }

    /**
     * Burns along the bottom of the screen.
     *
     * @param alpha 0..1, for the fade out after a sign-in
     */
    public void draw(GuiGraphicsExtractor g, int width, int height, float alpha) {
        fit(width);

        long now = System.nanoTime();
        int steps = 0;
        while (now - lastStep >= STEP_NS && steps++ < 3) {
            step();
            lastStep += STEP_NS;
        }
        if (now - lastStep > STEP_NS * 4) lastStep = now;

        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255) << 24;
        int top = height - ROWS * cell;

        for (int y = 0; y < ROWS; y++) {
            int py = top + y * cell;
            int x = 0;
            while (x < cols) {
                int index = heat[y * cols + x];
                int run = x + 1;
                while (run < cols && heat[y * cols + run] == index) run++;
                if (index > 0) g.fill(x * cell, py, run * cell, py + cell, a | PALETTE[index]);
                x = run;
            }
        }
    }
}
