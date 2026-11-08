package com.mineux.core.ui.term;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * pipes.sh, run inside a terminal: a few pipes crawling a grid, turning at random, each
 * stretch fading out a while after it was laid so the window never fills.
 * <p>
 * Stored and drawn as strokes -- one rectangle per straight stretch, from the centre of the
 * cell it started in to the centre of the cell it has reached -- rather than one or two per
 * cell. A pipe turns about one step in seven, so that is several times fewer rectangles a
 * frame, which is the whole cost of this: the first version drew per cell, filled the screen
 * behind the desktop, and was most of what the panel's frame rate went on.
 */
final class Pipes {

    static final int CELL = 7;
    private static final int THICK = 2;
    /** Ticks a stretch lasts once the pipe has left it, fading all the way out. */
    private static final int LIFE = 70;
    private static final int PIPES = 3;
    private static final int STEPS_PER_TICK = 2;
    private static final int TURN_PERCENT = 14;

    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DY = {-1, 0, 1, 0};

    private static final int[] COLORS = {
        0xFFEFC648, 0xFF5AF78E, 0xFF57C7FF, 0xFFFF6AC1, 0xFFFF5C57, 0xFF6C9EFF, 0xFFFFA928, 0xFFF1F1F0
    };

    /** One straight stretch of pipe, in cells, both ends inclusive. */
    private static final class Stroke {
        int x0, y0, x1, y1;
        final int color;
        int age;

        Stroke(int x, int y, int color) {
            this.x0 = this.x1 = x;
            this.y0 = this.y1 = y;
            this.color = color;
        }
    }

    private final Random random = new Random();
    private final List<Stroke> strokes = new ArrayList<>();

    private int cols, rows;
    private final int[] px = new int[PIPES], py = new int[PIPES], dir = new int[PIPES], pc = new int[PIPES];
    private final Stroke[] current = new Stroke[PIPES];

    /** Takes a new grid size; a change starts the pipes over, the way resizing a terminal redraws it. */
    void resize(int width, int height) {
        int c = Math.max(2, width / CELL);
        int r = Math.max(2, height / CELL);
        if (c == cols && r == rows) return;
        cols = c;
        rows = r;
        reset();
    }

    void reset() {
        strokes.clear();
        for (int i = 0; i < PIPES; i++) spawn(i);
    }

    private void spawn(int i) {
        int edge = random.nextInt(4);
        dir[i] = (edge + 2) & 3;
        px[i] = edge == 1 ? cols - 1 : edge == 3 ? 0 : random.nextInt(cols);
        py[i] = edge == 2 ? rows - 1 : edge == 0 ? 0 : random.nextInt(rows);
        pc[i] = COLORS[random.nextInt(COLORS.length)];
        current[i] = null;
    }

    void tick() {
        if (cols == 0) return;

        Iterator<Stroke> it = strokes.iterator();
        while (it.hasNext()) {
            if (++it.next().age > LIFE) it.remove();
        }

        for (int step = 0; step < STEPS_PER_TICK; step++) {
            for (int i = 0; i < PIPES; i++) advance(i);
        }
    }

    private void advance(int i) {
        int from = dir[i];
        int to = from;
        if (random.nextInt(100) < TURN_PERCENT) {
            to = (from + (random.nextBoolean() ? 1 : 3)) & 3;
            current[i] = null;
        }
        dir[i] = to;

        int nx = px[i] + DX[to];
        int ny = py[i] + DY[to];
        if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) {
            spawn(i);
            return;
        }

        Stroke stroke = current[i];
        if (stroke == null || !strokes.contains(stroke)) {
            stroke = new Stroke(px[i], py[i], pc[i]);
            strokes.add(stroke);
            current[i] = stroke;
        }

        // A stretch ages from its first cell: a pipe running straight a long way does not keep the whole run fresh.
        stroke.x1 = nx;
        stroke.y1 = ny;
        px[i] = nx;
        py[i] = ny;
    }

    void render(GuiGraphicsExtractor g, int x0, int y0) {
        int half = CELL / 2;

        for (Stroke s : strokes) {
            float fade = 1f - (float) s.age / LIFE;
            int c = withAlpha(s.color, fade * fade);

            int ax = x0 + Math.min(s.x0, s.x1) * CELL + half - THICK / 2;
            int ay = y0 + Math.min(s.y0, s.y1) * CELL + half - THICK / 2;
            int bx = x0 + Math.max(s.x0, s.x1) * CELL + half - THICK / 2;
            int by = y0 + Math.max(s.y0, s.y1) * CELL + half - THICK / 2;
            g.fill(ax, ay, bx + THICK, by + THICK, c);
        }

        for (int i = 0; i < PIPES; i++) {
            int hx = x0 + px[i] * CELL + half - 2;
            int hy = y0 + py[i] * CELL + half - 2;
            g.fill(hx, hy, hx + 4, hy + 4, pc[i]);
        }
    }

    /** Rectangles a frame draws, heads aside. */
    int strokeCount() {
        return strokes.size();
    }

    static int withAlpha(int argb, float a) {
        int alpha = Math.max(0, Math.min(255, Math.round(((argb >>> 24) & 0xFF) * a)));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }
}
