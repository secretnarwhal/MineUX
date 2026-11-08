package com.mineux.core.greeter;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Arrays;
import java.util.Random;

/**
 * The greeter's default animation: a pour. The screen is black; a thin stream drops
 * from the top, splashes off the floor and slowly thickens; the liquid rises from the bottom,
 * sloshing, foam on its surface and a plume of bubbles where the stream goes in, until the
 * whole background is under it -- and that stays, as a see-through tint over the black, with
 * bubbles still rising.
 * <p>
 * The stream and the liquid are one material: the same see-through body, lit along its edges
 * the way liquid catches light, so the stream reads as the liquid falling rather than a line
 * drawn over it. The colour and how strong the tint is come from the dotfile
 * ({@code ly/config.ini}).
 * <p>
 * The surface is a sum of a slow standing slosh (energy that builds while the stream pours and
 * dies away once it stops), ripples spreading from where the stream lands, and a little chop.
 * It is drawn as columns a couple of pixels wide: a few hundred fills while the liquid rises,
 * one once it is full.
 * <p>
 * The main menu carries the finished state on as its own background ({@link #drawSettled}):
 * the tint and the bubbles, with no stream and no surface.
 * <p>
 * The plume -- the bubbles the stream drives into the liquid -- is kept apart from the
 * background's steady bubbles: each one is its own particle, with no cap on how many there are,
 * and it lives until it leaves the top of the screen (or, while the liquid is still rising,
 * bursts at its surface). It is shared by every pour rather than owned by one screen, so the
 * main menu carries on the bubbles still rising when the greeter hands over to it.
 */
public final class StreamFill {

    /** Black first, then the fall, then a moment of stream hitting the floor before the liquid rises. */
    private static final long DARK = 1400, FALL = 900, FILL_AT = DARK + FALL + 1600, FILL = 6500, STOP = 700;
    private static final int COLUMN = 2;
    private static final int BUBBLES = 46;

    private final int body, edge, foam, shine, bubble, settled;
    private final float across;

    // ---- particles: bubbles rising from the impact, and droplets splashing ----
    // Static: they outlive the screen that made them (see the class comment). No cap -- the
    // arrays grow -- and nothing removes one but its own end.
    private static float[] px = new float[256], py = new float[256];
    private static float[] vx = new float[256], vy = new float[256];
    private static boolean[] drop = new boolean[256];
    private static int count;
    /** Wall clock of the last step, whichever screen took it. */
    private static long lastStep;
    private float spawnDebt;
    private final Random random = new Random();

    /** The pour as the dotfile ({@code ly/config.ini}) describes it, read now. */
    public static StreamFill ofDotfile() {
        LyConfig ly = LyConfig.load();
        return new StreamFill(ly.streamColor, ly.tintOpacity, ly.streamX);
    }

    public StreamFill(int color, float tint, float across) {
        int rgb = color & 0xFFFFFF;
        this.across = across;
        this.body = argb(tint, rgb);
        this.edge = argb(Math.min(1f, tint + 0.5f), mix(rgb, 0xFFFFFF, 0.3f));
        this.foam = argb(Math.min(1f, tint + 0.5f), mix(rgb, 0xFFFFFF, 0.75f));
        this.shine = argb(Math.min(1f, tint + 0.3f), mix(rgb, 0xFFFFFF, 0.6f));
        this.bubble = argb(Math.min(1f, tint + 0.4f), mix(rgb, 0xFFFFFF, 0.7f));
        this.settled = argb(Math.min(1f, tint + 0.15f), mix(rgb, 0xFFFFFF, 0.5f));
    }

    /**
     * The pour once it is over: the tint over everything and the bubbles rising through it, for
     * a screen that carries the greeter's background on.
     *
     * @param alpha 0..1, for fading it in
     */
    public void drawSettled(GuiGraphicsExtractor g, int width, int height, float alpha) {
        if (alpha <= 0f) return;
        float t = System.currentTimeMillis() % 3_600_000L / 1000f;
        g.fill(0, 0, width, height, scale(body, alpha));
        int bubbleColor = scale(settled, alpha);
        for (int i = 0; i < BUBBLES; i++) {
            float x = hash(i * 3 + 1) * width;
            float speed = 14 + hash(i * 3 + 2) * 34;
            float span = height + 20;
            float y = height - ((t * speed + hash(i * 3 + 3) * span) % span);
            int size = i % 5 == 0 ? 2 : 1;
            int bx = Math.round(x + (float) Math.sin(t * 2 + i) * 1.5f);
            g.fill(bx, Math.round(y), bx + size, Math.round(y) + size, bubbleColor);
        }
        // Whatever of the greeter's plume is still rising.
        move(g, width, height, 0, -height, t, stepDt(), 0f, 0f, true, scale(bubble, alpha), scale(edge, alpha));
    }

