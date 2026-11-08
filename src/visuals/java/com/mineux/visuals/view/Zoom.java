package com.mineux.visuals.view;

import com.mineux.core.input.Keys;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

/**
 * Zoom: while its key is held, the field of view narrows by {@link ViewConfig#zoomFactor}, with
 * the mouse slowed by as much so a movement of it covers the same part of the picture.
 * {@link ViewConfig#ZOOM_INSTANT} goes straight there and back; {@link ViewConfig#ZOOM_SMOOTH}
 * eases over {@link ViewConfig#zoomTime} along {@link ZoomCurve}.
 * <p>
 * The field of view is the frame's own: the loader's {@code ZoomFovMixin} hands every frame's
 * worked-out field of view ({@code Camera#calculateFov}, sprinting, the spyglass and the camera
 * module's no fov already in it) to {@link #fov}, which divides it. Nothing is written into the
 * video setting, which holds whole degrees only -- the old zoom wrote it, and a whole degree at
 * the narrow end of a zoom is a jump of a tenth of the picture, which is what made it judder.
 * <p>
 * The mouse is the sensitivity setting, written straight into its field (its own setter refuses
 * values off the slider): the aim modules read that setting to turn their angles into mouse counts,
 * so they keep aiming true while zoomed. It is put back the moment the zoom is out, a screen is
 * open (the options screen shows and saves it), no world is drawn, or the options are about to
 * be saved ({@code OP_OPTIONS_SAVE}: F11 and some debug keys save with no screen open).
 */
public final class Zoom {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/view");

    private static Field value;
    private static boolean broken;

    /** How far through the move in, 0 to 1; see {@link ZoomCurve#advance}. */
    private static double progress;
    private static long lastFrame;

    /** The sensitivity as it was before zooming; null while it is the user's own. */
    private static Double savedSensitivity;

    private Zoom() {}

    /** {@code OP_ZOOM_FOV}, once a frame: the field of view to draw with instead of {@code fov}. */
    public static double fov(Minecraft mc, float fov) {
        if (broken) return fov;
        ViewConfig config = ViewConfig.get();
        long now = System.nanoTime();
        double seconds = frame(lastFrame, now);
        boolean screen = mc.gui.screen() != null;
        boolean want = held(mc, config, screen);
        progress = config.zoomEases()
            ? ZoomCurve.advance(progress, want, seconds, config.zoomTime)
            : (want ? 1.0 : 0.0);
        double zoom = ZoomCurve.zoom(progress, config.zoomFactor);
        if (zoom <= 1.0 || screen) {
            restore(mc.options);
            return zoom <= 1.0 ? fov : fov / zoom;
        }
        slowMouse(mc.options, ZoomCurve.turnScale(fov, zoom));
        return fov / zoom;
    }

    /**
     * The seconds since the last frame, {@code now} kept as the last. The field comes in as a
     * parameter: neither a local copy of it (the obfuscator would read the field again after it
     * is written) nor a call reading it after the write (the obfuscator may move the call past
     * it) -- HoistCheck.
     */
    private static double frame(long last, long now) {
        lastFrame = now;
        return ZoomCurve.frameSeconds(last, now);
    }

    private static boolean held(Minecraft mc, ViewConfig config, boolean screen) {
        if (!config.zoom || screen || mc.player == null || config.zoomKey == Keys.UNBOUND) return false;
        return Keys.isDown(mc.getWindow(), config.zoomKey);
    }

    /** Once a tick: the mouse put back when no world is being drawn (the menus, a disconnect). */
    public static void onClientTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            progress = 0.0;
            restore(mc.options);
        }
    }

    /** {@code OP_OPTIONS_SAVE}: the user's own sensitivity into the file; the next frame slows it again. */
    public static void beforeSave(Options options) {
        restore(options);
    }

    private static void slowMouse(Options options, double scale) {
        try {
            if (savedSensitivity == null) savedSensitivity = options.sensitivity().get();
            set(options.sensitivity(), Double.valueOf(ZoomCurve.sensitivity(savedSensitivity, scale)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            LOGGER.error("Zoom could not reach the mouse sensitivity and is off until the game restarts", e);
            restore(options);
        }
    }

    private static void restore(Options options) {
        restoreTo(options, savedSensitivity);
    }

    /** {@code saved}: the user's own sensitivity, a parameter rather than a local copy of the field (HoistCheck). */
    private static void restoreTo(Options options, Double saved) {
        if (saved == null) return;
        savedSensitivity = null;
        try {
            set(options.sensitivity(), saved);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            LOGGER.error("Zoom could not put the mouse sensitivity back", e);
        }
    }

    private static void set(OptionInstance<?> option, Object to) throws ReflectiveOperationException {
        if (value == null) {
            Field field = OptionInstance.class.getDeclaredField("value");
            field.setAccessible(true);
            value = field;
        }
        value.set(option, to);
    }
}
