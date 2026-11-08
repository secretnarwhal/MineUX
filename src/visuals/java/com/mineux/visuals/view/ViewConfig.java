package com.mineux.visuals.view;

import com.mineux.core.config.ConfigFile;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Config file: config/mineux/view.json -- the zoom and clear weather. Neither is ever sent
 * anywhere: the field of view and the weather are only what this client draws.
 *
 * @see Zoom
 * @see Weather
 */
public final class ViewConfig implements ConfigFile.Backed {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/view");

    private static final ConfigFile<ViewConfig> FILE =
        new ConfigFile<>(ViewConfig.class, "view.json", ViewConfig::new, LOGGER);

    private static ViewConfig instance;

    /**
     * No rain or snow falling, and the sky as on a clear day: see {@link Weather}. Only what
     * your screen draws; the server's weather goes on as it was.
     */
    public boolean clearWeather = false;

    /** Zoom in while {@link #zoomKey} is held: see {@link Zoom}. */
    public boolean zoom = false;

    /** GLFW's key code, or a mouse button as {@code Keys#MOUSE_BASE - button}; C by default. */
    public int zoomKey = org.lwjgl.glfw.GLFW.GLFW_KEY_C;

    /** How many times closer: the field of view is divided by this. */
    public double zoomFactor = 4.0;

    /** {@link #zoomMode}: straight to the zoom and back, the mouse slowed to match at once. */
    public static final String ZOOM_INSTANT = "instant";

    /** {@link #zoomMode}: eased in and out over {@link #zoomTime}, the mouse slowed along the same curve. */
    public static final String ZOOM_SMOOTH = "smooth zoom";

    /** The ends of {@link #zoomTime}, in seconds. */
    public static final double MIN_ZOOM_TIME = 0.05;
    public static final double MAX_ZOOM_TIME = 1.0;

    /**
     * {@link #ZOOM_INSTANT} or {@link #ZOOM_SMOOTH}; a string, never an enum (the obfuscation
     * a host may apply renames enum constants). Null only in a file from before the mode, which
     * {@link #ensureDefaults} fills from {@link #zoomSmooth}.
     */
    public String zoomMode;

    /** How long smooth zoom takes to go all the way in, or all the way out. */
    public double zoomTime = 0.25;

    /**
     * Before {@link #zoomMode}: the cinematic camera while zoomed. Only read, once, to pick the
     * mode for a file written before it -- true is smooth zoom, false instant -- and then
     * dropped, so the file no longer carries it.
     */
    public Boolean zoomSmooth;

    public static synchronized ViewConfig get() {
        if (instance == null) instance = FILE.load();
        return instance;
    }

    @Override
    public void ensureDefaults() {

        zoomFactor = Math.max(1.5, Math.min(12.0, zoomFactor));
        zoomTime = Math.max(MIN_ZOOM_TIME, Math.min(MAX_ZOOM_TIME, zoomTime));
        zoomMode = zoomModeFor(zoomMode, zoomSmooth);
        zoomSmooth = null;
    }

    /**
     * The mode a loaded file means: its own when it is one of the two, else the old switch's
     * (true smooth, false instant), else -- a first run, or a hand-edited word -- smooth zoom.
     */
    static String zoomModeFor(String mode, Boolean oldSmooth) {
        if (Objects.equals(ZOOM_INSTANT, mode)) return ZOOM_INSTANT;
        if (Objects.equals(ZOOM_SMOOTH, mode)) return ZOOM_SMOOTH;
        if (mode == null && Boolean.FALSE.equals(oldSmooth)) return ZOOM_INSTANT;
        return ZOOM_SMOOTH;
    }

    /** Whether the zoom eases rather than jumps. */
    public boolean zoomEases() {
        return Objects.equals(ZOOM_SMOOTH, zoomMode);
    }

    public void save() {
        FILE.save(this);
    }
}