    /**
     * The pour run backwards, for a reboot: the settled tint drains away down a whirlpool under
     * where the stream came in -- the surface sloshing and foaming as it falls, the funnel
     * widening and deepening as the last of it goes, the bubbles bursting at it -- until the
     * screen is black. At 0 it is {@link #drawSettled}, so it takes over from that seamlessly.
     *
     * @param drained 0 (full) to 1 (empty)
     * @param seconds since the drain began, for the slosh
     * @param alpha   0..1, for fading it in over what was on screen
     */
    public void drawDraining(GuiGraphicsExtractor g, int width, int height, float drained, float seconds, float alpha) {
        float dt = stepDt();
        if (alpha <= 0f || drained >= 1f) return;
        int sx = Math.round(width * across);
        // From just above the top, so the surface is off screen at 0, to far enough below the
        // bottom that no slosh or funnel is left on screen at 1.
        float level = -18 + drained * (height + 60);
        float energy = Math.min(1f, seconds / 1.5f);
        int bodyColor = scale(body, alpha), edgeColor = scale(edge, alpha), foamColor = scale(foam, alpha);

        for (int x = 0; x < width; x += COLUMN) {
            int y = Math.round(drainSurface(x, width, sx, level, seconds, energy, drained));
            if (y >= height) continue;
            int top = Math.max(0, y);
            g.fill(x, top, x + COLUMN, height, bodyColor);
            g.fill(x, top, x + COLUMN, Math.max(0, y + 2), edgeColor);
            g.fill(x, Math.max(0, y + 2), x + COLUMN, Math.max(0, y + 4), foamColor);
        }

        // The settled screen's bubbles, on the same clock, so they carry straight on.
        float t = System.currentTimeMillis() % 3_600_000L / 1000f;
        int bubbleColor = scale(settled, alpha);
        for (int i = 0; i < BUBBLES; i++) {
            float x = hash(i * 3 + 1) * width;
            float speed = 14 + hash(i * 3 + 2) * 34;
            float span = height + 20;
            float y = height - ((t * speed + hash(i * 3 + 3) * span) % span);
            if (y < level + 6) continue;
            int size = i % 5 == 0 ? 2 : 1;
            int bx = Math.round(x + (float) Math.sin(t * 2 + i) * 1.5f);
            g.fill(bx, Math.round(y), bx + size, Math.round(y) + size, bubbleColor);
        }
        // Whatever of the greeter's plume is still rising bursts at the falling surface.
        move(g, width, height, sx, level, seconds, dt, energy, 0f, false, scale(bubble, alpha), edgeColor);
    }

    /** The draining surface's height at x: the level, the slosh and chop, and the funnel over the drain. */
    private static float drainSurface(int x, int width, int sx, float level, float t, float energy, float drained) {
        double u = x / (double) Math.max(1, width);
        double slosh = Math.sin(Math.PI * 2 * u + 0.4) * Math.cos(t * 2.1) * 6.0
            + Math.sin(Math.PI * 4 * u + 1.1) * Math.cos(t * 3.3 + 1.0) * 2.5;
        double chop = Math.sin(x * 0.045 + t * 2.6) * 1.1 + Math.sin(x * 0.013 - t * 1.4) * 1.6;
        double d = Math.abs(x - sx);
        double reach = 50 + 110 * drained;
        double funnel = (6 + 34 * drained) * Math.exp(-d * d / (reach * reach));
        // Rings running in towards the drain rather than out from a stream.
        double rings = Math.sin(d * 0.16 + t * 7.0) * Math.exp(-d / 140.0) * 2.0;
        return (float) (level + slosh * energy + chop + funnel + rings * energy);
    }

    /** Seconds since the last step, whichever screen took it; capped, so a pause is not a jump. */
    private static float stepDt() {
        long now = System.currentTimeMillis();
        float dt = lastStep == 0 ? 0f : Math.max(0f, Math.min(0.05f, (now - lastStep) / 1000f));
        lastStep = now;
        return dt;
    }

    private static int scale(int argb, float alpha) {
        int a = Math.round((argb >>> 24) * Math.max(0f, Math.min(1f, alpha)));
        return a << 24 | (argb & 0xFFFFFF);
    }

