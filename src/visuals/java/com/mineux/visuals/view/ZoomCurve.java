package com.mineux.visuals.view;

/**
 * The zoom's arithmetic, apart from the game so it can be tested: how far in the zoom is after a
 * frame, how many times closer that is, and what the mouse has to become to match.
 * <p>
 * Three choices make it smooth where the old zoom was not:
 * <ul>
 *   <li><b>Time, not steps.</b> How far in is a progress from 0 to 1 that moves by the frame's own
 *       length over the zoom time, so a 60 fps and a 240 fps frame-rate trace the same curve, and
 *       the end is reached exactly when the time is up -- no tail that creeps for half a second.</li>
 *   <li><b>An S curve.</b> The progress goes through smoothstep, which starts and stops with no
 *       speed at all and never leaves 0..1: no kick on the press, no bump at the end, nothing to
 *       overshoot.</li>
 *   <li><b>Magnification, not degrees.</b> The curve runs over the logarithm of the zoom, so each
 *       slice of the move looks like the same amount of closer. Dividing the field of view
 *       straight would spend most of the move's look in its last few frames, where a degree is
 *       a large part of a narrow picture.</li>
 * </ul>
 */
final class ZoomCurve {

    /**
     * The longest a frame is taken to be, in seconds. A stall (a chunk load, the window dragged)
     * slows the zoom for that frame rather than letting it jump, and the first frame after the
     * game has not drawn a world for a while counts as no more than this.
     */
    static final double LONGEST_FRAME = 0.05;

    private ZoomCurve() {}

    /** A frame's length from two {@code System.nanoTime} readings; 0 for the first frame (no last one). */
    static double frameSeconds(long last, long now) {
        if (last == 0L || now <= last) return 0.0;
        return Math.min(LONGEST_FRAME, (now - last) / 1.0E9);
    }

    /**
     * The progress after a frame of {@code seconds}: toward 1 while zooming in, toward 0 while not,
     * at one whole move per {@code duration}. Turning round part way goes back from where it is.
     */
    static double advance(double progress, boolean in, double seconds, double duration) {
        double step = duration <= 0.0 ? 1.0 : seconds / duration;
        double next = in ? progress + step : progress - step;
        return Math.max(0.0, Math.min(1.0, next));
    }

    /** Smoothstep: 0 at 0, 1 at 1, flat at both ends, rising in between and nowhere else. */
    static double ease(double progress) {
        double p = Math.max(0.0, Math.min(1.0, progress));
        return p * p * (3.0 - 2.0 * p);
    }

    /** How many times closer at that progress: 1 at 0, the factor at 1, eased in between on the log of it. */
    static double zoom(double progress, double factor) {
        if (factor <= 1.0) return 1.0;
        if (progress >= 1.0) return factor;
        return Math.exp(ease(progress) * Math.log(factor));
    }

    /**
     * How much of a turn per mouse count keeps a movement of the mouse the same size on the screen
     * at that zoom: the ratio of the half-angle tangents of the zoomed and the plain field of view,
     * which is how much smaller everything is drawn at the middle of the picture.
     */
    static double turnScale(double fovDegrees, double zoom) {
        if (zoom <= 1.0) return 1.0;
        double half = Math.toRadians(Math.max(1.0, Math.min(170.0, fovDegrees))) / 2.0;
        return Math.tan(half / zoom) / Math.tan(half);
    }

    /**
     * The mouse sensitivity setting that turns {@code scale} times as far per count as
     * {@code sensitivity} does. The game turns by {@code (s * 0.6 + 0.2)} cubed (as floats widened,
     * exactly as {@code MouseHandler.turnPlayer} and the aim modules write it), so the cube root
     * of the scale goes on that, and the setting is worked back out of it. It may come out below
     * the slider's 0 for a slow mouse at a deep zoom; it is written straight into the setting's
     * field, never through its slider, and the turn it makes is still the right one.
     */
    static double sensitivity(double sensitivity, double scale) {
        double base = sensitivity * 0.6F + 0.2F;
        double wanted = base * Math.cbrt(scale);
        return (wanted - 0.2F) / 0.6F;
    }
}
