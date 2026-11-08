package com.mineux.visuals.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shapes out of the GUI's rectangles: bars turned to any angle, rings and arcs, discs. Every
 * coordinate is in the pose's own units and may be fractional -- {@code fill} takes whole
 * numbers, so each rectangle is drawn four times as large under a quarter scale.
 */
final class Strokes {

    private static final float TURN = (float) (Math.PI * 2.0);

    private Strokes() {}

    /** An axis-aligned rectangle in the pose's units. */
    static void rect(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int color) {
        if ((color >>> 24) == 0) return;
        g.pose().pushMatrix();
        g.pose().scale(0.25F, 0.25F);
        g.fill(Math.round(x0 * 4.0F), Math.round(y0 * 4.0F), Math.round(x1 * 4.0F), Math.round(y1 * 4.0F), color);
        g.pose().popMatrix();
    }

    /** A bar out from the middle at {@code degrees} (0 is right, 90 down), from {@code r0} to {@code r1}. */
    static void spoke(GuiGraphicsExtractor g, float degrees, float r0, float r1, float thick, int color) {
        g.pose().pushMatrix();
        g.pose().rotate((float) Math.toRadians(degrees));
        float half = thick * 0.5F;
        rect(g, r0, 0.0F - half, r1, half, color);
        g.pose().popMatrix();
    }

    /** A bar from one point to another. */
    static void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float thick, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0E-3F) return;
        g.pose().pushMatrix();
        g.pose().translate(x0, y0);
        g.pose().rotate((float) Math.atan2(dy, dx));
        float half = thick * 0.5F;
        // Out past each end by half the thickness, so two bars meet without a notch.
        rect(g, 0.0F - half, 0.0F - half, length + half, half, color);
        g.pose().popMatrix();
    }

    /** An arc of a ring round the middle, from {@code from} to {@code to} in turns (0 at the top, clockwise). */
    static void arc(GuiGraphicsExtractor g, float radius, float thick, float from, float to, int color) {
        if (radius <= 0.0F || to <= from) return;
        float span = to - from;
        int segments = Math.max(3, Math.round(Math.max(12.0F, radius * 2.4F) * span));
        float step = span / segments;
        // Each piece a little longer than its share of the ring, so the joins do not show.
        float half = (float) (Math.PI * radius * step) * 1.15F + 0.1F;
        float lo = 0.0F - radius - thick * 0.5F;
        float hi = 0.0F - radius + thick * 0.5F;
        for (int i = 0; i < segments; i++) {
            float turn = from + step * (i + 0.5F);
            g.pose().pushMatrix();
            g.pose().rotate(turn * TURN);
            rect(g, 0.0F - half, lo, half, hi, color);
            g.pose().popMatrix();
        }
    }

    static void ring(GuiGraphicsExtractor g, float radius, float thick, int color) {
        arc(g, radius, thick, 0.0F, 1.0F, color);
    }

    /** A round dot: four squares a sixteenth of a turn apart. */
    static void disc(GuiGraphicsExtractor g, float radius, int color) {
        if (radius <= 0.0F) return;
        float half = radius * 0.86F;
        for (int i = 0; i < 4; i++) {
            g.pose().pushMatrix();
            g.pose().rotate(TURN / 16.0F * i);
            rect(g, 0.0F - half, 0.0F - half, half, half, color);
            g.pose().popMatrix();
        }
    }

    /** {@code t} from 0 to 1, fast at first and slow to land. */
    static float easeOut(float t) {
        float clamped = Math.max(0.0F, Math.min(1.0F, t));
        float rest = 1.0F - clamped;
        return 1.0F - rest * rest * rest;
    }
}