    /** @param ms since the screen was first actually showing */
    public void draw(GuiGraphicsExtractor g, int width, int height, long ms) {
        float dt = stepDt();
        if (ms < DARK) return;

        float t = ms / 1000f;
        int sx = Math.round(width * across);

        float p = ms < FILL_AT ? 0f : Math.min(1f, (ms - FILL_AT) / (float) FILL);
        float rise = p * p * (3 - 2 * p);
        float level = height - rise * (height + 14);
        boolean full = p >= 1f;

        long end = FILL_AT + FILL;
        boolean pouring = ms >= DARK + FALL && ms < end;
        // Slosh energy: builds while pouring, dies away after.
        float energy = ms < end ? Math.min(1f, Math.max(0f, (ms - FILL_AT) / 1500f)) : (float) Math.exp((end - ms) / 1600.0);
        float ripple = pouring && rise > 0 ? 1f : 0f;

        if (full) {
            g.fill(0, 0, width, height, body);
        } else if (rise > 0) {
            for (int x = 0; x < width; x += COLUMN) {
                int y = Math.round(surface(x, width, sx, level, t, energy, ripple));
                if (y >= height) continue;
                int top = Math.max(0, y);
                g.fill(x, top, x + COLUMN, height, body);
                g.fill(x, top, x + COLUMN, Math.max(0, y + 2), edge);
                g.fill(x, Math.max(0, y + 2), x + COLUMN, Math.max(0, y + 4), foam);
            }
        }

        if (rise > 0) ambientBubbles(g, width, height, level, t, full);
        particles(g, width, height, sx, level, t, dt, energy, ripple, pouring, rise);
        // Where the stream meets the liquid: the lowest point of the surface under its whole
        // width, and a little below, so no slosh or ripple can ever leave a gap under it.
        float contact = height;
        if (rise > 0) {
            float lowest = -Float.MAX_VALUE;
            for (int x = sx - 7; x <= sx + 7; x++) lowest = Math.max(lowest, surface(x, width, sx, level, t, energy, ripple));
            contact = Math.min(height, lowest + 5);
        }
        stream(g, height, ms, sx, contact, t);
    }

    /** The surface's height at x: the level, the slosh, the ripples from the stream, and chop. */
    private static float surface(int x, int width, int sx, float level, float t, float energy, float ripple) {
        double u = x / (double) Math.max(1, width);
        double slosh = Math.sin(Math.PI * 2 * u + 0.4) * Math.cos(t * 2.1) * 6.0
            + Math.sin(Math.PI * 4 * u + 1.1) * Math.cos(t * 3.3 + 1.0) * 2.5;
        double chop = Math.sin(x * 0.045 + t * 2.6) * 1.1 + Math.sin(x * 0.013 - t * 1.4) * 1.6;
        double d = Math.abs(x - sx);
        double rings = Math.sin(d * 0.16 - t * 9.0) * Math.exp(-d / 110.0) * 3.2;
        double dip = d < 22 ? (22 - d) / 22.0 * 4.0 : 0;
        return (float) (level + slosh * energy + chop + (rings - dip) * ripple);
    }

    /** The stream: one pixel as it first falls, thickening, then running out once the screen is full. */
    private void stream(GuiGraphicsExtractor g, int height, long ms, int sx, float contact, float t) {
        long since = ms - DARK;
        long end = FILL_AT + FILL;

        float f = Math.min(1f, since / (float) FALL);
        float head = height * f * f;
        float s = ms < end ? 0f : Math.min(1f, (ms - end) / (float) STOP);
        float tail = height * s * s;
        float bottom = Math.min(head, contact);
        if (bottom <= tail || tail >= height) return;

        float grown = since < FALL ? 0f : Math.min(1f, (since - FALL) / 3200f);
        float w = 2f + grown * grown * 6f;

        // In short segments, each nudged sideways, so the stream wavers as poured liquid does.
        int seg = 5;
        for (int y = Math.round(tail); y < bottom; y += seg) {
            int y1 = Math.min(Math.round(bottom), y + seg);
            float wob = (float) Math.sin(y * 0.045 - t * 7.0) * 0.7f * (0.3f + grown);
            // Slightly narrower near the top, where it has not spread yet.
            float ww = w * (0.75f + 0.25f * y / (float) height);
            int x0 = Math.round(sx - ww / 2f + wob);
            int x1 = Math.max(x0 + 2, Math.round(sx + ww / 2f + wob));
            g.fill(x0, y, x1, y1, body);
            g.fill(x0, y, x0 + 1, y1, edge);
            g.fill(x1 - 1, y, x1, y1, edge);
            if (x1 - x0 >= 5) g.fill(x0 + 2, y, x0 + 3, y1, shine);
        }
    }

    /** Bubbles in the body, at fixed places so they read as steady streams. */
    private void ambientBubbles(GuiGraphicsExtractor g, int width, int height, float level, float t, boolean full) {
        for (int i = 0; i < BUBBLES; i++) {
            float x = hash(i * 3 + 1) * width;
            float speed = 14 + hash(i * 3 + 2) * 34;
            float span = height + 20;
            float y = height - ((t * speed + hash(i * 3 + 3) * span) % span);
            if (y < level + 6) continue;
            int size = i % 5 == 0 ? 2 : 1;
            int bx = Math.round(x + (float) Math.sin(t * 2 + i) * 1.5f);
            g.fill(bx, Math.round(y), bx + size, Math.round(y) + size, full ? settled : bubble);
        }
    }

