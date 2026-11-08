package com.mineux.visuals.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The zoom's curve: the same at any frame rate, there on time, never past the factor and never
 * back the wrong way; and a mouse that turns as much less as the picture is closer.
 */
class ZoomCurveTest {

    private static final double FACTOR = 4.0;
    private static final double TIME = 0.25;

    /** The zoom after {@code seconds} of holding the key, at {@code fps} frames a second. */
    private static double zoomAfter(double seconds, int fps) {
        double progress = 0.0;
        int frames = (int) Math.round(seconds * fps);
        for (int i = 0; i < frames; i++) progress = ZoomCurve.advance(progress, true, 1.0 / fps, TIME);
        return ZoomCurve.zoom(progress, FACTOR);
    }

    @Test
    void sixtyAndTwoFortyFramesASecondTraceTheSameCurve() {
        // Times every one of the frame-rates lands on a frame of.
        for (int k = 1; k <= 6; k++) {
            double t = k * 0.05;
            double at60 = zoomAfter(t, 60);
            assertEquals(at60, zoomAfter(t, 240), 1e-9, "60 and 240 fps apart at " + t + " s");
            assertEquals(at60, zoomAfter(t, 120), 1e-9, "60 and 120 fps apart at " + t + " s");
            assertEquals(at60, zoomAfter(t, 20), 1e-9, "60 and 20 fps apart at " + t + " s");
        }
    }

    @Test
    void unevenFramesLandWhereEvenOnesDo() {
        // A frame of 4 ms, then one of 16 ms, over and over: a frame-rate that hitches.
        double progress = 0.0;
        for (int i = 0; i < 10; i++) {
            double frame = i % 2 == 0 ? 0.004 : 0.016;
            progress = ZoomCurve.advance(progress, true, frame, TIME);
        }
        // Ten of them come to 0.1 s.
        assertEquals(zoomAfter(0.1, 100), ZoomCurve.zoom(progress, FACTOR), 1e-9);
    }

    @Test
    void theZoomIsReachedExactlyWhenTheTimeIsUpAndStays() {
        assertEquals(FACTOR, zoomAfter(TIME, 60), 0.0, "not there after the zoom time at 60 fps");
        assertEquals(FACTOR, zoomAfter(TIME, 240), 0.0, "not there after the zoom time at 240 fps");
        assertEquals(FACTOR, zoomAfter(5.0, 240), 0.0, "held on, it moved off the factor");
    }

    @Test
    void zoomingInOnlyEverGoesCloserAndNeverPastTheFactor() {
        double progress = 0.0;
        double last = 1.0;
        for (int i = 0; i < 240; i++) {
            progress = ZoomCurve.advance(progress, true, 1.0 / 240, TIME);
            double zoom = ZoomCurve.zoom(progress, FACTOR);
            assertTrue(zoom >= last, "went back out at frame " + i);
            assertTrue(zoom <= FACTOR, "overshot the factor at frame " + i + ": " + zoom);
            last = zoom;
        }
    }

    @Test
    void lettingGoComesAllTheWayBackWithoutGoingWide() {
        double progress = 1.0;
        double last = FACTOR;
        for (int i = 0; i < 240; i++) {
            progress = ZoomCurve.advance(progress, false, 1.0 / 240, TIME);
            double zoom = ZoomCurve.zoom(progress, FACTOR);
            assertTrue(zoom <= last, "went back in at frame " + i);
            assertTrue(zoom >= 1.0, "wider than no zoom at frame " + i + ": " + zoom);
            last = zoom;
        }
        assertEquals(1.0, last, 0.0);
    }

    @Test
    void theCurveStartsAndStopsGently() {
        // Smoothstep's slope is 0 at both ends: the first and last frames move least.
        double first = ZoomCurve.zoom(ZoomCurve.advance(0.0, true, 1.0 / 240, TIME), FACTOR) - 1.0;
        double middle = ZoomCurve.zoom(0.5 + 1.0 / 240 / TIME, FACTOR) - ZoomCurve.zoom(0.5, FACTOR);
        double end = FACTOR - ZoomCurve.zoom(1.0 - 1.0 / 240 / TIME, FACTOR);
        assertTrue(first < middle / 10, "the press kicks: " + first + " against " + middle);
        assertTrue(end < middle / 10, "the end bumps: " + end + " against " + middle);
    }

    @Test
    void turningRoundPartWayGoesBackFromWhereItIs() {
        double progress = 0.0;
        for (int i = 0; i < 30; i++) progress = ZoomCurve.advance(progress, true, 1.0 / 240, TIME);
        double before = ZoomCurve.zoom(progress, FACTOR);
        double onwards = ZoomCurve.zoom(ZoomCurve.advance(progress, true, 1.0 / 240, TIME), FACTOR) - before;
        progress = ZoomCurve.advance(progress, false, 1.0 / 240, TIME);
        double back = before - ZoomCurve.zoom(progress, FACTOR);
        // The frame out moves no more than a frame in would have: no snap toward either end.
        assertTrue(back > 0, "letting go did not start back out");
        assertEquals(onwards, back, onwards * 0.1, "a jump on letting go: " + back + " against " + onwards);
    }

    @Test
    void aLongFrameIsCappedAndTheFirstOneIsNothing() {
        assertEquals(0.0, ZoomCurve.frameSeconds(0L, 123_456_789L), 0.0);
        assertEquals(ZoomCurve.LONGEST_FRAME, ZoomCurve.frameSeconds(1L, 10_000_000_000L), 0.0);
        assertEquals(0.004, ZoomCurve.frameSeconds(1_000_000L, 5_000_000L), 1e-12);
    }

    @Test
    void theMouseTurnsAsMuchLessAsThePictureIsCloser() {
        double fov = 70.0;
        for (double zoom = 1.5; zoom <= 12.0; zoom += 1.5) {
            double scale = ZoomCurve.turnScale(fov, zoom);
            double half = Math.toRadians(fov) / 2;
            assertEquals(Math.tan(half / zoom) / Math.tan(half), scale, 1e-12);
            for (double s = 0.0; s <= 1.0; s += 0.25) {
                double slowed = ZoomCurve.sensitivity(s, scale);
                double plain = Math.pow(s * 0.6F + 0.2F, 3);
                double turn = Math.pow(slowed * 0.6F + 0.2F, 3);
                assertEquals(scale, turn / plain, 1e-9, "sensitivity " + s + " at zoom " + zoom);
            }
        }
        assertEquals(1.0, ZoomCurve.turnScale(fov, 1.0), 0.0);
        assertEquals(0.5, ZoomCurve.sensitivity(0.5, 1.0), 1e-12);
    }
}