    /**
     * The plume of bubbles the stream drives down into the liquid, and before there is any
     * liquid the droplets it throws up off the floor.
     */
    private void particles(GuiGraphicsExtractor g, int width, int height, int sx, float level, float t, float dt,
                           float energy, float ripple, boolean pouring, float rise) {
        if (pouring && dt > 0) {
            spawnDebt += dt * (rise > 0 ? 90f : 55f);
            while (spawnDebt >= 1f) {
                spawnDebt -= 1f;
                if (rise > 0) {
                    // Driven down by the stream from just under where it goes in -- under the
                    // surface actually there, not the flat level, or a slosh leaves it above
                    // the liquid and it bursts the frame it is made -- plunging deep before
                    // the buoyancy turns it round, some of them all the way to the bottom.
                    float x = sx + (float) random.nextGaussian() * 3f;
                    add(false, x, surface(Math.round(x), width, sx, level, t, energy, ripple) + 6,
                        (float) random.nextGaussian() * 14f, 110f + random.nextFloat() * 230f);
                } else {
                    add(true, sx + (float) random.nextGaussian() * 2f, height - 2,
                        (float) random.nextGaussian() * 45f, 0f - (40f + random.nextFloat() * 90f));
                }
            }
        }
        move(g, width, height, sx, level, t, dt, energy, ripple, rise >= 1f, bubble, edge);
    }

    private static void add(boolean isDrop, float x, float y, float dx, float dy) {
        if (count == px.length) {
            int n = count * 2;
            px = Arrays.copyOf(px, n);
            py = Arrays.copyOf(py, n);
            vx = Arrays.copyOf(vx, n);
            vy = Arrays.copyOf(vy, n);
            drop = Arrays.copyOf(drop, n);
        }
        int i = count++;
        drop[i] = isDrop;
        px[i] = x;
        py[i] = y;
        vx[i] = dx;
        vy[i] = dy;
    }

    /**
     * Moves and draws every particle, and lets each one go at its own end: a droplet once it
     * falls off the bottom; a bubble once it is entirely off the top of the screen -- or, while
     * the liquid is still rising, when it reaches the surface and bursts.
     *
     * @param full the liquid is over the whole screen: no surface left to burst at
     */
    private static void move(GuiGraphicsExtractor g, int width, int height, int sx, float level, float t, float dt,
                             float energy, float ripple, boolean full, int bubbleColor, int dropColor) {
        for (int i = 0; i < count; i++) {
            if (drop[i]) {
                vy[i] += 420f * dt;
                px[i] += vx[i] * dt;
                py[i] += vy[i] * dt;
            } else {
                // Buoyancy: slows the plunge, turns it round, and floats it up.
                vy[i] -= 210f * dt;
                if (vy[i] < -45f) vy[i] = -45f;
                vx[i] *= 1f - Math.min(1f, dt * 1.5f);
                px[i] += (vx[i] + (float) Math.sin(t * 3 + i) * 4f) * dt;
                py[i] += vy[i] * dt;
                // Down at the bottom it stops, and rises from there.
                if (py[i] > height - 2) {
                    py[i] = height - 2;
                    vy[i] = Math.min(vy[i], 0f);
                }
            }

            boolean gone;
            if (drop[i]) {
                gone = py[i] > height;
            } else {
                float top = full ? -3 : surface(Math.round(px[i]), width, sx, level, t, energy, ripple) + 3;
                gone = vy[i] < 0 && py[i] < top;
            }
            if (gone) {
                // Swap-remove.
                count--;
                px[i] = px[count];
                py[i] = py[count];
                vx[i] = vx[count];
                vy[i] = vy[count];
                drop[i] = drop[count];
                i--;
                continue;
            }

            int x = Math.round(px[i]), y = Math.round(py[i]);
            int size = drop[i] ? 1 : (i % 4 == 0 ? 2 : 1);
            g.fill(x, y, x + size, y + size, drop[i] ? dropColor : bubbleColor);
        }
    }

    private static float hash(int n) {
        int x = n * 0x45D9F3B;
        x = ((x >>> 16) ^ x) * 0x45D9F3B;
        x = (x >>> 16) ^ x;
        return (x & 0xFFFFFF) / (float) 0x1000000;
    }

    private static int argb(float alpha, int rgb) {
        return Math.round(Math.max(0f, Math.min(1f, alpha)) * 255) << 24 | (rgb & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int gr = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return r << 16 | gr << 8 | bl;
    }
}
